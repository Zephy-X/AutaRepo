package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.common.domain.ReservationRules;
import cz.vsb.autarepo.autorepo.common.exception.ApprovalWindowExpiredException;
import cz.vsb.autarepo.autorepo.common.exception.CancellationWindowClosedException;
import cz.vsb.autarepo.autorepo.common.exception.ConflictOverlapException;
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
    private final ReservationRules reservationRules;

    @Autowired
    public ReservationService(Clock clock,
                              ReservationRepository reservationRepository,
                              VehicleService vehicleService,
                              UserService userService,
                              NotificationServiceStub notificationService) {
        this(clock, reservationRepository, vehicleService, userService, notificationService, new ReservationRules());
    }

    public ReservationService(Clock clock,
                              ReservationRepository reservationRepository,
                              VehicleService vehicleService,
                              UserService userService,
                              NotificationServiceStub notificationService,
                              ReservationRules reservationRules) {
        this.clock = clock;
        this.reservationRepository = reservationRepository;
        this.vehicleService = vehicleService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.reservationRules = reservationRules;
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
        reservationRules.validateReservationWindow(start, end, now);

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

        LocalDateTime now = now();
        if (!vehicle.isActive()) {
            throw new IllegalStateException("RESOURCE_INACTIVE");
        }
        if (!now.isBefore(reservation.getStartTime())) {
            throw new IllegalStateException("START_MUST_BE_FUTURE");
        }

        ensureNoBlockingConflict(reservation.getVehicle().getId(), reservation.getStartTime(), reservation.getEndTime(), reservation.getId());

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

        if (reservationRules.shouldExpire(reservation.getState(), now, reservation.getReviewDeadline(), reservation.getStartTime())) {
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
        if (!reservationRules.canCancel(reservation.getState(), now, reservation.getStartTime())) {
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
            if (reservationRules.shouldExpire(reservation.getState(), now, reservation.getReviewDeadline(), reservation.getStartTime())) {
                reservation.expire(now);
                reservationRepository.save(reservation);
                reservationRepository.flush();
                notificationService.notifyReservationExpired(reservation);
            }
        }
        return pending;
    }

    public boolean isAvailable(Long vehicleId, LocalDateTime start, LocalDateTime end) {
        reservationRules.validateReservationWindow(start, end, now());
        Vehicle vehicle = vehicleService.findById(vehicleId);
        if (!vehicle.isActive()) {
            throw new IllegalStateException("RESOURCE_INACTIVE");
        }
        return !reservationRepository.hasStateConflict(vehicleId, start, end);
    }

    public String checkAvailabilityStatus(Long vehicleId, LocalDateTime start, LocalDateTime end) {
        return isAvailable(vehicleId, start, end) ? "AVAILABLE" : "UNAVAILABLE";
    }

    private void ensureNoBlockingConflict(Long vehicleId, LocalDateTime start, LocalDateTime end, Long ignoreReservationId) {
        if (reservationRepository.hasStateConflict(vehicleId, start, end, ignoreReservationId)) {
            throw new ConflictOverlapException("CONFLICT_OVERLAP");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}