package com.rajmodi.snappark.slot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.rajmodi.snappark.support.AbstractIntegrationTest;

class HoldSweeperTest extends AbstractIntegrationTest {

	@Autowired
	private HoldSweeper sweeper;

	@Test
	void sweepsOnlyHoldsThatHaveLapsed() throws Exception {
		long stale = slotId("C-01");
		long fresh = slotId("C-02");
		hold(stale, "driver-1");
		clock.advance(Duration.ofMinutes(4));
		hold(fresh, "driver-2");
		clock.advance(Duration.ofMinutes(2)); // first hold is now 6 minutes old, second 2

		sweeper.sweep();

		assertThat(slotStatus(stale)).isEqualTo("AVAILABLE");
		assertThat(slotStatus(fresh)).isEqualTo("HELD");
	}
}
