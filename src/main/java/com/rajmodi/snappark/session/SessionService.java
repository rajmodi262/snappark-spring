package com.rajmodi.snappark.session;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rajmodi.snappark.pricing.PriceQuote;
import com.rajmodi.snappark.pricing.PricingService;
import com.rajmodi.snappark.slot.ParkingSlot;
import com.rajmodi.snappark.slot.SlotRepository;
import com.rajmodi.snappark.slot.SlotStatus;
import com.rajmodi.snappark.web.ConflictException;
import com.rajmodi.snappark.web.NotFoundException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class SessionService {

	private final SessionRepository sessions;
	private final SlotRepository slots;
	private final PricingService pricing;
	private final Clock clock;
	private final Counter checkIns;
	private final Counter checkOuts;

	public SessionService(SessionRepository sessions, SlotRepository slots, PricingService pricing, Clock clock,
			MeterRegistry meters) {
		this.sessions = sessions;
		this.slots = slots;
		this.pricing = pricing;
		this.clock = clock;
		this.checkIns = meters.counter("snappark.checkins");
		this.checkOuts = meters.counter("snappark.checkouts");
	}

	/** Converts the driver's live hold into a session, fixing the hourly rate at this moment. */
	@Transactional
	public SessionView checkIn(long slotId, String driverId, String vehicleNumber) {
		ParkingSlot slot = slots.findById(slotId)
			.orElseThrow(() -> new NotFoundException("Bay " + slotId + " does not exist"));
		Instant now = clock.instant();
		if (slots.occupyHeld(slotId, driverId, now) == 0) {
			throw new ConflictException(
					"No live hold on bay " + slot.getSlotNumber() + " for this driver; hold the bay first");
		}
		PriceQuote quote = pricing.quote(slot.getType(), now, slots.countByStatus(SlotStatus.OCCUPIED),
				slots.count());
		ParkingSession session = sessions.save(new ParkingSession(slots.getReferenceById(slotId), driverId,
				vehicleNumber, slot.getType(), now, quote.ratePerHour()));
		checkIns.increment();
		return new SessionView(session.getId(), slotId, slot.getSlotNumber(), vehicleNumber, slot.getType(), now,
				quote.ratePerHour(), SessionStatus.ACTIVE);
	}

	@Transactional(readOnly = true)
	public SessionView get(long sessionId) {
		return SessionView.of(find(sessionId));
	}

	@Transactional
	public Receipt checkOut(long sessionId) {
		ParkingSession session = find(sessionId);
		if (session.getStatus() == SessionStatus.CLOSED) {
			throw new ConflictException("Session " + sessionId + " is already checked out");
		}
		Instant exit = clock.instant();
		BigDecimal amount = pricing.fee(session.getRatePerHour(), session.getEntryTime(), exit);
		// Guards the double-tap: of two concurrent checkouts, only one sees the row still ACTIVE.
		if (sessions.close(sessionId, exit, amount) == 0) {
			throw new ConflictException("Session " + sessionId + " is already checked out");
		}
		slots.vacate(session.getSlot().getId());
		checkOuts.increment();
		return new Receipt(sessionId, session.getSlot().getSlotNumber(), session.getVehicleNumber(),
				session.getEntryTime(), exit,
				PricingService.billableHours(Duration.between(session.getEntryTime(), exit)),
				session.getRatePerHour(), amount);
	}

	private ParkingSession find(long sessionId) {
		return sessions.findWithSlot(sessionId)
			.orElseThrow(() -> new NotFoundException("Session " + sessionId + " does not exist"));
	}
}
