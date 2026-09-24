package com.rajmodi.snappark.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.rajmodi.snappark.support.AbstractIntegrationTest;

/** The driver journey end to end over HTTP: hold, check in, check out, and every way it can fail. */
class ParkingFlowTest extends AbstractIntegrationTest {

	@Test
	void holdCheckInAndCheckOutProducesAReceipt() throws Exception {
		long bay = slotId("C-01");

		hold(bay, "driver-1").andExpect(status().isOk())
			.andExpect(jsonPath("$.slotNumber").value("C-01"))
			.andExpect(jsonPath("$.expiresAt").value("2026-09-24T06:35:00Z"));

		var checkIn = checkIn(bay, "driver-1", "MH12AB1234").andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.ratePerHour").value(50.00))
			.andExpect(jsonPath("$.status").value("ACTIVE"));
		long session = idFrom(checkIn);
		assertThat(slotStatus(bay)).isEqualTo("OCCUPIED");

		clock.advance(Duration.ofMinutes(95));
		mvc.perform(post("/api/v1/sessions/{id}/checkout", session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.billableHours").value(2.0))
			.andExpect(jsonPath("$.amount").value(100.00));
		assertThat(slotStatus(bay)).isEqualTo("AVAILABLE");

		mvc.perform(get("/api/v1/sessions/{id}", session)).andExpect(jsonPath("$.status").value("CLOSED"));
	}

	@Test
	void theRateIsLockedAtCheckInEvenIfThePeakStartsLater() throws Exception {
		long bay = slotId("C-02");
		clock.set(java.time.Instant.parse("2026-09-24T11:00:00Z")); // 16:30 IST, standard band
		hold(bay, "driver-1");
		long session = idFrom(checkIn(bay, "driver-1", "KA01XY9876"));

		clock.advance(Duration.ofMinutes(60)); // now 17:30 IST, inside rush hour
		mvc.perform(post("/api/v1/sessions/{id}/checkout", session))
			.andExpect(jsonPath("$.ratePerHour").value(50.00))
			.andExpect(jsonPath("$.amount").value(50.00));
	}

	@Test
	void rushHourCheckInPaysTheSurcharge() throws Exception {
		clock.set(java.time.Instant.parse("2026-09-24T12:00:00Z")); // 17:30 IST
		long bay = slotId("C-05");
		hold(bay, "driver-1");

		checkIn(bay, "driver-1", "DL3CAB1234").andExpect(jsonPath("$.ratePerHour").value(65.00));
	}

	@Test
	void aHeldBayCannotBeHeldAgain() throws Exception {
		long bay = slotId("C-01");
		hold(bay, "driver-1").andExpect(status().isOk());

		hold(bay, "driver-2").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Bay C-01 is not available"));
	}

	@Test
	void checkInRequiresTheCallersOwnLiveHold() throws Exception {
		long bay = slotId("C-01");

		checkIn(bay, "driver-1", "MH12AB1234").andExpect(status().isConflict());

		hold(bay, "driver-1");
		checkIn(bay, "driver-2", "MH12AB1234").andExpect(status().isConflict());

		clock.advance(Duration.ofMinutes(6));
		checkIn(bay, "driver-1", "MH12AB1234").andExpect(status().isConflict());
	}

	@Test
	void onlyTheOwnerCanReleaseAHold() throws Exception {
		long bay = slotId("B-01");
		hold(bay, "driver-1");

		mvc.perform(delete("/api/v1/slots/{id}/hold", bay).contentType(MediaType.APPLICATION_JSON)
			.content("{\"driverId\":\"driver-2\"}")).andExpect(status().isConflict());
		mvc.perform(delete("/api/v1/slots/{id}/hold", bay).contentType(MediaType.APPLICATION_JSON)
			.content("{\"driverId\":\"driver-1\"}")).andExpect(status().isNoContent());

		assertThat(slotStatus(bay)).isEqualTo("AVAILABLE");
	}

	@Test
	void aSessionCannotBeCheckedOutTwice() throws Exception {
		long bay = slotId("S-02");
		hold(bay, "driver-1");
		long session = idFrom(checkIn(bay, "driver-1", "MH12AB1234"));

		mvc.perform(post("/api/v1/sessions/{id}/checkout", session)).andExpect(status().isOk());
		mvc.perform(post("/api/v1/sessions/{id}/checkout", session)).andExpect(status().isConflict());
	}

	@Test
	void aShortStayInsideTheGracePeriodIsFree() throws Exception {
		long bay = slotId("B-02");
		hold(bay, "driver-1");
		long session = idFrom(checkIn(bay, "driver-1", "MH12AB1234"));

		clock.advance(Duration.ofMinutes(4));
		mvc.perform(post("/api/v1/sessions/{id}/checkout", session)).andExpect(jsonPath("$.amount").value(0.00));
	}

	@Test
	void invalidInputIsRejectedWithFieldLevelProblemDetails() throws Exception {
		long bay = slotId("C-01");

		checkIn(bay, "driver-1", "not-a-plate").andExpect(status().isBadRequest())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.errors.vehicleNumber").exists());

		hold(bay, "drop table;").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.driverId").value("letters, digits and hyphens only"));

		mvc.perform(get("/api/v1/slots").param("type", "TRUCK")).andExpect(status().isBadRequest());
	}

	@Test
	void unknownResourcesAreNotFound() throws Exception {
		hold(999_999, "driver-1").andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/sessions/{id}", 999_999)).andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/sessions/{id}/checkout", 999_999)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/nothing-here")).andExpect(status().isNotFound());
	}

	@Test
	void baysCanBeFilteredByTypeAndStatus() throws Exception {
		hold(slotId("B-03"), "driver-1");

		mvc.perform(get("/api/v1/slots").param("type", "BIKE"))
			.andExpect(jsonPath("$.length()").value(4));
		mvc.perform(get("/api/v1/slots").param("type", "BIKE").param("status", "AVAILABLE"))
			.andExpect(jsonPath("$.length()").value(3));
		mvc.perform(get("/api/v1/slots")).andExpect(jsonPath("$.length()").value(12));
	}

	@Test
	void theQuoteReflectsTheCurrentBand() throws Exception {
		mvc.perform(get("/api/v1/pricing/quote").param("type", "SUV"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timeBand").value("STANDARD"))
			.andExpect(jsonPath("$.ratePerHour").value(80.00));
	}
}
