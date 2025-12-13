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
import ru.itmo.music.facts.service.FactsGenerator.GenerationResult;

/**
 * Orchestrates facts generation: prioritizes incoming events, fetches track metadata,
 * produces facts and publishes them back to Music Service.
 */
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

        String eventType = payload.eventType();
        String trackId = payload.trackId();

        if (isDeletedEvent(eventType)) {
            if (trackId == null || trackId.isBlank()) {
                log.warn("Skip deletion handling: trackId is missing in payload {}", payload);
                return;
            }

            int removed = cancelPending(trackId);
            log.info("Deleted track event: removed {} pending generation tasks for track {}", removed, trackId);
            return;
        }

        if (!requiresGeneration(eventType)) {
            log.info("Ignore event type {} for track {}: not a generation scenario", eventType, trackId);
            return;
        }

        if (trackId == null || trackId.isBlank()) {
            log.warn("Skip facts generation: trackId is missing in payload {}", payload);
            return;
        }

        queue.offer(payload);
        log.info("Enqueued facts generation for track {}, eventType={}, priority={}, timestamp={}",
                trackId, eventType, priorityOrDefault(payload), timestampOrDefault(payload));
    }

    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                FactsEventPayload payload = queue.take();
                log.info("Dequeued facts generation task for track {}, eventType={}, priority={}",
                        payload.trackId(), payload.eventType(), priorityOrDefault(payload));
                processGeneration(payload);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Unexpected error while processing facts generation queue", e);
            }
        }
    }

    private void processGeneration(FactsEventPayload payload) {
        String trackId = payload.trackId();
        String eventType = payload.eventType();
        log.info("Start facts generation for track {}, eventType={}", trackId, eventType);
        try {
            TrackMetadata metadata = musicServiceClient.getTrack(trackId);
            GenerationResult result = factsGenerator.generateFacts(metadata, eventType);
            log.info("Facts generated for track {} using template={}, eventType={}", trackId, result.templateName(), eventType);
            factsEventsPublisher.publishGeneratedFacts(trackId, result.factsJson(), eventType, result.templateName());
            log.info("Completed facts generation pipeline for track {}, eventType={}, template={}", trackId, eventType, result.templateName());
        } catch (FeignException.NotFound e) {
            log.warn("Track {} not found in Music Service, cannot generate facts", trackId);
        } catch (FeignException e) {
            log.error("Failed to fetch track {} from Music Service", trackId, e);
        } catch (Exception e) {
            log.error("Failed to generate facts for track {}", trackId, e);
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

    private boolean isDeletedEvent(String eventType) {
        return "deleted".equalsIgnoreCase(eventType);
    }

    private int cancelPending(String trackId) {
        FactsEventPayload[] snapshot = queue.toArray(FactsEventPayload[]::new);
        int removed = 0;

        for (FactsEventPayload event : snapshot) {
            if (trackId.equals(event.trackId()) && queue.remove(event)) {
                removed++;
            }
        }

        return removed;
    }
}
