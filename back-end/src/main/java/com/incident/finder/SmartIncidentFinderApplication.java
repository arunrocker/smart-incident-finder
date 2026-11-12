package com.incident.finder;

import com.incident.finder.service.IncidentSearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
		IncidentSearchProperties.class
})
public class SmartIncidentFinderApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartIncidentFinderApplication.class, args);
	}

}
