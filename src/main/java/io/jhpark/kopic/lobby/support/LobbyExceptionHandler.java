package io.jhpark.kopic.lobby.support;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class LobbyExceptionHandler {

	@ExceptionHandler(LobbyException.class)
	public ResponseEntity<Map<String, String>> handleLobbyException(LobbyException exception) {
		if (exception.getCause() == null) {
			log.warn("lobby request failed reason={} message={}", exception.getReason(), exception.getMessage());
		} else {
			log.warn("lobby request failed reason={} message={}", exception.getReason(), exception.getMessage(), exception);
		}

		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(Map.of(
						"reason", exception.getReason().name(),
						"message", exception.getMessage()
				));
	}
}
