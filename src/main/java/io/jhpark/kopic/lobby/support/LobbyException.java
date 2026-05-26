package io.jhpark.kopic.lobby.support;

public class LobbyException extends RuntimeException {

	private final Reason reason;

	public LobbyException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public LobbyException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public static LobbyException routeUnavailable(String message) {
		return new LobbyException(Reason.ROUTE_UNAVAILABLE, message);
	}

	public static LobbyException routeUnavailable(String message, Throwable cause) {
		return new LobbyException(Reason.ROUTE_UNAVAILABLE, message, cause);
	}

	public static LobbyException invalidRequest(String message) {
		return new LobbyException(Reason.INVALID_REQUEST, message);
	}

	public enum Reason {
		INVALID_REQUEST,
		ROUTE_UNAVAILABLE
	}
}
