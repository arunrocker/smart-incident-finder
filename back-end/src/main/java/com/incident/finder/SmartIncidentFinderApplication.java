package com.incident.finder;

import com.incident.finder.service.IncidentSearchProperties;
import com.incident.finder.service.ClaudeApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
		IncidentSearchProperties.class,
		ClaudeApiProperties.class
})
public class SmartIncidentFinderApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartIncidentFinderApplication.class, args);
	}

}
