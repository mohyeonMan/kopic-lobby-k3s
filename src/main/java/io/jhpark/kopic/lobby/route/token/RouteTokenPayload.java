package io.jhpark.kopic.lobby.route.token;

public record RouteTokenPayload(
		int action,
		String nickname,
		String geId,
		String roomCode,
		long exp
) {
}
