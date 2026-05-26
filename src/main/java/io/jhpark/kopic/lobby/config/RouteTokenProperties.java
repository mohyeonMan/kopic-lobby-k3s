package io.jhpark.kopic.lobby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kopic.route-token")
public record RouteTokenProperties(String secret, Duration ttl) {
}
