package com.rajmodi.snappark.admin;

import java.math.BigDecimal;
import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rajmodi.snappark.session.SessionRepository;
import com.rajmodi.snappark.session.SessionStatus;
import com.rajmodi.snappark.slot.ParkingSlot;
import com.rajmodi.snappark.slot.SlotRepository;
import com.rajmodi.snappark.slot.SlotStatus;
import com.rajmodi.snappark.slot.SlotView;
import com.rajmodi.snappark.slot.VehicleType;
import com.rajmodi.snappark.web.ConflictException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Operator API. Access is restricted to ROLE_ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController {

	record CreateSlotRequest(
			@NotNull @Pattern(regexp = "[A-Z]{1,2}-[0-9]{2,3}", message = "must look like C-07") String slotNumber,
			@Min(0) @Max(20) int floor,
			@NotNull VehicleType type) {
	}

	record Stats(long bays, long available, long held, long occupied, long activeSessions, BigDecimal revenue) {
	}

	private final SlotRepository slots;
	private final SessionRepository sessions;

	AdminController(SlotRepository slots, SessionRepository sessions) {
		this.slots = slots;
		this.sessions = sessions;
	}

	@PostMapping("/slots")
	@Transactional
	public ResponseEntity<SlotView> createSlot(@Valid @RequestBody CreateSlotRequest request) {
		if (slots.existsBySlotNumber(request.slotNumber())) {
			throw new ConflictException("Bay " + request.slotNumber() + " already exists");
		}
		ParkingSlot slot = slots.save(new ParkingSlot(request.slotNumber(), request.floor(), request.type()));
		return ResponseEntity.created(URI.create("/api/v1/slots/" + slot.getId())).body(SlotView.of(slot));
	}

	@GetMapping("/stats")
	@Transactional(readOnly = true)
	public Stats stats() {
		return new Stats(slots.count(), slots.countByStatus(SlotStatus.AVAILABLE), slots.countByStatus(SlotStatus.HELD),
				slots.countByStatus(SlotStatus.OCCUPIED), sessions.countByStatus(SessionStatus.ACTIVE),
				sessions.totalRevenue());
	}
}
