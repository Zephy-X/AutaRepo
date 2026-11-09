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
<<<<<<< Updated upstream
    private final NotificationServiceStub notificationService;
    private final Clock clock;
    private final ReservationRules reservationRules;
=======
    private final ReservationNotifier reservationNotifier;
>>>>>>> Stashed changes

    @Autowired
    public ReservationService(Clock clock,
                              ReservationRepository reservationRepository,
                              VehicleService vehicleService,
                              UserService userService,
<<<<<<< Updated upstream
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
=======
                              ReservationNotifier notificationService) {
        this.reservationRepository = reservationRepository;
        this.vehicleService = vehicleService;
        this.userService = userService;
        this.reservationNotifier = notificationService;
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
        if (!now.isBefore(reservation.getStartTime())) {
            throw new IllegalStateException("START_MUST_BE_FUTURE");
        }

        ensureNoBlockingConflict(reservation.getVehicle().getId(), reservation.getStartTime(), reservation.getEndTime(), reservation.getId());
=======

        if (reservation.getVehicle().isRequiresApproval()) {
            reservation.requestApproval();
            return reservationRepository.save(reservation);
        }

        boolean hasConflict = reservationRepository
                .existsByVehicleIdAndStateAndStartTimeLessThanAndEndTimeGreaterThan(
                        reservation.getVehicle().getId(),
                        ReservationState.CONFIRMED,
                        reservation.getEndTime(),
                        reservation.getStartTime()
                );
>>>>>>> Stashed changes

        if (vehicle.isRequiresApproval()) {
            reservation.requestApproval(now);
            Reservation saved = reservationRepository.save(reservation);
            reservationRepository.flush();
            notificationService.notifyReservationPendingApproval(saved);
            return saved;
        }

<<<<<<< Updated upstream
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
=======
        reservation.confirm();
        Reservation confirmed = reservationRepository.save(reservation);
        reservationNotifier.sendConfirmation(confirmed);
        return confirmed;
    }

    public Reservation approveReservation(Long reservationId) {
        Reservation reservation = findById(reservationId);

>>>>>>> Stashed changes
        if (reservation.getState() != ReservationState.PENDING_APPROVAL) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }

<<<<<<< Updated upstream
        if (reservationRules.shouldExpire(reservation.getState(), now, reservation.getReviewDeadline(), reservation.getStartTime())) {
            reservation.expire(now);
=======
        if (!reservation.getVehicle().isActive()) {
            throw new IllegalStateException("RESOURCE_INACTIVE");
        }

        if (!LocalDateTime.now().isBefore(reservation.getStartTime())) {
            reservation.expire();
>>>>>>> Stashed changes
            reservationRepository.save(reservation);
            reservationRepository.flush();
            notificationService.notifyReservationExpired(reservation);
            throw new ApprovalWindowExpiredException("APPROVAL_WINDOW_EXPIRED");
        }

<<<<<<< Updated upstream
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
=======
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

        reservation.confirm();
        Reservation confirmed = reservationRepository.save(reservation);
        reservationNotifier.sendConfirmation(confirmed);
        return confirmed;
    }

    public Reservation rejectReservation(Long reservationId) {
        Reservation reservation = findById(reservationId);

        if (reservation.getState() != ReservationState.PENDING_APPROVAL) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }

        reservation.reject();
        return reservationRepository.save(reservation);
>>>>>>> Stashed changes
    }

    @Transactional
    public Reservation cancelReservation(Long id) {
        Reservation reservation = findById(id);
        LocalDateTime now = now();

        if (reservation.getState() == ReservationState.CANCELLED) {
            throw new IllegalStateException("ALREADY_CANCELLED");
        }
<<<<<<< Updated upstream
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
=======

        if (reservation.getState() != ReservationState.DRAFT
                && reservation.getState() != ReservationState.PENDING_APPROVAL
                && reservation.getState() != ReservationState.CONFIRMED) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }

        if (!LocalDateTime.now().isBefore(reservation.getStartTime())) {
            throw new IllegalStateException("CANCELLATION_WINDOW_CLOSED");
        }

        reservation.cancel();
        return reservationRepository.save(reservation);
>>>>>>> Stashed changes
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