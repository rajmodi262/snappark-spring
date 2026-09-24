package com.rajmodi.snappark.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Boots the whole application against PostgreSQL 16 in Testcontainers. The scheduled sweeper is
 * pushed out to an hour so tests decide when holds are swept.
 */
@SpringBootTest(properties = "snappark.sweep-interval=PT1H")
@AutoConfigureMockMvc
@AutoConfigureMetrics
@Import(TestInfra.class)
public abstract class AbstractIntegrationTest {

	/** 12:00 in Asia/Kolkata: the STANDARD (1.00x) time band. */
	public static final Instant START = Instant.parse("2026-09-24T06:30:00Z");

	protected static final String ADMIN_USER = "admin";
	protected static final String ADMIN_PASSWORD = "correct-horse-battery-staple";

	@DynamicPropertySource
	static void adminAccount(DynamicPropertyRegistry registry) {
		registry.add("snappark.admin.username", () -> ADMIN_USER);
		registry.add("snappark.admin.password-hash", () -> new BCryptPasswordEncoder().encode(ADMIN_PASSWORD));
	}

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected MutableClock clock;

	@BeforeEach
	void resetState() {
		jdbc.update("delete from parking_session");
		jdbc.update("delete from parking_slot where slot_number like 'T-%'");
		jdbc.update("update parking_slot set status = 'AVAILABLE', held_by = null, hold_expires_at = null");
		clock.set(START);
	}

	protected long slotId(String slotNumber) {
		Long id = jdbc.queryForObject("select id from parking_slot where slot_number = ?", Long.class, slotNumber);
		return id;
	}

	protected String slotStatus(long slotId) {
		return jdbc.queryForObject("select status from parking_slot where id = ?", String.class, slotId);
	}

	protected ResultActions hold(long slotId, String driverId) throws Exception {
		return mvc.perform(post("/api/v1/slots/{id}/hold", slotId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"driverId\":\"" + driverId + "\"}"));
	}

	protected ResultActions checkIn(long slotId, String driverId, String vehicleNumber) throws Exception {
		return mvc.perform(post("/api/v1/sessions")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"slotId": %d, "driverId": "%s", "vehicleNumber": "%s"}""".formatted(slotId, driverId, vehicleNumber)));
	}

	protected static long idFrom(ResultActions result) throws Exception {
		Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
		return id.longValue();
	}
}
