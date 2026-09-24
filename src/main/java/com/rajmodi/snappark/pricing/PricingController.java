package com.rajmodi.snappark.pricing;

import java.time.Clock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rajmodi.snappark.slot.SlotRepository;
import com.rajmodi.snappark.slot.SlotStatus;
import com.rajmodi.snappark.slot.VehicleType;

@RestController
@RequestMapping("/api/v1/pricing")
class PricingController {

	private final PricingService pricing;
	private final SlotRepository slots;
	private final Clock clock;

	PricingController(PricingService pricing, SlotRepository slots, Clock clock) {
		this.pricing = pricing;
		this.slots = slots;
		this.clock = clock;
	}

	/** The rate a driver would lock in by checking in right now. */
	@GetMapping("/quote")
	PriceQuote quote(@RequestParam VehicleType type) {
		return pricing.quote(type, clock.instant(), slots.countByStatus(SlotStatus.OCCUPIED), slots.count());
	}
}
