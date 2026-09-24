package com.rajmodi.snappark.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;

import org.springframework.stereotype.Service;

import com.rajmodi.snappark.config.SnapparkProperties;
import com.rajmodi.snappark.slot.VehicleType;

/**
 * Surge pricing and billing, ported from the original SnapPark desktop service. Money is
 * {@link BigDecimal} rounded half-up to the paisa; the original used double.
 */
@Service
public class PricingService {

	private static final BigDecimal BIKE_BASE = new BigDecimal("20.00");
	private static final BigDecimal CAR_BASE = new BigDecimal("50.00");
	private static final BigDecimal SUV_BASE = new BigDecimal("80.00");
	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final SnapparkProperties properties;

	public PricingService(SnapparkProperties properties) {
		this.properties = properties;
	}

	public static BigDecimal baseRate(VehicleType type) {
		return switch (type) {
			case BIKE -> BIKE_BASE;
			case CAR -> CAR_BASE;
			case SUV -> SUV_BASE;
		};
	}

	public PriceQuote quote(VehicleType type, Instant at, long occupied, long total) {
		LocalTime local = at.atZone(properties.zone()).toLocalTime();
		TimeBand timeBand = TimeBand.at(local);
		OccupancyBand occupancyBand = OccupancyBand.of(occupied, total);
		BigDecimal base = baseRate(type);
		BigDecimal rate = base.multiply(timeBand.multiplier())
			.multiply(occupancyBand.multiplier())
			.setScale(2, RoundingMode.HALF_UP);
		return new PriceQuote(type, base, timeBand, timeBand.multiplier(), occupancyBand,
				occupancyBand.multiplier(), rate);
	}

	/** Fee for a stay at a rate fixed when the driver checked in. */
	public BigDecimal fee(BigDecimal ratePerHour, Instant entry, Instant exit) {
		Duration stay = Duration.between(entry, exit);
		if (stay.compareTo(properties.gracePeriod()) <= 0) {
			return ZERO;
		}
		return ratePerHour.multiply(billableHours(stay)).setScale(2, RoundingMode.HALF_UP);
	}

	/** Billed in half-hour steps, rounded up, with a one-hour minimum. */
	public static BigDecimal billableHours(Duration stay) {
		long minutes = Math.max(1, stay.toMinutes());
		long halfHours = Math.max(2, (minutes + 29) / 30);
		return BigDecimal.valueOf(halfHours).divide(BigDecimal.valueOf(2)).setScale(1, RoundingMode.UNNECESSARY);
	}
}
