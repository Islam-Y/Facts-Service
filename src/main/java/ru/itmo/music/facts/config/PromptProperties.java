package ru.itmo.music.facts.config;

import lombok.Data;

/**
 * Prompt defaults for facts generation.
 */
@Data
public class PromptProperties {

    private Integer formatVersion = 1;
    private String lang = "ru";
    private Integer maxSources = 3;
}
