package ru.itmo.music.facts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
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
    public void onMessage(String message) throws JacksonException {
        FactsEventPayload payload = deserialize(message);
        log.info("Received facts event: eventType={}, trackId={}, timestamp={}, priority={}",
                payload.eventType(), payload.trackId(), payload.timestamp(), payload.priority());
        factsGenerationService.processBlocking(payload);
    }

    FactsEventPayload deserialize(String message) throws JacksonException {
        JsonNode root = objectMapper.readTree(message);
        if (root.getNodeType() == JsonNodeType.STRING) {
            // Some producers double-encode the payload; unwrap the inner JSON string.
            root = objectMapper.readTree(root.asText());
        }
        // convertValue avoids deprecated treeToValue in jackson 3.x
        return objectMapper.convertValue(root, FactsEventPayload.class);
    }
}
