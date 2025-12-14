package ru.itmo.music.facts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Top-level configuration for LLM integration.
 */
@Data
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /**
     * Provider name (proxyapi, etc.).
     */
    private String provider;

    /**
     * Defaults for prompt construction.
     */
    private PromptProperties prompt = new PromptProperties();

    /**
     * Settings for ProxyAPI OpenAI-compatible gateway.
     */
    private ProxyApiProperties proxyapi = new ProxyApiProperties();
}
