package ru.itmo.music.facts.client;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;
import ru.itmo.music.facts.config.LlmProperties;
import ru.itmo.music.facts.config.ProxyApiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Thin client over ProxyAPI (OpenAI-compatible endpoint) to request chat completions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProxyApiClient {

    private final LlmProperties llmProperties;
    private final WebClient.Builder webClientBuilder;

    public String complete(List<Message> messages) {
        ProxyApiProperties properties = llmProperties.getProxyapi();
        ensureApiKey();
        log.info("Calling ProxyAPI model={} baseUrl={} messages={}", properties.getModel(), properties.getBaseUrl(), messages.size());
        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.getModel(),
                messages,
                properties.getTemperature(),
                properties.getMaxTokens(),
                new ResponseFormat("json_object")
        );

        return webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .map(ProxyApiClient::firstMessageContent)
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .retryWhen(buildRetrySpec())
                .doOnSuccess(content -> log.info("ProxyAPI responded with {} chars", content != null ? content.length() : 0))
                .doOnError(ex -> log.warn("ProxyAPI call failed: {}", ex.getMessage()))
                .block();
    }

    private Retry buildRetrySpec() {
        ProxyApiProperties properties = llmProperties.getProxyapi();
        long backoff = properties.getRetry().getBackoffMs();
        int attempts = properties.getRetry().getMaxAttempts();
        return Retry.backoff(Math.max(attempts, 1), Duration.ofMillis(Math.max(backoff, 1)))
                .jitter(0.4)
                .filter(this::isRetryable);
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            int status = ex.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return true;
    }

    private void ensureApiKey() {
        if (!StringUtils.hasText(llmProperties.getProxyapi().getApiKey())) {
            throw new IllegalStateException("PROXYAPI_API_KEY is not configured");
        }
    }

    private static String firstMessageContent(ChatCompletionResponse response) {
        if (response == null
                || response.choices == null
                || response.choices.isEmpty()
                || response.choices.getFirst().message == null) {
            throw new IllegalStateException("ProxyAPI returned empty choices");
        }
        return response.choices.getFirst().message.content;
    }

    public record Message(String role, String content) {
    }

    public record ChatCompletionRequest(
            String model,
            List<Message> messages,
            Double temperature,
            Integer max_tokens,
            ResponseFormat response_format
    ) {
    }

    public record ResponseFormat(String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {
    }
}
