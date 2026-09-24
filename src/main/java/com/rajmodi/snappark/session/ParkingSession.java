package com.rajmodi.snappark.session;

import java.math.BigDecimal;
import java.time.Instant;

import com.rajmodi.snappark.slot.ParkingSlot;
import com.rajmodi.snappark.slot.VehicleType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "parking_session")
public class ParkingSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "slot_id", nullable = false)
	private ParkingSlot slot;

	@Column(name = "driver_id", nullable = false, length = 64)
	private String driverId;

	@Column(name = "vehicle_number", nullable = false, length = 16)
	private String vehicleNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "vehicle_type", nullable = false, length = 8)
	private VehicleType vehicleType;

	@Column(name = "entry_time", nullable = false)
	private Instant entryTime;

	@Column(name = "exit_time")
	private Instant exitTime;

	/** Fixed at check-in, so the driver pays the rate they were quoted. */
	@Column(name = "rate_per_hour", nullable = false, precision = 10, scale = 2)
	private BigDecimal ratePerHour;

	@Column(precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private SessionStatus status;

	protected ParkingSession() {
	}

	public ParkingSession(ParkingSlot slot, String driverId, String vehicleNumber, VehicleType vehicleType,
			Instant entryTime, BigDecimal ratePerHour) {
		this.slot = slot;
		this.driverId = driverId;
		this.vehicleNumber = vehicleNumber;
		this.vehicleType = vehicleType;
		this.entryTime = entryTime;
		this.ratePerHour = ratePerHour;
		this.status = SessionStatus.ACTIVE;
	}

	public Long getId() {
		return id;
	}

	public ParkingSlot getSlot() {
		return slot;
	}

	public String getDriverId() {
		return driverId;
	}

	public String getVehicleNumber() {
		return vehicleNumber;
	}

	public VehicleType getVehicleType() {
		return vehicleType;
	}

	public Instant getEntryTime() {
		return entryTime;
	}

	public Instant getExitTime() {
		return exitTime;
	}

	public BigDecimal getRatePerHour() {
		return ratePerHour;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public SessionStatus getStatus() {
		return status;
	}
}
