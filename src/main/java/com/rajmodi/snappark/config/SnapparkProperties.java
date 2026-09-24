package com.rajmodi.snappark.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the booking flow.
 *
 * @param holdTtl     how long a driver may hold a bay before checking in (the original kiosk used 300 s)
 * @param gracePeriod stays at or under this length are free
 * @param zone        time zone used to decide the time-of-day price band
 * @param admin       operator account for the admin API; an empty password hash locks the admin API
 */
@ConfigurationProperties("snappark")
public record SnapparkProperties(Duration holdTtl, Duration gracePeriod, ZoneId zone, Admin admin) {

	public record Admin(String username, String passwordHash) {
	}
}
