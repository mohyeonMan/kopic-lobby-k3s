package io.jhpark.kopic.lobby.redis;

import java.util.Optional;
import java.util.Set;

import io.jhpark.kopic.lobby.config.LobbyProperties;
import io.jhpark.kopic.lobby.config.RedisKeyProperties;
import io.jhpark.kopic.lobby.support.LobbyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisGeDirectory implements GeDirectory {

	private static final String ACTIVE = "ACTIVE";

	private final StringRedisTemplate redisTemplate;
	private final RedisKeyProperties redisKeyProperties;
	private final LobbyProperties lobbyProperties;

	@Override
	public Optional<String> findQuickRouteGeId() {
		try {
			Optional<String> quickCandidateGeId = findQuickCandidateGeId();
			if (quickCandidateGeId.isPresent()) {
				log.info("ge directory selected type=quick source=quickAvailable geId={}", quickCandidateGeId.get());
				return quickCandidateGeId;
			}

			Optional<String> loadCandidateGeId = findLowestLoadActiveGeId();
			if (loadCandidateGeId.isPresent()) {
				log.info("ge directory selected type=quick source=geLoad geId={}", loadCandidateGeId.get());
			} else {
				log.warn("ge directory candidate missing type=quick");
			}
			return loadCandidateGeId;
		} catch (DataAccessException exception) {
			log.warn("ge directory lookup failed type=quick", exception);
			throw LobbyException.routeUnavailable("Redis directory lookup failed", exception);
		}
	}

	@Override
	public Optional<String> findPrivateCreateGeId() {
		try {
			Optional<String> geId = findLowestLoadActiveGeId();
			if (geId.isPresent()) {
				log.info("ge directory selected type=private-create source=geLoad geId={}", geId.get());
			} else {
				log.warn("ge directory candidate missing type=private-create");
			}
			return geId;
		} catch (DataAccessException exception) {
			log.warn("ge directory lookup failed type=private-create", exception);
			throw LobbyException.routeUnavailable("Redis directory lookup failed", exception);
		}
	}

	@Override
	public Optional<String> findGeIdByRoomCode(String roomCode) {
		try {
			String geId = redisTemplate.opsForValue().get(redisKeyProperties.roomCodeKey(roomCode));
			if (geId == null) {
				log.warn("ge directory roomCode missing roomCode={}", roomCode);
				return Optional.empty();
			}
			if (!isActive(geId)) {
				log.warn("ge directory roomCode inactive roomCode={} geId={}", roomCode, geId);
				return Optional.empty();
			}
			log.info("ge directory selected type=private-join source=roomCode roomCode={} geId={}", roomCode, geId);
			return Optional.of(geId);
		} catch (DataAccessException exception) {
			log.warn("ge directory lookup failed type=private-join roomCode={}", roomCode, exception);
			throw LobbyException.routeUnavailable("Redis directory lookup failed", exception);
		}
	}

	private Optional<String> findQuickCandidateGeId() {
		int scanLimit = lobbyProperties.quickCandidateScanLimit();
		if (scanLimit <= 0) {
			log.debug("quick candidate scan skipped scanLimit={}", scanLimit);
			return Optional.empty();
		}

		log.debug("quick candidate scan started key={} scanLimit={}", redisKeyProperties.quickAvailable(), scanLimit);
		Set<String> members = redisTemplate.opsForZSet()
				.range(redisKeyProperties.quickAvailable(), 0, scanLimit - 1L);
		if (members == null) {
			log.debug("quick candidate scan empty key={}", redisKeyProperties.quickAvailable());
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
