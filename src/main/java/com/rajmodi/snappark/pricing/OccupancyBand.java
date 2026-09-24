package com.rajmodi.snappark.pricing;

import java.math.BigDecimal;

/** Demand bands by share of bays occupied. Ordered from highest threshold down. */
public enum OccupancyBand {
	ALMOST_FULL(90, "1.25", "Almost full"),
	HIGH_DEMAND(75, "1.15", "High demand"),
	MODERATE(50, "1.05", "Moderate"),
	NORMAL(0, "1.00", "Normal");

	private final int minPercent;
	private final BigDecimal multiplier;
	private final String label;

	OccupancyBand(int minPercent, String multiplier, String label) {
		this.minPercent = minPercent;
		this.multiplier = new BigDecimal(multiplier);
		this.label = label;
	}

	/** Integer arithmetic on purpose: 75 of 100 must land in HIGH_DEMAND, never 74.999...%. */
	public static OccupancyBand of(long occupied, long total) {
		if (total <= 0) {
			return NORMAL;
		}
		for (OccupancyBand band : values()) {
			if (occupied * 100 >= band.minPercent * total) {
				return band;
			}
		}
		return NORMAL;
	}

	public BigDecimal multiplier() {
		return multiplier;
	}

	public String label() {
		return label;
	}
}
