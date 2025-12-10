package ru.itmo.music.facts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.kafka.topics")
public class KafkaTopicsProperties {

    /**
     * Incoming events from Music Service (music.facts.events).
     */
    private String factsEvents;

    /**
     * Outgoing generated facts topic (music.track.facts.generated).
     */
    private String generatedFacts;
}
