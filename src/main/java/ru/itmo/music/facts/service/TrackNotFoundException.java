package ru.itmo.music.facts.service;

/**
 * Signals that a track was not found in Music Service while processing an event.
 */
public class TrackNotFoundException extends RuntimeException {

    public TrackNotFoundException(String trackId, Throwable cause) {
        super("Track %s not found".formatted(trackId), cause);
    }
}
