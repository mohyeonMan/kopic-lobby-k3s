package io.jhpark.kopic.lobby.redis;

import java.util.Optional;
import java.util.Set;

import io.jhpark.kopic.lobby.config.LobbyProperties;
import io.jhpark.kopic.lobby.config.RedisKeyProperties;
import io.jhpark.kopic.lobby.support.LobbyException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisGeDirectory implements GeDirectory {

	private static final String ACTIVE = "ACTIVE";

	private final StringRedisTemplate redisTemplate;
	private final RedisKeyProperties redisKeyProperties;
	private final LobbyProperties lobbyProperties;

	public RedisGeDirectory(
			StringRedisTemplate redisTemplate,
			RedisKeyProperties redisKeyProperties,
			LobbyProperties lobbyProperties
	) {
		this.redisTemplate = redisTemplate;
		this.redisKeyProperties = redisKeyProperties;
		this.lobbyProperties = lobbyProperties;
	}

	@Override
	public Optional<String> findQuickRouteGeId() {
		try {
			return findQuickCandidateGeId()
					.or(this::findLowestLoadActiveGeId);
		} catch (DataAccessException exception) {
			throw LobbyException.routeUnavailable("Redis directory lookup failed", exception);
		}
	}

	@Override
	public Optional<String> findPrivateCreateGeId() {
		try {
			return findLowestLoadActiveGeId();
		} catch (DataAccessException exception) {
			throw LobbyException.routeUnavailable("Redis directory lookup failed", exception);
		}
	}

	@Override
	public Optional<String> findGeIdByRoomCode(String roomCode) {
		try {
			String geId = redisTemplate.opsForValue().get(redisKeyProperties.roomCodeKey(roomCode));
			if (geId == null || !isActive(geId)) {
				return Optional.empty();
			}
			return Optional.of(geId);
		} catch (DataAccessException exception) {
			throw LobbyException.routeUnavailable("Redis directory lookup failed", exception);
		}
	}

	private Optional<String> findQuickCandidateGeId() {
		int scanLimit = lobbyProperties.quickCandidateScanLimit();
		if (scanLimit <= 0) {
			return Optional.empty();
		}

		Set<String> members = redisTemplate.opsForZSet()
				.range(redisKeyProperties.quickAvailable(), 0, scanLimit - 1L);
		if (members == null) {
			return Optional.empty();
		}

		return members.stream()
				.map(this::parseGeId)
				.flatMap(Optional::stream)
				.filter(this::isActive)
				.findFirst();
	}

	private Optional<String> findLowestLoadActiveGeId() {
		Set<String> geIds = redisTemplate.opsForZSet().range(redisKeyProperties.geLoad(), 0, -1);
		if (geIds == null) {
			return Optional.empty();
		}

		return geIds.stream()
				.filter(this::isActive)
				.findFirst();
	}

	private Optional<String> parseGeId(String member) {
		int separatorIndex = member.indexOf(':');
		if (separatorIndex <= 0) {
			return Optional.empty();
		}
		return Optional.of(member.substring(0, separatorIndex));
	}

	private boolean isActive(String geId) {
		String state = redisTemplate.opsForValue().get(redisKeyProperties.geKey(geId));
		return ACTIVE.equals(state);
	}
}
