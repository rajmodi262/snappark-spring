package com.rajmodi.snappark.pricing;

import java.math.BigDecimal;

import com.rajmodi.snappark.slot.VehicleType;

public record PriceQuote(VehicleType vehicleType, BigDecimal baseRatePerHour, TimeBand timeBand,
		BigDecimal timeMultiplier, OccupancyBand occupancyBand, BigDecimal occupancyMultiplier,
		BigDecimal ratePerHour) {
}
