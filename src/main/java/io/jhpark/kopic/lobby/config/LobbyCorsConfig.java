package io.jhpark.kopic.lobby.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class LobbyCorsConfig implements WebMvcConfigurer {

	private static final String[] LOCAL_CLIENT_ORIGINS = {
			"http://localhost:5173",
			"http://127.0.0.1:5173"
	};

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/routes/**")
				.allowedOrigins(LOCAL_CLIENT_ORIGINS)
				.allowedMethods("POST", "OPTIONS")
				.allowedHeaders("*");
	}
}
