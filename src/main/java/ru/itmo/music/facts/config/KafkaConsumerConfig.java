package ru.itmo.music.facts.config;

import com.fasterxml.jackson.core.JacksonException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import ru.itmo.music.facts.service.TrackNotFoundException;

/**
 * Configures retry/backoff strategy for Kafka listeners and routes failed messages to DLT.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template,
        KafkaTopicsProperties topics) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                // Отправляем в партицию 0, чтобы хватило одного партиционированного DLT-топика.
                (record, ex) -> new TopicPartition(topics.getFactsEvents() + ".dlt", 0)
        );

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(2);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JacksonException.class, TrackNotFoundException.class);
        return handler;
    }
}
