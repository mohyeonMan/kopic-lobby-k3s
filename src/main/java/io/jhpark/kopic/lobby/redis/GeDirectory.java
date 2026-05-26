package io.jhpark.kopic.lobby.redis;

import java.util.Optional;

public interface GeDirectory {

	Optional<String> findQuickRouteGeId();

	Optional<String> findPrivateCreateGeId();

	Optional<String> findGeIdByRoomCode(String roomCode);
}
