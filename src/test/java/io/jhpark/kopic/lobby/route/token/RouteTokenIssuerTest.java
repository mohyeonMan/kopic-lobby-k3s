package io.jhpark.kopic.lobby.route.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.jhpark.kopic.lobby.config.RouteTokenProperties;
import org.junit.jupiter.api.Test;

class RouteTokenIssuerTest {

	private static final String SECRET = "12345678901234567890123456789012";
	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-26T00:00:00Z"), ZoneOffset.UTC);

	@Test
	void issueIncludesRequiredClaimsAndNumericDateExpiration() throws Exception {
		RouteTokenIssuer issuer = new RouteTokenIssuer(
				new RouteTokenProperties(SECRET, Duration.ofSeconds(10)),
				FIXED_CLOCK
		);

		String token = issuer.issue(0, "pobi", "ge-1", null);

		SignedJWT jwt = SignedJWT.parse(token);
		assertThat(jwt.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
		assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
		assertThat(jwt.getJWTClaimsSet().getIntegerClaim("action")).isEqualTo(0);
		assertThat(jwt.getJWTClaimsSet().getStringClaim("nickname")).isEqualTo("pobi");
		assertThat(jwt.getJWTClaimsSet().getStringClaim("geId")).isEqualTo("ge-1");
		assertThat(jwt.getJWTClaimsSet().getClaim("roomCode")).isNull();
		assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant().getEpochSecond())
				.isEqualTo(Instant.parse("2026-05-26T00:00:10Z").getEpochSecond());
	}

	@Test
	void issueIncludesRoomCodeOnlyWhenProvided() throws Exception {
		RouteTokenIssuer issuer = new RouteTokenIssuer(
				new RouteTokenProperties(SECRET, Duration.ofSeconds(10)),
				FIXED_CLOCK
		);

		String token = issuer.issue(0, "pobi", "ge-1", "ABCDEF");

		SignedJWT jwt = SignedJWT.parse(token);
		assertThat(jwt.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
		assertThat(jwt.getJWTClaimsSet().getStringClaim("roomCode")).isEqualTo("ABCDEF");
	}
}
