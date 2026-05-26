package io.jhpark.kopic.lobby.route.token;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jhpark.kopic.lobby.config.RouteTokenProperties;
import io.jhpark.kopic.lobby.support.LobbyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RouteTokenIssuer {

	private final RouteTokenProperties routeTokenProperties;
	private final Clock clock;

	@Autowired
	public RouteTokenIssuer(RouteTokenProperties routeTokenProperties) {
		this(routeTokenProperties, Clock.systemUTC());
	}

	RouteTokenIssuer(RouteTokenProperties routeTokenProperties, Clock clock) {
		this.routeTokenProperties = routeTokenProperties;
		this.clock = clock;
	}

	public String issue(int action, String nickname, String geId, String roomCode) {
		RouteTokenPayload payload = new RouteTokenPayload(
				action,
				nickname,
				geId,
				roomCode,
				expirationEpochSecond()
		);
		return sign(payload);
	}

	private String sign(RouteTokenPayload payload) {
		String secret = routeTokenProperties.secret();
		if (secret == null || secret.isBlank()) {
			throw LobbyException.routeUnavailable("Route token secret is not configured");
		}

		try {
			SignedJWT jwt = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.HS256)
							.type(JOSEObjectType.JWT)
							.build(),
					claims(payload)
			);
			jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
			return jwt.serialize();
		} catch (JOSEException exception) {
			throw LobbyException.routeUnavailable("Route token issuance failed", exception);
		}
	}

	private JWTClaimsSet claims(RouteTokenPayload payload) {
		JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
				.claim("action", payload.action())
				.claim("nickname", payload.nickname())
				.claim("geId", payload.geId())
				.expirationTime(Date.from(Instant.ofEpochSecond(payload.exp())));

		if (payload.roomCode() != null) {
			builder.claim("roomCode", payload.roomCode());
		}

		return builder.build();
	}

	private long expirationEpochSecond() {
		Duration ttl = routeTokenProperties.ttl();
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw LobbyException.routeUnavailable("Route token ttl is not configured");
		}
		return clock.instant().plus(ttl).getEpochSecond();
	}
}
