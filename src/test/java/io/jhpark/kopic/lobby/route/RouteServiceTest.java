package io.jhpark.kopic.lobby.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import io.jhpark.kopic.lobby.redis.GeDirectory;
import io.jhpark.kopic.lobby.route.token.RouteTokenIssuer;
import io.jhpark.kopic.lobby.support.LobbyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

	@Mock
	private GeDirectory geDirectory;

	@Mock
	private RouteTokenIssuer routeTokenIssuer;

	private RouteService routeService;

	@BeforeEach
	void setUp() {
		routeService = new RouteService(geDirectory, routeTokenIssuer);
	}

	@Test
	void quickIssuesJoinActionTokenForSelectedGe() {
		when(geDirectory.findQuickRouteGeId()).thenReturn(Optional.of("ge-1"));
		when(routeTokenIssuer.issue(0, "pobi", "ge-1", null)).thenReturn("token");

		RouteResponse response = routeService.quick(new RouteRequest.NicknameOnly("pobi"));

		assertThat(response).isEqualTo(new RouteResponse("token"));
		verify(routeTokenIssuer).issue(0, "pobi", "ge-1", null);
	}

	@Test
	void privateCreateIssuesCreateActionTokenForSelectedGe() {
		when(geDirectory.findPrivateCreateGeId()).thenReturn(Optional.of("ge-2"));
		when(routeTokenIssuer.issue(1, "pobi", "ge-2", null)).thenReturn("token");

		RouteResponse response = routeService.privateCreate(new RouteRequest.NicknameOnly("pobi"));

		assertThat(response).isEqualTo(new RouteResponse("token"));
		verify(routeTokenIssuer).issue(1, "pobi", "ge-2", null);
	}

	@Test
	void privateJoinIssuesJoinActionTokenWithRoomCode() {
		when(geDirectory.findGeIdByRoomCode("ABCDEF")).thenReturn(Optional.of("ge-3"));
		when(routeTokenIssuer.issue(0, "pobi", "ge-3", "ABCDEF")).thenReturn("token");

		RouteResponse response = routeService.privateJoin(new RouteRequest.PrivateJoin("pobi", "ABCDEF"));

		assertThat(response).isEqualTo(new RouteResponse("token"));
		verify(routeTokenIssuer).issue(0, "pobi", "ge-3", "ABCDEF");
	}

	@Test
	void quickThrowsLobbyExceptionWhenNoGeCandidateExists() {
		when(geDirectory.findQuickRouteGeId()).thenReturn(Optional.empty());

		assertThatThrownBy(() -> routeService.quick(new RouteRequest.NicknameOnly("pobi")))
				.isInstanceOf(LobbyException.class)
				.hasMessage("No ACTIVE GE is available");
	}

	@Test
	void blankNicknameIsRejectedBeforeDirectoryLookup() {
		assertThatThrownBy(() -> routeService.quick(new RouteRequest.NicknameOnly(" ")))
				.isInstanceOf(LobbyException.class)
				.hasMessage("nickname is required");
		verifyNoInteractions(geDirectory, routeTokenIssuer);
	}

	@Test
	void blankRoomCodeIsRejectedBeforeDirectoryLookup() {
		assertThatThrownBy(() -> routeService.privateJoin(new RouteRequest.PrivateJoin("pobi", "")))
				.isInstanceOf(LobbyException.class)
				.hasMessage("roomCode is required");
		verifyNoInteractions(geDirectory, routeTokenIssuer);
	}
}
