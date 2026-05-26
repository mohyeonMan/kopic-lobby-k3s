package io.jhpark.kopic.lobby.support;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class LobbyExceptionHandler {

	@ExceptionHandler(LobbyException.class)
	public ResponseEntity<Map<String, String>> handleLobbyException(LobbyException exception) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(Map.of(
						"reason", exception.reason().name(),
						"message", exception.getMessage()
				));
	}
}
