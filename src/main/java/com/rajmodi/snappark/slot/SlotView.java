package com.rajmodi.snappark.slot;

public record SlotView(Long id, String slotNumber, int floor, VehicleType type, SlotStatus status) {

	public static SlotView of(ParkingSlot slot) {
		return new SlotView(slot.getId(), slot.getSlotNumber(), slot.getFloor(), slot.getType(), slot.getStatus());
	}
}
