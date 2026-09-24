package com.rajmodi.snappark.session;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sessions")
class SessionController {

	private final SessionService sessions;

	SessionController(SessionService sessions) {
		this.sessions = sessions;
	}

	@PostMapping
	ResponseEntity<SessionView> checkIn(@Valid @RequestBody CheckInRequest request) {
		SessionView session = sessions.checkIn(request.slotId(), request.driverId(), request.vehicleNumber());
		return ResponseEntity.created(URI.create("/api/v1/sessions/" + session.id())).body(session);
	}

	@GetMapping("/{sessionId}")
	SessionView get(@PathVariable long sessionId) {
		return sessions.get(sessionId);
	}

	@PostMapping("/{sessionId}/checkout")
	Receipt checkOut(@PathVariable long sessionId) {
		return sessions.checkOut(sessionId);
	}
}
