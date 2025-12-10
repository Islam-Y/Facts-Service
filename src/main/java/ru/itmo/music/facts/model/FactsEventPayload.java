package ru.itmo.music.facts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Incoming command from Music Service describing what facts operation to perform for a track.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FactsEventPayload(
        String version,
        String eventType,
        String trackId,
        Integer priority,
        Instant timestamp
) {
}
