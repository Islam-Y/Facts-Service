package ru.itmo.music.facts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import ru.itmo.music.facts.model.FactsEventPayload;

@Component
@RequiredArgsConstructor
@Slf4j
public class FactsEventsListener {

    private final ObjectMapper objectMapper;
    private final FactsGenerationService factsGenerationService;

    @KafkaListener(topics = "${app.kafka.topics.facts-events}", groupId = "${spring.kafka.consumer.group-id:${spring.application.name}}")
    public void onMessage(String message) {
        try {
            FactsEventPayload payload = objectMapper.readValue(message, FactsEventPayload.class);
            factsGenerationService.handleEvent(payload);
        } catch (Exception e) {
            log.error("Failed to handle message from facts topic: {}", message, e);
        }
    }
}
