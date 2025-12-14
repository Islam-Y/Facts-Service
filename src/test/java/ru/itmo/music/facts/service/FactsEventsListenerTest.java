package ru.itmo.music.facts.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.itmo.music.facts.model.FactsEventPayload;

class FactsEventsListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FactsEventsListener listener = new FactsEventsListener(objectMapper, Mockito.mock(FactsGenerationService.class));

    @Test
    void deserializeDoubleEncodedPayload() throws Exception {
        String originalJson = objectMapper.writeValueAsString(new FactsEventPayload("1", "created", "track-1", 5, null));
        String doubleEncoded = objectMapper.writeValueAsString(originalJson); // payload wrapped as JSON string

        FactsEventPayload payload = listener.deserialize(doubleEncoded);

        assertThat(payload.trackId()).isEqualTo("track-1");
        assertThat(payload.eventType()).isEqualTo("created");
        assertThat(payload.priority()).isEqualTo(5);
    }
}
