package io.jhpark.kopic.lobby.route;

import io.jhpark.kopic.lobby.redis.GeDirectory;
import io.jhpark.kopic.lobby.route.token.RouteTokenIssuer;
import io.jhpark.kopic.lobby.support.LobbyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

	private static final int JOIN_ACTION = 0;
	private static final int CREATE_PRIVATE_ROOM_ACTION = 1;

	private final GeDirectory geDirectory;
	private final RouteTokenIssuer routeTokenIssuer;

	public RouteResponse quick(RouteRequest.NicknameOnly request) {
		String nickname = requireNickname(request);
		log.info("route request type=quick nickname={}", nickname);
		String geId = geDirectory.findQuickRouteGeId()
				.orElseThrow(() -> LobbyException.routeUnavailable("No ACTIVE GE is available"));
		log.info("route issued type=quick geId={} nickname={}", geId, nickname);
		return routeResponse(JOIN_ACTION, nickname, geId, null);
	}

	public RouteResponse privateCreate(RouteRequest.NicknameOnly request) {
		String nickname = requireNickname(request);
		log.info("route request type=private-create nickname={}", nickname);
		String geId = geDirectory.findPrivateCreateGeId()
				.orElseThrow(() -> LobbyException.routeUnavailable("No ACTIVE GE is available"));
		log.info("route issued type=private-create geId={} nickname={}", geId, nickname);
		return routeResponse(CREATE_PRIVATE_ROOM_ACTION, nickname, geId, null);
	}

	public RouteResponse privateJoin(RouteRequest.PrivateJoin request) {
		String nickname = requireNickname(request);
		String roomCode = requireRoomCode(request);
		log.info("route request type=private-join nickname={} roomCode={}", nickname, roomCode);
		String geId = geDirectory.findGeIdByRoomCode(roomCode)
				.orElseThrow(() -> LobbyException.routeUnavailable("No ACTIVE GE is available for roomCode"));
		log.info("route issued type=private-join geId={} nickname={} roomCode={}", geId, nickname, roomCode);
		return routeResponse(JOIN_ACTION, nickname, geId, roomCode);
	}

	private RouteResponse routeResponse(int action, String nickname, String geId, String roomCode) {
		return new RouteResponse(routeTokenIssuer.issue(action, nickname, geId, roomCode));
	}

	private String requireNickname(RouteRequest request) {
		if (request == null || isBlank(request.nickname())) {
			throw LobbyException.invalidRequest("nickname is required");
		}
		return request.nickname();
	}

	private String requireRoomCode(RouteRequest.PrivateJoin request) {
		if (request == null || isBlank(request.roomCode())) {
			throw LobbyException.invalidRequest("roomCode is required");
		}
		return request.roomCode();
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
