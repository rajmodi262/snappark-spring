package com.rajmodi.snappark.slot;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/slots")
class SlotController {

	private final SlotService slots;

	SlotController(SlotService slots) {
		this.slots = slots;
	}

	@GetMapping
	List<SlotView> list(@RequestParam(required = false) VehicleType type,
			@RequestParam(required = false) SlotStatus status) {
		return slots.list(type, status);
	}

	@PostMapping("/{slotId}/hold")
	HoldView hold(@PathVariable long slotId, @Valid @RequestBody HoldRequest request) {
		return slots.hold(slotId, request.driverId());
	}

	@DeleteMapping("/{slotId}/hold")
	ResponseEntity<Void> release(@PathVariable long slotId, @Valid @RequestBody HoldRequest request) {
		slots.release(slotId, request.driverId());
		return ResponseEntity.noContent().build();
	}
}
