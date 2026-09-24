package com.rajmodi.snappark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class SnapparkApplication {

	public static void main(String[] args) {
		SpringApplication.run(SnapparkApplication.class, args);
	}
}
