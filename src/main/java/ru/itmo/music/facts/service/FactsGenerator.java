package ru.itmo.music.facts.service;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ru.itmo.music.facts.client.ProxyApiClient;
import ru.itmo.music.facts.client.ProxyApiClient.Message;
import ru.itmo.music.facts.config.LlmProperties;
import ru.itmo.music.facts.config.PromptProperties;
import ru.itmo.music.facts.config.ProxyApiProperties;
import ru.itmo.music.facts.model.TrackMetadata;

/**
 * Generates facts via ProxyAPI (OpenAI-compatible) using track metadata.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FactsGenerator {

    private final ProxyApiClient proxyApiClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    private static final String TEMPLATE_NAME = "proxyapi";

    public GenerationResult generateFacts(TrackMetadata metadata, String eventType) {
        ProxyApiProperties proxyProps = llmProperties.getProxyapi();
        int maxFormatAttempts = Math.max(proxyProps.getFormatRetry().getMaxAttempts(), 1);
        String lastResponse = null;

        for (int attempt = 1; attempt <= maxFormatAttempts; attempt++) {
            log.info("Requesting LLM facts for track {} (eventType={}, attempt {}/{}, model={}, temp={}, maxTokens={})",
                    metadata.id(), eventType, attempt, maxFormatAttempts, proxyProps.getModel(), proxyProps.getTemperature(), proxyProps.getMaxTokens());
            String response = proxyApiClient.complete(buildMessages(metadata, eventType, attempt));
            lastResponse = response;
            try {
                FactContent fact = parseFact(response);
                validate(fact);
                String normalized = objectMapper.writeValueAsString(fact);
                return new GenerationResult(TEMPLATE_NAME, normalized);
            } catch (Exception ex) {
                log.warn("LLM returned invalid fact format (attempt {}/{}): {}", attempt, maxFormatAttempts, ex.getMessage());
                if (attempt == maxFormatAttempts) {
                    throw new IllegalStateException("LLM returned invalid format after retries: " + ex.getMessage(), ex);
                }
            }
        }

        throw new IllegalStateException("Unexpected fallthrough while generating facts. Last response: " + lastResponse);
    }

    private List<Message> buildMessages(TrackMetadata metadata, String eventType, int attempt) {
        PromptProperties prompt = llmProperties.getPrompt();
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt(prompt, attempt)));
        messages.add(new Message("user", userPrompt(metadata, eventType, prompt)));
        return messages;
    }

    private String systemPrompt(PromptProperties prompt, int attempt) {
        String base = """
                Ты генерируешь один интересный факт о треке и возвращаешь строго JSON без Markdown и лишнего текста.
                Формат: {"formatVersion":%d,"lang":"%s","short":"...","full":"...","sources":[{"title":"...","url":"..."}]}.
                Обязательные поля: formatVersion, lang, short, full, sources (1..%d элементов, url должен быть https/http).
                """
                .formatted(prompt.getFormatVersion(), prompt.getLang(), prompt.getMaxSources());

        if (attempt > 1) {
            return base + "Предыдущий ответ был в неверном формате. Верни только JSON-объект без ``` и без пояснений.";
        }
        return base;
    }

    private String userPrompt(TrackMetadata metadata, String eventType, PromptProperties prompt) {
        String title = safe(metadata.title());
        String artist = safe(metadata.artist());
        String year = metadata.year() != null ? metadata.year().toString() : "unknown";
        String duration = metadata.durationMs() != null ? metadata.durationMs().toString() : "unknown";
        String explicit = metadata.explicit() != null && metadata.explicit() ? "true" : "false";
        String scenario = eventType != null ? eventType : "generic";

        return """
                Сгенерируй один проверяемый факт о треке.
                title="%s"; artist="%s"; year=%s; durationMs=%s; explicit=%s; eventType=%s; lang=%s.
                Если нет достоверной информации, напиши, что достоверный факт не найден, но сохрани формат.
                """.formatted(title, artist, year, duration, explicit, scenario, prompt.getLang());
    }

    private FactContent parseFact(String rawContent) throws JacksonException {
        String cleaned = stripMarkdown(rawContent);
        log.debug("LLM raw response (trimmed): {}", cleaned);
        return objectMapper.readValue(cleaned, FactContent.class);
    }

    private void validate(FactContent fact) {
        if (fact.formatVersion == null) {
            throw new IllegalArgumentException("formatVersion is missing");
        }
        if (!StringUtils.hasText(fact.lang)) {
            throw new IllegalArgumentException("lang is missing");
        }
        if (!StringUtils.hasText(fact.shortFact)) {
            throw new IllegalArgumentException("short is missing");
        }
        if (!StringUtils.hasText(fact.full)) {
            throw new IllegalArgumentException("full is missing");
        }
        if (fact.sources == null || fact.sources.isEmpty()) {
            throw new IllegalArgumentException("sources are missing");
        }
        fact.sources.forEach(source -> {
            if (!StringUtils.hasText(source.url)) {
                throw new IllegalArgumentException("source url is missing");
            }
        });
    }

    private String stripMarkdown(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "unknown";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FactContent(
            Integer formatVersion,
            String lang,
            @JsonProperty("short") String shortFact,
            String full,
            List<FactSource> sources
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FactSource(
            String title,
            String url
    ) {
    }

    public record GenerationResult(String templateName, String factsJson) {
    }
}
