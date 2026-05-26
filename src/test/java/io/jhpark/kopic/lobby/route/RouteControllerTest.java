package io.jhpark.kopic.lobby.route;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jhpark.kopic.lobby.support.LobbyException;
import io.jhpark.kopic.lobby.support.LobbyExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

	@Mock
	private RouteService routeService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new RouteController(routeService))
				.setControllerAdvice(new LobbyExceptionHandler())
				.build();
	}

	@Test
	void quickReturnsOnlyRouteToken() throws Exception {
		when(routeService.quick(new RouteRequest.NicknameOnly("pobi")))
				.thenReturn(new RouteResponse("token"));

		mockMvc.perform(post("/routes/quick")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nickname":"pobi"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.routeToken").value("token"))
				.andExpect(jsonPath("$.geId").doesNotExist())
				.andExpect(jsonPath("$.action").doesNotExist())
				.andExpect(jsonPath("$.roomCode").doesNotExist());
	}

	@Test
	void privateCreateReturnsOnlyRouteToken() throws Exception {
		when(routeService.privateCreate(new RouteRequest.NicknameOnly("pobi")))
				.thenReturn(new RouteResponse("token"));

		mockMvc.perform(post("/routes/private")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nickname":"pobi"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.routeToken").value("token"))
				.andExpect(jsonPath("$.geId").doesNotExist())
				.andExpect(jsonPath("$.action").doesNotExist())
				.andExpect(jsonPath("$.roomCode").doesNotExist());
	}

	@Test
	void privateJoinReturnsOnlyRouteToken() throws Exception {
		when(routeService.privateJoin(new RouteRequest.PrivateJoin("pobi", "ABCDEF")))
				.thenReturn(new RouteResponse("token"));

		mockMvc.perform(post("/routes/private/join")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nickname":"pobi","roomCode":"ABCDEF"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.routeToken").value("token"))
				.andExpect(jsonPath("$.geId").doesNotExist())
				.andExpect(jsonPath("$.action").doesNotExist());
	}

	@Test
	void lobbyExceptionReturnsFailureBodyWithoutRouteToken() throws Exception {
		when(routeService.quick(new RouteRequest.NicknameOnly("pobi")))
				.thenThrow(LobbyException.routeUnavailable("No ACTIVE GE is available"));

		mockMvc.perform(post("/routes/quick")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nickname":"pobi"}
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.reason").value("ROUTE_UNAVAILABLE"))
				.andExpect(jsonPath("$.message").value("No ACTIVE GE is available"))
				.andExpect(jsonPath("$.routeToken").doesNotExist());
	}
}
