package io.jhpark.kopic.lobby.route;

public sealed interface RouteRequest permits RouteRequest.NicknameOnly, RouteRequest.PrivateJoin {

	String nickname();

	record NicknameOnly(String nickname) implements RouteRequest {
	}

	record PrivateJoin(String nickname, String roomCode) implements RouteRequest {
	}
}
