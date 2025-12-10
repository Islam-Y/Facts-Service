package ru.itmo.music.facts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Entry point for the Facts Service: bootstraps Spring, Feign clients and configuration properties.
 */
@SpringBootApplication
@EnableFeignClients
@ConfigurationPropertiesScan
public class FactsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FactsServiceApplication.class, args);
	}
}
