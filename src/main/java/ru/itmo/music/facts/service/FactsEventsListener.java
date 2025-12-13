package ru.itmo.music.facts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ru.itmo.music.facts.model.FactsEventPayload;

/**
 * Kafka listener that deserializes incoming commands and forwards them to the generation service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FactsEventsListener {

    private final ObjectMapper objectMapper;
    private final FactsGenerationService factsGenerationService;

    @KafkaListener(topics = "${app.kafka.topics.facts-events}", groupId = "${spring.kafka.consumer.group-id:${spring.application.name}}")
    public void onMessage(String message) {
        try {
            FactsEventPayload payload = deserialize(message);
            log.info("Received facts event: eventType={}, trackId={}, timestamp={}, priority={}",
                    payload.eventType(), payload.trackId(), payload.timestamp(), payload.priority());
            factsGenerationService.handleEvent(payload);
        } catch (Exception e) {
            log.error("Failed to handle message from facts topic: {}", message, e);
        }
    }

    private FactsEventPayload deserialize(String message) throws JacksonException {
        JsonNode root = objectMapper.readTree(message);
        if (root.isTextual()) {
            // Some producers double-encode the payload; unwrap the inner JSON string.
            root = objectMapper.readTree(root.asText());
        }
        return objectMapper.treeToValue(root, FactsEventPayload.class);
    }
}
