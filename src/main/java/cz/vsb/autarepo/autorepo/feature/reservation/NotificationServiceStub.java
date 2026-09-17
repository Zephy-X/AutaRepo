package cz.vsb.autarepo.autorepo.feature.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceStub {
    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceStub.class);

    public void notifyReservationConfirmed(Reservation reservation) {
        logger.info("NOTIFICATION [STUB]: Reservation {} confirmed for user {} on vehicle {}",
                reservation.getId(),
                reservation.getUser().getFullName(),
                reservation.getVehicle().getVin());
    }

    public void notifyReservationCancelled(Reservation reservation) {
        logger.info("NOTIFICATION [STUB]: Reservation {} cancelled for user {} on vehicle {}",
                reservation.getId(),
                reservation.getUser().getFullName(),
                reservation.getVehicle().getVin());
    }
}
