package cz.vsb.autarepo.autorepo.feature.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByState(ReservationState state);

    @Query("""
        SELECT r FROM Reservation r
        WHERE r.state IN :states
    """)
    List<Reservation> findByStateIn(@Param("states") Set<ReservationState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT COUNT(r) > 0 FROM Reservation r
        WHERE r.vehicle.id = :vehicleId
          AND r.state IN :states
          AND r.startTime < :endTime
          AND r.endTime > :startTime
    """)
    boolean hasBlockingOverlap(
            @Param("vehicleId") Long vehicleId,
            @Param("states") Set<ReservationState> states,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("""
        SELECT COUNT(r) > 0 FROM Reservation r
        WHERE r.vehicle.id = :vehicleId
          AND r.state IN ('CONFIRMED', 'PENDING_APPROVAL')
          AND (:ignoreReservationId IS NULL OR r.id <> :ignoreReservationId)
          AND r.startTime < :endTime
          AND r.endTime > :startTime
    """)
    boolean hasStateConflict(
            @Param("vehicleId") Long vehicleId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("ignoreReservationId") Long ignoreReservationId
    );

    default boolean hasStateConflict(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime) {
        return hasStateConflict(vehicleId, startTime, endTime, null);
    }
}