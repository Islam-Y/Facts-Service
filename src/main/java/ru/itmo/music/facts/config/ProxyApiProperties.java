package ru.itmo.music.facts.config;

import lombok.Data;

/**
 * ProxyAPI (OpenAI-compatible gateway) settings.
 */
@Data
public class ProxyApiProperties {

    private String baseUrl = "https://openai.api.proxyapi.ru/v1";
    private String apiKey;
    private String model = "openai/gpt-4o-mini";
    private Integer timeoutMs = 10_000;
    private Double temperature = 0.2;
    private Integer maxTokens = 600;
    private RetryProperties retry = new RetryProperties();
    private RetryProperties formatRetry = new RetryProperties(2, 400L);
}
