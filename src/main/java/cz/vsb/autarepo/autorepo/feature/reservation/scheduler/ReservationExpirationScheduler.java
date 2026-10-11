package cz.vsb.autarepo.autorepo.feature.reservation.scheduler;

import cz.vsb.autarepo.autorepo.feature.reservation.ReservationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpirationScheduler {
    private final ReservationService reservationService;

    public ReservationExpirationScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedRateString = "${autorepo.expiry-interval:60000}")
    public void expirePendingReservations() {
        System.out.println(">>> Scheduler fired at: " + java.time.LocalDateTime.now());
        reservationService.expireStaleReservations();
    }
}
