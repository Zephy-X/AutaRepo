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
    public List<Reservation> findDrafts() {
        return reservationRepository.findByState(ReservationState.DRAFT);
    }

    @Transactional(readOnly = true)
    public Reservation findById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
    }

    public Reservation createReservation(ReservationForm form) {
        LocalDateTime startTime = form.toStartDateTime();
        LocalDateTime endTime = form.toEndDateTime();

        validateReservationWindow(startTime, endTime);

        Vehicle vehicle = vehicleService.findById(form.vehicleId());
        User user = userService.findById(form.userId());

        Reservation reservation = Reservation.builder()
                .vehicle(vehicle)
                .user(user)
                .startTime(startTime)
                .endTime(endTime)
                .state(ReservationState.DRAFT)
                .createdAt(LocalDateTime.now())
                .build();

        return reservationRepository.save(reservation);
    }

    public Reservation confirmReservation(Long reservationId) {
        Reservation reservation = findById(reservationId);

        if (reservation.getState() != ReservationState.DRAFT) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }

        if (!reservation.getVehicle().isActive()) {
            throw new IllegalStateException("RESOURCE_INACTIVE");
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
            throw new IllegalStateException("CONFLICT_OVERLAP");
        }

        reservation.setState(ReservationState.CONFIRMED);
        Reservation confirmed = reservationRepository.save(reservation);
        notificationService.notifyReservationConfirmed(confirmed);
        return confirmed;
    }

    public Reservation cancelReservation(Long reservationId) {
        Reservation reservation = findById(reservationId);

        if (reservation.getState() == ReservationState.CANCELLED) {
            throw new IllegalStateException("ALREADY_CANCELLED");
        }

        if (reservation.getState() != ReservationState.DRAFT
                && reservation.getState() != ReservationState.CONFIRMED) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }

        if (!LocalDateTime.now().isBefore(reservation.getStartTime())) {
            throw new IllegalStateException("CANCELLATION_WINDOW_CLOSED");
        }

        reservation.setState(ReservationState.CANCELLED);
        Reservation cancelled = reservationRepository.save(reservation);
        notificationService.notifyReservationCancelled(cancelled);
        return cancelled;
    }

    private void validateReservationWindow(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("INVALID_INTERVAL");
        }

        LocalDateTime now = LocalDateTime.now();

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("INVALID_INTERVAL");
        }

        if (!startTime.isAfter(now)) {
            throw new IllegalArgumentException("INTERVAL_IN_PAST");
        }
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime) {
        validateReservationWindow(startTime, endTime);
        vehicleService.findById(vehicleId);

        return !reservationRepository.existsByVehicleIdAndStateAndStartTimeLessThanAndEndTimeGreaterThan(
                vehicleId,
                ReservationState.CONFIRMED,
                endTime,
                startTime
        );
    }

    @Transactional(readOnly = true)
    public String checkAvailabilityStatus(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime) {
        return isAvailable(vehicleId, startTime, endTime) ? "AVAILABLE" : "UNAVAILABLE";
    }

    public String resolveErrorMessage(String errorCode) {
        return switch (errorCode) {
            case "CONFLICT_OVERLAP" -> "Vehicle conflict: another reservation was confirmed for this time. Booking remains DRAFT.";
            case "CANCELLATION_WINDOW_CLOSED" -> "Cancellation window closed: reservation has already started.";
            case "ALREADY_CANCELLED" -> "Reservation is already cancelled.";
            case "RESOURCE_INACTIVE" -> "Vehicle is inactive and cannot be confirmed.";
            case "INVALID_STATE_TRANSITION" -> "Invalid state transition for this operation.";
            case "INVALID_INTERVAL" -> "Invalid interval. Start time must be before end time.";
            case "INTERVAL_IN_PAST" -> "Interval start must be in the future.";
            default -> errorCode;
        };
    }

    @Transactional(readOnly = true)
    public boolean isCancellationAllowed(Reservation reservation) {
        if (reservation.getState() == ReservationState.CANCELLED) {
            return false;
        }
        return LocalDateTime.now().isBefore(reservation.getStartTime());
    }
}