package cz.vsb.autarepo.autorepo.feature.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    boolean existsByVehicleIdAndStateAndStartTimeLessThanAndEndTimeGreaterThan(
        Long vehicleId,
        ReservationState state,
        LocalDateTime requestedEnd,
        LocalDateTime requestedStart
    );
}
