package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.common.exception.ResourceNotFoundException;
import cz.vsb.autarepo.autorepo.feature.reservation.dto.ReservationForm;
import cz.vsb.autarepo.autorepo.feature.user.User;
import cz.vsb.autarepo.autorepo.feature.user.UserService;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final VehicleService vehicleService;
    private final UserService userService;
    private final NotificationServiceStub notificationService;

    private static final long ADVANCE_HOURS = 2;
    private static final long MAX_DURATION_DAYS = 14;

    public ReservationService(ReservationRepository reservationRepository,
                             VehicleService vehicleService,
                             UserService userService,
                             NotificationServiceStub notificationService) {
        this.reservationRepository = reservationRepository;
        this.vehicleService = vehicleService;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<Reservation> findAll() {
        return reservationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Reservation findById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
    }

    public Reservation createReservation(ReservationForm form) {
        validateReservationWindow(form.startTime(), form.endTime());

        Vehicle vehicle = vehicleService.findById(form.vehicleId());
        User user = userService.findById(form.userId());

        Reservation reservation = Reservation.builder()
                .vehicle(vehicle)
                .user(user)
                .startTime(form.startTime())
                .endTime(form.endTime())
                .state(ReservationState.DRAFT)
                .createdAt(LocalDateTime.now())
                .build();

        return reservationRepository.save(reservation);
    }

    public Reservation confirmReservation(Long reservationId) {
        Reservation reservation = findById(reservationId);

        if (reservation.getState() != ReservationState.DRAFT) {
            throw new IllegalStateException("Only DRAFT reservations can be confirmed");
        }

        // Check for overlaps with confirmed reservations
        boolean hasConflict = reservationRepository
                .existsByVehicleIdAndStateAndStartTimeLessThanAndEndTimeGreaterThan(
                        reservation.getVehicle().getId(),
                        ReservationState.CONFIRMED,
                        reservation.getEndTime(),
                        reservation.getStartTime()
                );

        if (hasConflict) {
            throw new IllegalStateException("Vehicle is already reserved during this period");
        }

        reservation.setState(ReservationState.CONFIRMED);
        Reservation confirmed = reservationRepository.save(reservation);
        notificationService.notifyReservationConfirmed(confirmed);
        return confirmed;
    }

    public Reservation cancelReservation(Long reservationId) {
        Reservation reservation = findById(reservationId);

        if (reservation.getState() == ReservationState.CANCELLED) {
            throw new IllegalStateException("Reservation is already cancelled");
        }

        reservation.setState(ReservationState.CANCELLED);
        Reservation cancelled = reservationRepository.save(reservation);
        notificationService.notifyReservationCancelled(cancelled);
        return cancelled;
    }

    private void validateReservationWindow(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();

        // Advance window check
        if (startTime.isBefore(now.plusHours(ADVANCE_HOURS))) {
            throw new IllegalArgumentException(
                    "Reservation must be created at least " + ADVANCE_HOURS + " hours in advance");
        }

        // Max duration check
        long durationDays = java.time.temporal.ChronoUnit.DAYS.between(startTime, endTime);
        if (durationDays > MAX_DURATION_DAYS) {
            throw new IllegalArgumentException(
                    "Reservation duration cannot exceed " + MAX_DURATION_DAYS + " days");
        }

        // Logical order check
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
    }
}
