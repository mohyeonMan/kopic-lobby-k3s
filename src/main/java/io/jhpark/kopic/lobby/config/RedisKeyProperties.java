package io.jhpark.kopic.lobby.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kopic.redis.keys")
public record RedisKeyProperties(
		String gePrefix,
		String geLoad,
		String quickAvailable,
		String roomCodePrefix
) {

	public String geKey(String geId) {
		return gePrefix + geId;
	}

	public String roomCodeKey(String roomCode) {
		return roomCodePrefix + roomCode;
	}
}
