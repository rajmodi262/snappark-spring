package com.rajmodi.snappark.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL in a throwaway container, plus a controllable clock. */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfra {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:16-alpine");
	}

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock(AbstractIntegrationTest.START);
	}
}
