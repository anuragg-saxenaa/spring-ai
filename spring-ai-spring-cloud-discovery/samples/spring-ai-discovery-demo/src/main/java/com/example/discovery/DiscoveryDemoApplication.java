package com.example.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class DiscoveryDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DiscoveryDemoApplication.class, args);
	}

}
