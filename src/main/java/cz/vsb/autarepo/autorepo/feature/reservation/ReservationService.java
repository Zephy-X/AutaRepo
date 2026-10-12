package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.common.exception.ApprovalWindowExpiredException;
import cz.vsb.autarepo.autorepo.common.exception.CancellationWindowClosedException;
import cz.vsb.autarepo.autorepo.common.exception.ResourceNotFoundException;
import cz.vsb.autarepo.autorepo.feature.reservation.dto.ReservationForm;
import cz.vsb.autarepo.autorepo.feature.user.User;
import cz.vsb.autarepo.autorepo.feature.user.UserService;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final VehicleService vehicleService;
    private final UserService userService;
    private final NotificationServiceStub notificationService;
    private final Clock clock;

    @Autowired
    public ReservationService(Clock clock,
                              ReservationRepository reservationRepository,
                              VehicleService vehicleService,
                              UserService userService,
                              NotificationServiceStub notificationService) {
        this.clock = clock;
        this.reservationRepository = reservationRepository;
        this.vehicleService = vehicleService;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    public ReservationService(ReservationRepository reservationRepository,
                              VehicleService vehicleService,
                              UserService userService,
                              NotificationServiceStub notificationService,
                              Clock clock) {
        this(clock, reservationRepository, vehicleService, userService, notificationService);
    }

    public List<Reservation> findAll() {
        return reservationRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public List<Reservation> findDrafts() {
        return reservationRepository.findByState(ReservationState.DRAFT,
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public List<Reservation> findPendingApprovals() {
        return reservationRepository.findByState(ReservationState.PENDING_APPROVAL,
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Reservation findById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
    }

    @Transactional
    public Reservation createReservation(ReservationForm form) {
        LocalDateTime now = now();
        LocalDateTime start = form.toStartDateTime();
        LocalDateTime end = form.toEndDateTime();
        validateReservationWindow(start, end, now);

        Vehicle vehicle = vehicleService.findById(form.vehicleId());
        User user = userService.findById(form.userId());

        if (!vehicle.isActive()) {
            throw new IllegalStateException("RESOURCE_INACTIVE");
        }

        return reservationRepository.save(Reservation.builder()
                .vehicle(vehicle)
                .user(user)
                .startTime(start)
                .endTime(end)
                .state(ReservationState.DRAFT)
                .createdAt(now)
                .build());
    }

    @Transactional
    public Reservation confirmReservation(Long id) {
        Reservation reservation = findById(id);
        if (reservation.getState() != ReservationState.DRAFT) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }

        Vehicle vehicle = vehicleService.findByIdForUpdate(reservation.getVehicle().getId());
        reservation.setVehicle(vehicle);
        ensureNoBlockingConflict(reservation.getVehicle().getId(), reservation.getStartTime(), reservation.getEndTime(), reservation.getId());

        LocalDateTime now = now();
        if (vehicle.isRequiresApproval()) {
            reservation.requestApproval(now);
            Reservation saved = reservationRepository.save(reservation);
            reservationRepository.flush();
            notificationService.notifyReservationPendingApproval(saved);
            return saved;
        }

        reservation.confirm(now);
        Reservation saved = reservationRepository.save(reservation);
        reservationRepository.flush();
        notificationService.notifyReservationConfirmed(saved);
        return saved;
    }

    @Transactional
    public Reservation approveReservation(Long id) {
        Reservation reservation = findById(id);
        LocalDateTime now = now();
        if (reservation.getState() != ReservationState.PENDING_APPROVAL) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }

        if (shouldExpireReservation(reservation, now)) {
            reservation.expire(now);
            reservationRepository.save(reservation);
            reservationRepository.flush();
            notificationService.notifyReservationExpired(reservation);
            throw new ApprovalWindowExpiredException("APPROVAL_WINDOW_EXPIRED");
        }

        Vehicle vehicle = vehicleService.findByIdForUpdate(reservation.getVehicle().getId());
        reservation.setVehicle(vehicle);
        ensureNoBlockingConflict(reservation.getVehicle().getId(), reservation.getStartTime(), reservation.getEndTime(), reservation.getId());

        reservation.approve(now);
        Reservation saved = reservationRepository.save(reservation);
        reservationRepository.flush();
        notificationService.notifyReservationConfirmed(saved);
        return saved;
    }

    @Transactional
    public Reservation rejectReservation(Long id) {
        Reservation reservation = findById(id);
        if (reservation.getState() != ReservationState.PENDING_APPROVAL) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }
        reservation.reject();
        Reservation saved = reservationRepository.save(reservation);
        reservationRepository.flush();
        notificationService.notifyReservationRejected(saved);
        return saved;
    }

    @Transactional
    public Reservation cancelReservation(Long id) {
        Reservation reservation = findById(id);
        LocalDateTime now = now();

        if (reservation.getState() == ReservationState.CANCELLED) {
            throw new IllegalStateException("ALREADY_CANCELLED");
        }
        if (!reservation.isCancellable(now)) {
            if (!now.isBefore(reservation.getStartTime())) {
                throw new CancellationWindowClosedException("CANCELLATION_WINDOW_CLOSED");
            }
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }

        reservation.cancel(now);
        Reservation saved = reservationRepository.save(reservation);
        reservationRepository.flush();
        notificationService.notifyReservationCancelled(saved);
        return saved;
    }

    @Transactional
    public List<Reservation> expireStaleReservations() {
        List<Reservation> pending = reservationRepository.findByState(ReservationState.PENDING_APPROVAL);
        LocalDateTime now = now();

        for (Reservation reservation : pending) {
            if (shouldExpireReservation(reservation, now)) {
                reservation.expire(now);
                reservationRepository.save(reservation);
                reservationRepository.flush();
                notificationService.notifyReservationExpired(reservation);
            }
        }
        return pending;
    }

    public boolean isAvailable(Long vehicleId, LocalDateTime start, LocalDateTime end) {
        validateReservationWindow(start, end, now());
        vehicleService.findById(vehicleId);
        return !reservationRepository.hasStateConflict(vehicleId, start, end);
    }

    public String checkAvailabilityStatus(Long vehicleId, LocalDateTime start, LocalDateTime end) {
        return isAvailable(vehicleId, start, end) ? "AVAILABLE" : "UNAVAILABLE";
    }

    private void ensureNoBlockingConflict(Long vehicleId, LocalDateTime start, LocalDateTime end, Long ignoreReservationId) {
        if (reservationRepository.hasStateConflict(vehicleId, start, end, ignoreReservationId)) {
            throw new IllegalArgumentException("CONFLICT_OVERLAP");
        }
    }

    private boolean shouldExpireReservation(Reservation reservation, LocalDateTime now) {
        return reservation.getState() == ReservationState.PENDING_APPROVAL
                && ((!now.isBefore(reservation.getStartTime()))
                || (reservation.getReviewDeadline() != null
                && !now.isBefore(reservation.getReviewDeadline())));
    }

    private void validateReservationWindow(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("INVALID_INTERVAL");
        }
        if (!start.isAfter(now)) {
            throw new IllegalArgumentException("START_MUST_BE_FUTURE");
        }
        if (Duration.between(start, end).compareTo(Duration.ofDays(14)) > 0) {
            throw new IllegalArgumentException("MAX_DURATION_EXCEEDED");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}