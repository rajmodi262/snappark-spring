package com.rajmodi.snappark.slot;

public enum SlotStatus {
	/** Free to hold. */
	AVAILABLE,
	/** Claimed by one driver for a short window while they check in. */
	HELD,
	/** A parking session is running on this bay. */
	OCCUPIED
}
