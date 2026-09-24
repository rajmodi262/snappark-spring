package com.rajmodi.snappark.session;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessionRepository extends JpaRepository<ParkingSession, Long> {

	@Query("select p from ParkingSession p join fetch p.slot where p.id = :id")
	Optional<ParkingSession> findWithSlot(Long id);

	long countByStatus(SessionStatus status);

	/** Close an active session. A second, concurrent checkout of the same session updates 0 rows. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update ParkingSession p
			   set p.status = com.rajmodi.snappark.session.SessionStatus.CLOSED,
			       p.exitTime = :exitTime, p.amount = :amount
			 where p.id = :id and p.status = com.rajmodi.snappark.session.SessionStatus.ACTIVE""")
	int close(Long id, Instant exitTime, BigDecimal amount);

	@Query("select coalesce(sum(p.amount), 0) from ParkingSession p "
			+ "where p.status = com.rajmodi.snappark.session.SessionStatus.CLOSED")
	BigDecimal totalRevenue();
}
