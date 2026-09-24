package com.rajmodi.snappark.session;

import java.math.BigDecimal;
import java.time.Instant;

import com.rajmodi.snappark.slot.VehicleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

record CheckInRequest(
		@NotNull @Positive Long slotId,
		@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9-]+", message = "letters, digits and hyphens only")
		String driverId,
		@NotBlank @Pattern(regexp = "[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{4}",
				message = "must be an Indian registration number such as MH12AB1234")
		String vehicleNumber) {
}

record SessionView(Long id, Long slotId, String slotNumber, String vehicleNumber, VehicleType vehicleType,
		Instant entryTime, BigDecimal ratePerHour, SessionStatus status) {

	static SessionView of(ParkingSession session) {
		return new SessionView(session.getId(), session.getSlot().getId(), session.getSlot().getSlotNumber(),
				session.getVehicleNumber(), session.getVehicleType(), session.getEntryTime(), session.getRatePerHour(),
				session.getStatus());
	}
}

record Receipt(Long sessionId, String slotNumber, String vehicleNumber, Instant entryTime, Instant exitTime,
		BigDecimal billableHours, BigDecimal ratePerHour, BigDecimal amount) {
}
