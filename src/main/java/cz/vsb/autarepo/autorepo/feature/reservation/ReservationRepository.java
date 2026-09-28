package cz.vsb.autarepo.autorepo.feature.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByState(ReservationState state);

    boolean existsByVehicleIdAndStateAndStartTimeLessThanAndEndTimeGreaterThan(
            Long vehicleId,
            ReservationState state,
            LocalDateTime endTime,
            LocalDateTime startTime
    );
}