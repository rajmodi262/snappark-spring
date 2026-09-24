package com.rajmodi.snappark.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Time is injected rather than read from the system, so hold expiry and billing are testable. It ticks
 * in milliseconds so a timestamp reads the same before and after a round trip through PostgreSQL.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.tickMillis(ZoneOffset.UTC);
	}
}
