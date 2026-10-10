package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.feature.user.User;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_reservation_vehicle_state", columnList = "vehicle_id, state"),
        @Index(name = "idx_reservation_range", columnList = "vehicle_id, start_time, end_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {
    private static final Set<ReservationState> CANCELLABLE_STATES =
            EnumSet.of(ReservationState.DRAFT, ReservationState.PENDING_APPROVAL, ReservationState.CONFIRMED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationState state;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime submittedAt;

    @Column
    private LocalDateTime reviewDeadline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Column
    private LocalDateTime approvedAt;

    public void confirm() {
        confirm(LocalDateTime.now());
    }

    public void confirm(LocalDateTime now) {
        requireState(ReservationState.DRAFT);
        assertVehicleActive();
        this.state = ReservationState.CONFIRMED;
        this.approvedAt = now;
    }

    public void requestApproval(LocalDateTime now) {
        requireState(ReservationState.DRAFT);
        assertVehicleActive();
        this.state = ReservationState.PENDING_APPROVAL;
        this.submittedAt = now;
        this.reviewDeadline = resolveReviewDeadline(now);
    }

    public void markPendingApproval() {
        requestApproval(LocalDateTime.now());
    }

    public void markPendingApproval(LocalDateTime now) {
        requestApproval(now);
    }

    public void approve() {
        approve(LocalDateTime.now());
    }

    public void approve(LocalDateTime now) {
        requireState(ReservationState.PENDING_APPROVAL);
        assertVehicleActive();
        this.state = ReservationState.CONFIRMED;
        this.approvedAt = now;
    }

    public void reject() {
        reject(LocalDateTime.now());
    }

    public void reject(LocalDateTime now) {
        requireState(ReservationState.PENDING_APPROVAL);
        this.state = ReservationState.REJECTED;
    }

    public void expire(LocalDateTime now) {
        requireState(ReservationState.PENDING_APPROVAL);
        this.state = ReservationState.EXPIRED;
        this.reviewDeadline = now;
    }

    public void cancel() {
        cancel(LocalDateTime.now());
    }

    public void cancel(LocalDateTime now) {
        if (state == ReservationState.CANCELLED) {
            throw new IllegalStateException("ALREADY_CANCELLED");
        }
        if (!CANCELLABLE_STATES.contains(state)) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }
        if (!now.isBefore(this.startTime)) {
            throw new IllegalStateException("CANCELLATION_WINDOW_CLOSED");
        }
        this.state = ReservationState.CANCELLED;
    }

    public boolean isBlocking() {
        return state == ReservationState.CONFIRMED || state == ReservationState.PENDING_APPROVAL;
    }

    public boolean isCancellable(LocalDateTime now) {
        if (state == ReservationState.CANCELLED) {
            return false;
        }
        if (!CANCELLABLE_STATES.contains(state)) {
            return false;
        }
        return now.isBefore(this.startTime);
    }

    private LocalDateTime resolveReviewDeadline(LocalDateTime now) {
        LocalDateTime slaDeadline = now.plusHours(24);
        LocalDateTime candidate = slaDeadline.isBefore(startTime) ? slaDeadline : startTime;
        return candidate;
    }

    private void requireState(ReservationState expected) {
        if (this.state != expected) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
        }
    }

    private void assertVehicleActive() {
        if (vehicle == null || !vehicle.isActive()) {
            throw new IllegalStateException("RESOURCE_INACTIVE");
        }
    }
}