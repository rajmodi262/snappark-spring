package com.rajmodi.snappark.slot;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Every state change on a bay is a single conditional UPDATE. The database row lock decides the
 * winner and the WHERE clause is re-checked after any wait, so two requests can never both succeed,
 * and no lock state lives in application memory: any number of instances can serve traffic.
 */
public interface SlotRepository extends JpaRepository<ParkingSlot, Long> {

	List<ParkingSlot> findAllByOrderBySlotNumberAsc();

	boolean existsBySlotNumber(String slotNumber);

	long countByStatus(SlotStatus status);

	/** Hold a bay that is free, or whose previous hold has lapsed. Returns rows updated (0 or 1). */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update ParkingSlot s
			   set s.status = com.rajmodi.snappark.slot.SlotStatus.HELD,
			       s.heldBy = :driverId, s.holdExpiresAt = :expiresAt, s.version = s.version + 1
			 where s.id = :id
			   and (s.status = com.rajmodi.snappark.slot.SlotStatus.AVAILABLE
			        or (s.status = com.rajmodi.snappark.slot.SlotStatus.HELD and s.holdExpiresAt < :now))""")
	int tryHold(Long id, String driverId, Instant expiresAt, Instant now);

	/** Release a hold, but only for the driver who owns it. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update ParkingSlot s
			   set s.status = com.rajmodi.snappark.slot.SlotStatus.AVAILABLE,
			       s.heldBy = null, s.holdExpiresAt = null, s.version = s.version + 1
			 where s.id = :id
			   and s.status = com.rajmodi.snappark.slot.SlotStatus.HELD and s.heldBy = :driverId""")
	int releaseHold(Long id, String driverId);

	/** Turn the caller's live hold into an occupied bay. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update ParkingSlot s
			   set s.status = com.rajmodi.snappark.slot.SlotStatus.OCCUPIED,
			       s.heldBy = null, s.holdExpiresAt = null, s.version = s.version + 1
			 where s.id = :id
			   and s.status = com.rajmodi.snappark.slot.SlotStatus.HELD
			   and s.heldBy = :driverId and s.holdExpiresAt >= :now""")
	int occupyHeld(Long id, String driverId, Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update ParkingSlot s
			   set s.status = com.rajmodi.snappark.slot.SlotStatus.AVAILABLE, s.version = s.version + 1
			 where s.id = :id and s.status = com.rajmodi.snappark.slot.SlotStatus.OCCUPIED""")
	int vacate(Long id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update ParkingSlot s
			   set s.status = com.rajmodi.snappark.slot.SlotStatus.AVAILABLE,
			       s.heldBy = null, s.holdExpiresAt = null, s.version = s.version + 1
			 where s.status = com.rajmodi.snappark.slot.SlotStatus.HELD and s.holdExpiresAt < :now""")
	int releaseExpiredHolds(Instant now);
}
