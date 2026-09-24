package com.rajmodi.snappark.slot;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record HoldRequest(
		@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9-]+", message = "letters, digits and hyphens only")
		String driverId) {
}

record HoldView(long slotId, String slotNumber, String driverId, Instant expiresAt) {
}
