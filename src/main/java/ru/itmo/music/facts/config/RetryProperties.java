package ru.itmo.music.facts.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple retry configuration (attempts + backoff).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetryProperties {

    private Integer maxAttempts = 3;
    private Long backoffMs = 400L;
}
