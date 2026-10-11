package cz.vsb.autarepo.autorepo.feature.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceStub {
    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceStub.class);

    public void notifyReservationConfirmed(Reservation reservation) {
        notify(reservation, "confirmed");
    }

    public void notifyReservationPendingApproval(Reservation reservation) {
        notify(reservation, "pending approval");
    }

    public void notifyReservationRejected(Reservation reservation) {
        notify(reservation, "rejected");
    }

    public void notifyReservationCancelled(Reservation reservation) {
        notify(reservation, "cancelled");
    }

    public void notifyReservationExpired(Reservation reservation) {
        notify(reservation, "expired");
    }

    private void notify(Reservation reservation, String event) {
        logger.info("NOTIFICATION [STUB]: Reservation {} {} for user {} on vehicle {}",
                reservation.getId(),
                event,
                reservation.getUser().getFullName(),
                reservation.getVehicle().getVin());
    }
}
