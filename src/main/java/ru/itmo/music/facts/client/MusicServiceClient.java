package ru.itmo.music.facts.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.itmo.music.facts.model.TrackMetadata;

/**
 * Feign client for internal Music Service API used to fetch track metadata before generation.
 */
@FeignClient(name = "music-service-client", url = "${app.music-service.base-url}")
public interface MusicServiceClient {

    @GetMapping("/internal/tracks/{trackId}")
    TrackMetadata getTrack(@PathVariable("trackId") String trackId);
}
