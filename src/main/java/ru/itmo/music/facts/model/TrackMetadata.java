package ru.itmo.music.facts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal track metadata fetched from Music Service to build a facts prompt.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrackMetadata(
        String id,
        String title,
        String artist,
        Integer durationMs,
        Integer year,
        Boolean explicit,
        String coverUrl
) {
}
