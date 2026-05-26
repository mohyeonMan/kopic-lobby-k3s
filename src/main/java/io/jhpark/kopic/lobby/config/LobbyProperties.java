package io.jhpark.kopic.lobby.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kopic.lobby")
public record LobbyProperties(int quickCandidateScanLimit) {
}
