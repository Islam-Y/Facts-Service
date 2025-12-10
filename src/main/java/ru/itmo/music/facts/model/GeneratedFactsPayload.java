package ru.itmo.music.facts.model;

/**
 * Outgoing payload with generated facts that Music Service consumes and persists.
 */
public record GeneratedFactsPayload(
        String trackId,
        String factsJson
) {
}
