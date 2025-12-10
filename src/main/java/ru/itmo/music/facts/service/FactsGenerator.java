package ru.itmo.music.facts.service;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import ru.itmo.music.facts.model.TrackMetadata;

@Component
@RequiredArgsConstructor
public class FactsGenerator {

    private final ObjectMapper objectMapper;

    /**
     * Generates a deterministic stub fact so we can exercise the integration
     * end-to-end without calling an LLM yet.
     */
    public String generateFacts(TrackMetadata metadata) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("formatVersion", 1);
        fact.put("lang", "ru");
        fact.put("short", buildShortFact(metadata));
        fact.put("full", buildFullFact(metadata));

        try {
            return objectMapper.writeValueAsString(fact);
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to serialize facts JSON", e);
        }
    }

    private String buildShortFact(TrackMetadata metadata) {
        String title = orDefault(metadata.title(), "неизвестный трек");
        String artist = orDefault(metadata.artist(), "неизвестного исполнителя");
        return "“%s” — трек %s. Факт сгенерирован шаблонно для MVP.".formatted(title, artist);
    }

    private String buildFullFact(TrackMetadata metadata) {
        StringBuilder builder = new StringBuilder();
        String title = orDefault(metadata.title(), "трек без названия");
        String artist = orDefault(metadata.artist(), "неизвестного исполнителя");
        builder.append("Трек “").append(title).append("” от ").append(artist);

        if (metadata.year() != null) {
            builder.append(", выпущенный в ").append(metadata.year()).append(" году");
        }

        if (metadata.durationMs() != null && metadata.durationMs() > 0) {
            builder.append(", длится ").append(formatDuration(metadata.durationMs()));
        }

        if (Boolean.TRUE.equals(metadata.explicit())) {
            builder.append(". Имеет пометку Explicit — предупреждаем слушателей");
        }

        builder.append(". Это stub-текст: LLM отключён, но пайплайн генерации работает.");

        if (metadata.coverUrl() != null && !metadata.coverUrl().isBlank()) {
            builder.append(" Обложка трека: ").append(metadata.coverUrl());
        }

        return builder.toString();
    }

    private String formatDuration(int durationMs) {
        int totalSeconds = durationMs / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "%d:%02d".formatted(minutes, seconds);
    }

    private String orDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
