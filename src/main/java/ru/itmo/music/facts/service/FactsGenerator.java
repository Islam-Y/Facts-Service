package ru.itmo.music.facts.service;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import ru.itmo.music.facts.model.TrackMetadata;

/**
 * Builds facts JSON based on track metadata (stub implementation without LLM for now).
 */
@Component
@RequiredArgsConstructor
public class FactsGenerator {

    private final ObjectMapper objectMapper;

    /**
     * Generates a deterministic stub fact so we can exercise the integration
     * end-to-end without calling an LLM yet.
     */
    public GenerationResult generateFacts(TrackMetadata metadata, String eventType) {
        String scenario = normalizeEventType(eventType);
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("formatVersion", 1);
        fact.put("lang", "ru");
        fact.put("short", buildShortFact(metadata, scenario));
        fact.put("full", buildFullFact(metadata, scenario));

        try {
            return new GenerationResult(scenario, objectMapper.writeValueAsString(fact));
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to serialize facts JSON", e);
        }
    }

    private String buildShortFact(TrackMetadata metadata, String scenario) {
        String title = orDefault(metadata.title(), "неизвестный трек");
        String artist = orDefault(metadata.artist(), "неизвестного исполнителя");
        return switch (scenario) {
            case "created" -> "“%s” — новый трек %s. Факт создан впервые (scenario=created).".formatted(title, artist);
            case "updated" -> "“%s” — обновлённый трек %s. Факт пересчитан (scenario=updated).".formatted(title, artist);
            case "refresh" -> "“%s” — трек %s. Факт освежён по запросу (scenario=refresh).".formatted(title, artist);
            default -> "“%s” — трек %s. Факт сгенерирован шаблонно для MVP (scenario=generic).".formatted(title, artist);
        };
    }

    private String buildFullFact(TrackMetadata metadata, String scenario) {
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

        switch (scenario) {
            case "created" -> builder.append(". Сценарий created: это первичная генерация фактов для нового трека.");
            case "updated" -> builder.append(". Сценарий updated: обновляем факты после изменения данных трека.");
            case "refresh" -> builder.append(". Сценарий refresh: переобновляем факты по запросу.");
            default -> builder.append(". Сценарий generic: LLM отключён, но пайплайн генерации работает.");
        }

        if (metadata.coverUrl() != null && !metadata.coverUrl().isBlank()) {
            builder.append(" Обложка трека: ").append(metadata.coverUrl());
        }

        return builder.toString();
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "generic";
        }

        String normalized = eventType.trim().toLowerCase();
        return switch (normalized) {
            case "created", "updated", "refresh" -> normalized;
            default -> "generic";
        };
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

    public record GenerationResult(String templateName, String factsJson) {
    }
}
