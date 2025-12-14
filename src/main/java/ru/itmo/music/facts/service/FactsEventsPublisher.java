package ru.itmo.music.facts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.itmo.music.facts.config.KafkaTopicsProperties;
import ru.itmo.music.facts.model.GeneratedFactsPayload;
import java.util.concurrent.ExecutionException;

/**
 * Publishes generated facts to Kafka so Music Service can persist and cache them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FactsEventsPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicsProperties kafkaTopicsProperties;
    private final ObjectMapper objectMapper;

    public void publishGeneratedFacts(String trackId, String factsJson, String eventType, String templateName) {
        String payload = serializePayload(new GeneratedFactsPayload(trackId, factsJson));
        try {
            kafkaTemplate.send(kafkaTopicsProperties.getGeneratedFacts(), trackId, payload).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing generated facts for track " + trackId, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to publish generated facts for track " + trackId, e.getCause());
        }
        log.info("Published generated facts for track {} to topic {} [eventType={}, template={}]",
                trackId, kafkaTopicsProperties.getGeneratedFacts(), eventType, templateName);
    }

    private String serializePayload(GeneratedFactsPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to serialize generated facts payload", e);
        }
    }
}
