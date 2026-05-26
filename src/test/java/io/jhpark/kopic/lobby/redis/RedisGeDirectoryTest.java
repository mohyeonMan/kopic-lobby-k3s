package io.jhpark.kopic.lobby.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.Arrays;

import io.jhpark.kopic.lobby.config.LobbyProperties;
import io.jhpark.kopic.lobby.config.RedisKeyProperties;
import io.jhpark.kopic.lobby.support.LobbyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class RedisGeDirectoryTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private ZSetOperations<String, String> zSetOperations;

	private RedisGeDirectory directory;

	@BeforeEach
	void setUp() {
		lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
		directory = new RedisGeDirectory(
				redisTemplate,
				new RedisKeyProperties("ge:", "ge:load", "quick:available", "roomcode:"),
				new LobbyProperties(5)
		);
	}

	@Test
	void quickReturnsFirstActiveQuickCandidate() {
		when(zSetOperations.range("quick:available", 0, 4))
				.thenReturn(orderedSet("ge-draining:room-1", "ge-active:room-2"));
		when(valueOperations.get("ge:ge-draining")).thenReturn("DRAINING");
		when(valueOperations.get("ge:ge-active")).thenReturn("ACTIVE");

		Optional<String> geId = directory.findQuickRouteGeId();

		assertThat(geId).contains("ge-active");
		verify(zSetOperations, never()).range("ge:load", 0, -1);
		verify(zSetOperations, never()).remove(eq("quick:available"), any());
	}

	@Test
	void quickFallsBackToLowestLoadActiveGe() {
		when(zSetOperations.range("quick:available", 0, 4)).thenReturn(Set.of());
		when(zSetOperations.range("ge:load", 0, -1))
				.thenReturn(orderedSet("ge-draining", "ge-active"));
		when(valueOperations.get("ge:ge-draining")).thenReturn("DRAINING");
		when(valueOperations.get("ge:ge-active")).thenReturn("ACTIVE");

		Optional<String> geId = directory.findQuickRouteGeId();

		assertThat(geId).contains("ge-active");
	}

	@Test
	void privateCreateReturnsLowestLoadActiveGe() {
		when(zSetOperations.range("ge:load", 0, -1))
				.thenReturn(orderedSet("ge-missing", "ge-active"));
		when(valueOperations.get("ge:ge-missing")).thenReturn(null);
		when(valueOperations.get("ge:ge-active")).thenReturn("ACTIVE");

		Optional<String> geId = directory.findPrivateCreateGeId();

		assertThat(geId).contains("ge-active");
	}

	@Test
	void privateJoinReturnsRoomCodeGeOnlyWhenActive() {
		when(valueOperations.get("roomcode:ABCDEF")).thenReturn("ge-active");
		when(valueOperations.get("ge:ge-active")).thenReturn("ACTIVE");

		Optional<String> geId = directory.findGeIdByRoomCode("ABCDEF");

		assertThat(geId).contains("ge-active");
	}

	@Test
	void privateJoinReturnsEmptyWhenRoomCodeIsMissing() {
		when(valueOperations.get("roomcode:ABCDEF")).thenReturn(null);

		Optional<String> geId = directory.findGeIdByRoomCode("ABCDEF");

		assertThat(geId).isEmpty();
		verify(valueOperations).get("roomcode:ABCDEF");
		verifyNoMoreInteractions(valueOperations);
	}

	@Test
	void redisFailureIsConvertedToLobbyException() {
		when(zSetOperations.range("quick:available", 0, 4))
				.thenThrow(new DataAccessResourceFailureException("down"));

		assertThatThrownBy(() -> directory.findQuickRouteGeId())
				.isInstanceOf(LobbyException.class)
				.hasMessage("Redis directory lookup failed");
	}

	private LinkedHashSet<String> orderedSet(String... values) {
		return new LinkedHashSet<>(Arrays.asList(values));
	}
}
