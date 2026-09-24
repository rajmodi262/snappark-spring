package com.rajmodi.snappark.slot;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rajmodi.snappark.config.SnapparkProperties;
import com.rajmodi.snappark.web.ConflictException;
import com.rajmodi.snappark.web.NotFoundException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class SlotService {

	private final SlotRepository slots;
	private final SnapparkProperties properties;
	private final Clock clock;
	private final Counter holdsGranted;
	private final Counter holdsRejected;

	public SlotService(SlotRepository slots, SnapparkProperties properties, Clock clock, MeterRegistry meters) {
		this.slots = slots;
		this.properties = properties;
		this.clock = clock;
		this.holdsGranted = Counter.builder("snappark.holds").tag("outcome", "granted").register(meters);
		this.holdsRejected = Counter.builder("snappark.holds").tag("outcome", "conflict").register(meters);
	}

	@Transactional(readOnly = true)
	public List<SlotView> list(VehicleType type, SlotStatus status) {
		return slots.findAllByOrderBySlotNumberAsc().stream()
			.filter(slot -> type == null || slot.getType() == type)
			.filter(slot -> status == null || slot.getStatus() == status)
			.map(SlotView::of)
			.toList();
	}

	@Transactional
	public HoldView hold(long slotId, String driverId) {
		ParkingSlot slot = find(slotId);
		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.holdTtl());
		if (slots.tryHold(slotId, driverId, expiresAt, now) == 0) {
			holdsRejected.increment();
			throw new ConflictException("Bay " + slot.getSlotNumber() + " is not available");
		}
		holdsGranted.increment();
		return new HoldView(slotId, slot.getSlotNumber(), driverId, expiresAt);
	}

	@Transactional
	public void release(long slotId, String driverId) {
		ParkingSlot slot = find(slotId);
		if (slots.releaseHold(slotId, driverId) == 0) {
			throw new ConflictException("Bay " + slot.getSlotNumber() + " has no hold owned by this driver");
		}
	}

	/** Frees holds whose drivers never checked in. Returns how many were released. */
	@Transactional
	public int releaseExpiredHolds() {
		return slots.releaseExpiredHolds(clock.instant());
	}

	ParkingSlot find(long slotId) {
		return slots.findById(slotId).orElseThrow(() -> new NotFoundException("Bay " + slotId + " does not exist"));
	}
}
