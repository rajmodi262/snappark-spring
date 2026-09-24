package com.rajmodi.snappark.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.rajmodi.snappark.slot.SlotService;
import com.rajmodi.snappark.support.AbstractIntegrationTest;
import com.rajmodi.snappark.web.ConflictException;

/**
 * The guarantees the service exists for, checked under real contention on PostgreSQL: every thread
 * is released at the same instant, and each race must end with exactly one winner and no errors.
 */
class BookingConcurrencyTest extends AbstractIntegrationTest {

	private static final int THREADS = 32;

	@Autowired
	private SlotService slots;

	@Autowired
	private SessionService sessions;

	@RepeatedTest(5)
	void exactlyOneDriverWinsAFreeBay() throws Exception {
		long bay = slotId("C-03");

		int winners = race(THREADS, driver -> slots.hold(bay, "driver-" + driver));

		assertThat(winners).isEqualTo(1);
		assertThat(slotStatus(bay)).isEqualTo("HELD");
	}

	/**
	 * The case the original in-memory lock manager got wrong: when a hold has expired, every waiting
	 * driver sees it as free at once, and its check-then-put let more than one of them "win".
	 */
	@RepeatedTest(5)
	void exactlyOneDriverTakesOverAnExpiredHold() throws Exception {
		long bay = slotId("C-04");
		slots.hold(bay, "abandoned");
		clock.advance(Duration.ofMinutes(6));

		int winners = race(THREADS, driver -> slots.hold(bay, "driver-" + driver));

		assertThat(winners).isEqualTo(1);
		String holder = jdbc.queryForObject("select held_by from parking_slot where id = ?", String.class, bay);
		assertThat(holder).startsWith("driver-");
	}

	@Test
	void aDoubleTappedCheckoutBillsOnce() throws Exception {
		long bay = slotId("S-01");
		slots.hold(bay, "driver-1");
		long session = sessions.checkIn(bay, "driver-1", "MH12AB1234").id();
		clock.advance(Duration.ofMinutes(40));

		int winners = race(16, attempt -> sessions.checkOut(session));

		assertThat(winners).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from parking_session where status = 'CLOSED'", Long.class))
			.isEqualTo(1);
		assertThat(slotStatus(bay)).isEqualTo("AVAILABLE");
	}

	/**
	 * Runs {@code action} on {@code threads} threads released together. Returns how many succeeded;
	 * a {@link ConflictException} counts as a clean loss, and any other exception fails the test.
	 */
	private static int race(int threads, IntConsumer action) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch ready = new CountDownLatch(threads);
			CountDownLatch go = new CountDownLatch(1);
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				int id = i;
				results.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					try {
						action.accept(id);
						return true;
					}
					catch (ConflictException lostTheRace) {
						return false;
					}
				}));
			}
			ready.await();
			go.countDown();
			int winners = 0;
			for (Future<Boolean> result : results) {
				if (result.get()) {
					winners++;
				}
			}
			return winners;
		}
		finally {
			pool.shutdownNow();
		}
	}
}
