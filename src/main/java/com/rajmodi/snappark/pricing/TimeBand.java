package com.rajmodi.snappark.pricing;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Time-of-day price bands, [startHour, endHour) in local time. */
public enum TimeBand {
	LATE_NIGHT(0, 5, "0.85", "Late night"),
	EARLY_BIRD(5, 8, "1.00", "Early bird"),
	MORNING_PEAK(8, 12, "1.20", "Morning peak"),
	STANDARD(12, 17, "1.00", "Standard"),
	RUSH_HOUR(17, 21, "1.30", "Rush hour"),
	NIGHT(21, 24, "0.90", "Night");

	private final int startHour;
	private final int endHour;
	private final BigDecimal multiplier;
	private final String label;

	TimeBand(int startHour, int endHour, String multiplier, String label) {
		this.startHour = startHour;
		this.endHour = endHour;
		this.multiplier = new BigDecimal(multiplier);
		this.label = label;
	}

	public static TimeBand at(LocalTime time) {
		int hour = time.getHour();
		for (TimeBand band : values()) {
			if (hour >= band.startHour && hour < band.endHour) {
				return band;
			}
		}
		throw new IllegalStateException("No band covers hour " + hour);
	}

	public BigDecimal multiplier() {
		return multiplier;
	}

	public String label() {
		return label;
	}
}
