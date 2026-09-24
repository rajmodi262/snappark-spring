package com.rajmodi.snappark.admin;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.rajmodi.snappark.support.AbstractIntegrationTest;

class AdminAndSecurityTest extends AbstractIntegrationTest {

	private static final String NEW_BAY = """
			{"slotNumber": "T-01", "floor": 2, "type": "CAR"}""";

	@Test
	void theAdminApiRejectsMissingOrWrongCredentials() throws Exception {
		mvc.perform(get("/api/v1/admin/stats")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/admin/stats").with(httpBasic(ADMIN_USER, "wrong-password")))
			.andExpect(status().isUnauthorized());
		mvc.perform(createBay(NEW_BAY)).andExpect(status().isUnauthorized());
	}

	@Test
	void anOperatorCanAddABayOnceAndReadStats() throws Exception {
		mvc.perform(createBay(NEW_BAY).with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slotNumber").value("T-01"))
			.andExpect(jsonPath("$.status").value("AVAILABLE"));
		mvc.perform(createBay(NEW_BAY).with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
			.andExpect(status().isConflict());

		hold(slotId("C-01"), "driver-1");
		mvc.perform(get("/api/v1/admin/stats").with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bays").value(13))
			.andExpect(jsonPath("$.held").value(1))
			.andExpect(jsonPath("$.available").value(12));
	}

	@Test
	void badBayDefinitionsAreRejected() throws Exception {
		mvc.perform(createBay("""
				{"slotNumber": "car seven", "floor": 99, "type": "CAR"}""").with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.slotNumber").exists())
			.andExpect(jsonPath("$.errors.floor").exists());
	}

	@Test
	void healthIsPublicButMetricsNeedAnOperator() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
		mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

		mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());

		hold(slotId("C-06"), "driver-1");
		mvc.perform(get("/actuator/prometheus").with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("snappark_holds_total")));
	}

	@Test
	void anythingOutsideTheApiIsDenied() throws Exception {
		mvc.perform(get("/actuator/env").with(httpBasic(ADMIN_USER, ADMIN_PASSWORD)))
			.andExpect(status().isNotFound());
		mvc.perform(get("/internal/debug")).andExpect(status().isUnauthorized());
	}

	@Test
	void responsesCarrySecurityHeaders() throws Exception {
		mvc.perform(get("/api/v1/slots"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
	}

	private static MockHttpServletRequestBuilder createBay(String body) {
		return post("/api/v1/admin/slots").contentType(MediaType.APPLICATION_JSON).content(body);
	}
}
