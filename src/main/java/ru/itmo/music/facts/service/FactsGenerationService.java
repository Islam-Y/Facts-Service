package ru.itmo.music.facts.service;

import feign.FeignException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.itmo.music.facts.client.MusicServiceClient;
import ru.itmo.music.facts.model.FactsEventPayload;
import ru.itmo.music.facts.model.TrackMetadata;
import ru.itmo.music.facts.service.FactsGenerator.GenerationResult;

/**
 * Executes facts generation pipeline: fetches track metadata, produces facts and publishes them back to Music Service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FactsGenerationService {

    private final MusicServiceClient musicServiceClient;
    private final FactsGenerator factsGenerator;
    private final FactsEventsPublisher factsEventsPublisher;

    public void processBlocking(FactsEventPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Received empty facts event payload");
        }

        String eventType = payload.eventType();
        String trackId = payload.trackId();

        if (isDeletedEvent(eventType)) {
            log.info("Skip facts generation for deleted track event: {}", payload);
            return;
        }

        if (!requiresGeneration(eventType)) {
            log.info("Ignore event type {} for track {}: not a generation scenario", eventType, trackId);
            return;
        }

        if (trackId == null || trackId.isBlank()) {
            throw new IllegalArgumentException("trackId is missing in payload " + payload);
        }

        log.info("Start facts generation for track {}, eventType={}, priority={}, timestamp={}",
                trackId, eventType, priorityOrDefault(payload), timestampOrDefault(payload));

        TrackMetadata metadata = fetchTrackMetadata(trackId);
        log.info("Fetched track metadata for track {}: title='{}', artist='{}', year={}, explicit={}, durationMs={}",
                trackId, metadata.title(), metadata.artist(), metadata.year(), metadata.explicit(), metadata.durationMs());

        try {
            GenerationResult result = generateFacts(metadata, eventType, trackId);
            factsEventsPublisher.publishGeneratedFacts(trackId, result.factsJson(), eventType, result.templateName());
            log.info("Completed facts generation pipeline for track {}, eventType={}, template={}", trackId, eventType, result.templateName());
        } catch (Exception e) {
            log.error("Facts generation pipeline failed for track {}", trackId, e);
            throw e;
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

    private TrackMetadata fetchTrackMetadata(String trackId) {
        try {
            log.info("Requesting track metadata from Music Service for track {}", trackId);
            return musicServiceClient.getTrack(trackId);
        } catch (FeignException.NotFound e) {
            log.warn("Track {} not found in Music Service, will send to DLT", trackId);
            throw new TrackNotFoundException(trackId, e);
        } catch (FeignException e) {
            log.error("Failed to fetch track {} from Music Service (status {})", trackId, e.status(), e);
            throw e;
        }
    }

    private GenerationResult generateFacts(TrackMetadata metadata, String eventType, String trackId) {
        try {
            GenerationResult result = factsGenerator.generateFacts(metadata, eventType);
            log.info("Facts generated for track {} using template={}, eventType={}", trackId, result.templateName(), eventType);
            return result;
        } catch (RuntimeException e) {
            log.error("Failed to generate facts for track {}", trackId, e);
            throw e;
        }
    }
}
