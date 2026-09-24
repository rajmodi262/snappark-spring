package com.rajmodi.snappark.slot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Returns abandoned holds to the pool. Not needed for correctness (an expired hold can already be
 * taken over), but it keeps the availability the drivers see honest.
 */
@Component
class HoldSweeper {

	private static final Logger log = LoggerFactory.getLogger(HoldSweeper.class);

	private final SlotService slots;

	HoldSweeper(SlotService slots) {
		this.slots = slots;
	}

	@Scheduled(fixedDelayString = "${snappark.sweep-interval:PT30S}")
	void sweep() {
		int released = slots.releaseExpiredHolds();
		if (released > 0) {
			log.info("Released {} expired bay hold(s)", released);
		}
	}
}
