package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.common.exception.ResourceNotFoundException;
import cz.vsb.autarepo.autorepo.feature.reservation.dto.ReservationForm;
import cz.vsb.autarepo.autorepo.feature.user.User;
import cz.vsb.autarepo.autorepo.feature.user.UserService;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ReservationService {
    private static final Duration MAX_RESERVATION_DURATION = Duration.ofDays(14);

    private final ReservationRepository reservationRepository;
    private final VehicleService vehicleService;
    private final UserService userService;
    private final NotificationServiceStub notificationService;
    private final Clock clock;

    public ReservationService(ReservationRepository reservationRepository,
                              VehicleService vehicleService,
                              UserService userService,
                              NotificationServiceStub notificationService,
                              Clock clock) {
        this.reservationRepository = reservationRepository;
        this.vehicleService = vehicleService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.clock = clock;
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
    public List<Reservation> findPendingApprovals() {
        return reservationRepository.findByState(ReservationState.PENDING_APPROVAL);
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
                .createdAt(now())
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

        // BR-05: Luxury or restricted vehicles route to PENDING_APPROVAL
        if (reservation.getVehicle().isRequiresApproval()) {
            reservation.setState(ReservationState.PENDING_APPROVAL);
            return reservationRepository.save(reservation);
        }

        // Standard vehicles check conflict and transition to CONFIRMED
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

    // OP-05: Approve Reservation
    public Reservation approveReservation(Long reservationId) {
        Reservation reservation = findById(reservationId);

        if (reservation.getState() != ReservationState.PENDING_APPROVAL) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }

        if (!reservation.getVehicle().isActive()) {
            throw new IllegalStateException("RESOURCE_INACTIVE");
        }

        // BR-06: Expiration window check
        if (!now().isBefore(reservation.getStartTime())) {
            reservation.setState(ReservationState.EXPIRED);
            reservationRepository.save(reservation);
            throw new IllegalStateException("APPROVAL_WINDOW_EXPIRED");
        }

        // Conflict check against any other confirmed reservations
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

    // OP-06: Reject Reservation
    public Reservation rejectReservation(Long reservationId) {
        Reservation reservation = findById(reservationId);

        if (reservation.getState() != ReservationState.PENDING_APPROVAL) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }

        reservation.setState(ReservationState.REJECTED);
        return reservationRepository.save(reservation);
    }

    public Reservation cancelReservation(Long reservationId) {
        Reservation reservation = findById(reservationId);

        if (reservation.getState() == ReservationState.CANCELLED) {
            throw new IllegalStateException("ALREADY_CANCELLED");
        }

        // Allow cancellation from DRAFT, PENDING_APPROVAL, and CONFIRMED
        if (reservation.getState() != ReservationState.DRAFT
                && reservation.getState() != ReservationState.PENDING_APPROVAL
                && reservation.getState() != ReservationState.CONFIRMED) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }

        if (!now().isBefore(reservation.getStartTime())) {
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

        LocalDateTime now = now();

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("INVALID_INTERVAL");
        }

        if (!startTime.isAfter(now)) {
            throw new IllegalArgumentException("INTERVAL_IN_PAST");
        }

        if (Duration.between(startTime, endTime).compareTo(MAX_RESERVATION_DURATION) > 0) {
            throw new IllegalArgumentException("MAX_DURATION_EXCEEDED");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
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
            case "CONFLICT_OVERLAP" -> "Vehicle conflict: another reservation was confirmed for this time slot.";
            case "APPROVAL_WINDOW_EXPIRED" -> "Approval window expired: reservation start time has already passed.";
            case "CANCELLATION_WINDOW_CLOSED" -> "Cancellation window closed: reservation has already started.";
            case "ALREADY_CANCELLED" -> "Reservation is already cancelled.";
            case "RESOURCE_INACTIVE" -> "Vehicle is inactive and cannot be reserved.";
            case "INVALID_STATE_TRANSITION" -> "Invalid state transition for this operation.";
            case "INVALID_INTERVAL" -> "Invalid interval. Start time must be before end time.";
            case "INTERVAL_IN_PAST" -> "Interval start must be in the future.";
            case "MAX_DURATION_EXCEEDED" -> "Reservation duration cannot exceed 14 days.";
            default -> errorCode;
        };
    }
}