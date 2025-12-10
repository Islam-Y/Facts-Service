package ru.itmo.music.facts.service;

import feign.FeignException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.itmo.music.facts.client.MusicServiceClient;
import ru.itmo.music.facts.model.FactsEventPayload;
import ru.itmo.music.facts.model.TrackMetadata;

@Service
@RequiredArgsConstructor
@Slf4j
public class FactsGenerationService {

    private final MusicServiceClient musicServiceClient;
    private final FactsGenerator factsGenerator;
    private final FactsEventsPublisher factsEventsPublisher;
    private final PriorityBlockingQueue<FactsEventPayload> queue = new PriorityBlockingQueue<>(
            11,
            Comparator.comparingInt(FactsGenerationService::priorityOrDefault)
                    .reversed()
                    .thenComparing(FactsGenerationService::timestampOrDefault)
    );
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "facts-generation-worker");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    void startQueueProcessing() {
        executor.submit(this::processQueue);
    }

    @PreDestroy
    void stopQueueProcessing() {
        executor.shutdownNow();
    }

    public void handleEvent(FactsEventPayload payload) {
        if (payload == null) {
            log.warn("Received empty facts event payload");
            return;
        }

        if (!requiresGeneration(payload.eventType())) {
            log.debug("Ignore event type {} for track {}", payload.eventType(), payload.trackId());
            return;
        }

        if (payload.trackId() == null || payload.trackId().isBlank()) {
            log.warn("Skip facts generation: trackId is missing in payload {}", payload);
            return;
        }

        queue.offer(payload);
        log.info("Enqueued facts generation for track {}, eventType={}, priority={}, timestamp={}",
                payload.trackId(), payload.eventType(), priorityOrDefault(payload), timestampOrDefault(payload));
    }

    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                FactsEventPayload payload = queue.take();
                processGeneration(payload);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Unexpected error while processing facts generation queue", e);
            }
        }
    }

    private void processGeneration(FactsEventPayload payload) {
        try {
            TrackMetadata metadata = musicServiceClient.getTrack(payload.trackId());
            String factsJson = factsGenerator.generateFacts(metadata);
            factsEventsPublisher.publishGeneratedFacts(payload.trackId(), factsJson);
        } catch (FeignException.NotFound e) {
            log.warn("Track {} not found in Music Service, cannot generate facts", payload.trackId());
        } catch (FeignException e) {
            log.error("Failed to fetch track {} from Music Service", payload.trackId(), e);
        } catch (Exception e) {
            log.error("Failed to generate facts for track {}", payload.trackId(), e);
        }
    }

    private boolean requiresGeneration(String eventType) {
        if (eventType == null) {
            return false;
        }

        return switch (eventType.toLowerCase()) {
            case "created", "updated", "refresh" -> true;
            default -> false;
        };
    }

    private static int priorityOrDefault(FactsEventPayload payload) {
        return payload.priority() != null ? payload.priority() : 0;
    }

    private static Instant timestampOrDefault(FactsEventPayload payload) {
        return payload.timestamp() != null ? payload.timestamp() : Instant.EPOCH;
    }
}
