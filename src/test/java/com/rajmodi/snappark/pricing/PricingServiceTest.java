package com.rajmodi.snappark.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.rajmodi.snappark.config.SnapparkProperties;
import com.rajmodi.snappark.slot.VehicleType;

class PricingServiceTest {

	private final PricingService pricing = new PricingService(
			new SnapparkProperties(Duration.ofMinutes(5), Duration.ofMinutes(5), ZoneId.of("Asia/Kolkata"), null));

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({ "00:00, LATE_NIGHT", "04:59, LATE_NIGHT", "05:00, EARLY_BIRD", "07:59, EARLY_BIRD",
			"08:00, MORNING_PEAK", "11:59, MORNING_PEAK", "12:00, STANDARD", "16:59, STANDARD", "17:00, RUSH_HOUR",
			"20:59, RUSH_HOUR", "21:00, NIGHT", "23:59, NIGHT" })
	void timeBandBoundaries(LocalTime time, TimeBand expected) {
		assertThat(TimeBand.at(time)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0}/{1} occupied -> {2}")
	@CsvSource({ "0, 100, NORMAL", "49, 100, NORMAL", "50, 100, MODERATE", "74, 100, MODERATE", "75, 100, HIGH_DEMAND",
			"89, 100, HIGH_DEMAND", "90, 100, ALMOST_FULL", "100, 100, ALMOST_FULL", "3, 4, HIGH_DEMAND",
			"9, 10, ALMOST_FULL", "0, 0, NORMAL" })
	void occupancyBandBoundaries(long occupied, long total, OccupancyBand expected) {
		assertThat(OccupancyBand.of(occupied, total)).isEqualTo(expected);
	}

	@Test
	void rushHourAtNinetyPercentStacksBothSurcharges() {
		// 12:30Z is 18:00 IST: rush hour (1.30) on an almost full lot (1.25)
		PriceQuote quote = pricing.quote(VehicleType.SUV, Instant.parse("2026-09-24T12:30:00Z"), 9, 10);

		assertThat(quote.timeBand()).isEqualTo(TimeBand.RUSH_HOUR);
		assertThat(quote.occupancyBand()).isEqualTo(OccupancyBand.ALMOST_FULL);
		assertThat(quote.ratePerHour()).isEqualByComparingTo("130.00");
	}

	@Test
	void morningPeakOnAModerateLot() {
		PriceQuote quote = pricing.quote(VehicleType.CAR, Instant.parse("2026-09-24T03:00:00Z"), 5, 10);

		assertThat(quote.ratePerHour()).isEqualByComparingTo("63.00");
	}

	@Test
	void lateNightDiscountUsesLocalTimeNotUtc() {
		// 20:00Z is 01:30 IST the next day
		PriceQuote quote = pricing.quote(VehicleType.BIKE, Instant.parse("2026-09-24T20:00:00Z"), 0, 10);

		assertThat(quote.timeBand()).isEqualTo(TimeBand.LATE_NIGHT);
		assertThat(quote.ratePerHour()).isEqualByComparingTo("17.00");
	}

	@ParameterizedTest(name = "{0} min -> {1} h")
	@CsvSource({ "0, 1.0", "1, 1.0", "30, 1.0", "60, 1.0", "61, 1.5", "90, 1.5", "91, 2.0", "240, 4.0" })
	void billsInHalfHourStepsWithAOneHourMinimum(long minutes, String hours) {
		assertThat(PricingService.billableHours(Duration.ofMinutes(minutes))).isEqualByComparingTo(hours);
	}

	@Test
	void staysInsideTheGracePeriodAreFree() {
		Instant entry = Instant.parse("2026-09-24T06:30:00Z");

		assertThat(pricing.fee(new BigDecimal("50.00"), entry, entry.plus(Duration.ofMinutes(5))))
			.isEqualByComparingTo("0.00");
		assertThat(pricing.fee(new BigDecimal("50.00"), entry, entry.plus(Duration.ofMinutes(5).plusSeconds(1))))
			.isEqualByComparingTo("50.00");
	}

	@Test
	void feesRoundHalfUpToThePaisa() {
		Instant entry = Instant.parse("2026-09-24T06:30:00Z");

		// 33.33 x 1.5 h = 49.995, which must bill as 50.00, not 49.99
		BigDecimal fee = pricing.fee(new BigDecimal("33.33"), entry, entry.plus(Duration.ofMinutes(75)));

		assertThat(fee).isEqualByComparingTo("50.00");
		assertThat(fee.scale()).isEqualTo(2);
	}
}
