package io.jhpark.kopic.lobby;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KopicLobbyApplication {

	public static void main(String[] args) {
		SpringApplication.run(KopicLobbyApplication.class, args);
	}

}
