package com.rajmodi.snappark.slot;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "parking_slot")
public class ParkingSlot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "slot_number", nullable = false, unique = true, length = 16)
	private String slotNumber;

	@Column(name = "floor_no", nullable = false)
	private int floor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private VehicleType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private SlotStatus status;

	@Column(name = "held_by", length = 64)
	private String heldBy;

	@Column(name = "hold_expires_at")
	private Instant holdExpiresAt;

	@Version
	private long version;

	protected ParkingSlot() {
	}

	public ParkingSlot(String slotNumber, int floor, VehicleType type) {
		this.slotNumber = slotNumber;
		this.floor = floor;
		this.type = type;
		this.status = SlotStatus.AVAILABLE;
	}

	public Long getId() {
		return id;
	}

	public String getSlotNumber() {
		return slotNumber;
	}

	public int getFloor() {
		return floor;
	}

	public VehicleType getType() {
		return type;
	}

	public SlotStatus getStatus() {
		return status;
	}

	public String getHeldBy() {
		return heldBy;
	}

	public Instant getHoldExpiresAt() {
		return holdExpiresAt;
	}
}
