package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.feature.reservation.dto.ReservationForm;
import cz.vsb.autarepo.autorepo.feature.user.User;
import cz.vsb.autarepo.autorepo.feature.user.UserService;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private VehicleService vehicleService;
    @Mock
    private UserService userService;
    @Mock
    private NotificationServiceStub notificationService;

    private ReservationService service;
    private Vehicle vehicle;
    private User user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new ReservationService(clock, reservationRepository, vehicleService, userService, notificationService);
        vehicle = Vehicle.builder().id(1L).isActive(true).requiresApproval(false).build();
        user = User.builder().id(2L).fullName("Test User").build();

        lenient().when(vehicleService.findById(1L)).thenReturn(vehicle);
        lenient().when(vehicleService.findByIdForUpdate(1L)).thenReturn(vehicle);
        lenient().when(userService.findById(2L)).thenReturn(user);
        lenient().when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsZeroLengthInterval() {
        ReservationForm form = form(NOW.plusDays(1), NOW.plusDays(1));

        assertThatThrownBy(() -> service.createReservation(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_INTERVAL");
    }

    @Test
    void rejectsPastStart() {
        ReservationForm form = form(NOW.minusMinutes(1), NOW.plusHours(1));

        assertThatThrownBy(() -> service.createReservation(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("START_MUST_BE_FUTURE");
    }

    @Test
    void rejectsDurationLongerThanFourteenDays() {
        ReservationForm form = form(NOW.plusDays(1), NOW.plusDays(15).plusHours(1));

        assertThatThrownBy(() -> service.createReservation(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("MAX_DURATION_EXCEEDED");
    }

    @Test
    void acceptsDurationOfExactlyFourteenDays() {
        ReservationForm form = form(NOW.plusDays(1), NOW.plusDays(15));

        Reservation reservation = service.createReservation(form);

        assertThat(reservation.getEndTime().minusDays(14)).isEqualTo(reservation.getStartTime());
        assertThat(reservation.getState()).isEqualTo(ReservationState.DRAFT);
    }

    @Test
    void adjacentIntervalsRemainAvailable() {
        LocalDateTime start = NOW.plusDays(1);
        LocalDateTime end = start.plusHours(2);
        when(reservationRepository.hasStateConflict(1L, end, end.plusHours(2))).thenReturn(false);

        assertThat(service.isAvailable(1L, end, end.plusHours(2))).isTrue();
    }

    @Test
    void overlappingConfirmedIntervalRaisesConflict() {
        LocalDateTime start = NOW.plusDays(1);
        Reservation draft = reservation(start, start.plusHours(2), ReservationState.DRAFT);
        when(reservationRepository.findById(3L)).thenReturn(Optional.of(draft));
        when(reservationRepository.hasStateConflict(1L, start, start.plusHours(2), 3L)).thenReturn(true);

        assertThatThrownBy(() -> service.confirmReservation(3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CONFLICT_OVERLAP");
    }

    @Test
    void routesApprovalRequiredVehicleToPendingApproval() {
        vehicle.setRequiresApproval(true);
        LocalDateTime start = NOW.plusDays(1);
        Reservation draft = reservation(start, start.plusHours(2), ReservationState.DRAFT);
        when(reservationRepository.findById(3L)).thenReturn(Optional.of(draft));
        when(reservationRepository.hasStateConflict(1L, start, start.plusHours(2), 3L)).thenReturn(false);

        Reservation result = service.confirmReservation(3L);

        assertThat(result.getState()).isEqualTo(ReservationState.PENDING_APPROVAL);
        verify(notificationService).notifyReservationPendingApproval(result);
        verify(notificationService, never()).notifyReservationConfirmed(any());
    }

    @Test
    void approvalExpiresAtStartTime() {
        LocalDateTime start = NOW;
        Reservation pending = reservation(start, start.plusHours(2), ReservationState.PENDING_APPROVAL);
        pending.setReviewDeadline(start.plusDays(1));
        when(reservationRepository.findById(3L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approveReservation(3L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("APPROVAL_WINDOW_EXPIRED");
        assertThat(pending.getState()).isEqualTo(ReservationState.EXPIRED);
        verify(notificationService).notifyReservationExpired(pending);
    }

    @Test
    void cancelsBeforeStartAndNotifies() {
        Reservation confirmed = reservation(NOW.plusDays(1), NOW.plusDays(1).plusHours(2), ReservationState.CONFIRMED);
        when(reservationRepository.findById(3L)).thenReturn(Optional.of(confirmed));

        Reservation result = service.cancelReservation(3L);

        assertThat(result.getState()).isEqualTo(ReservationState.CANCELLED);
        verify(notificationService).notifyReservationCancelled(result);
    }

    @Test
    void rejectsPendingReservationAndNotifies() {
        Reservation pending = reservation(NOW.plusDays(1), NOW.plusDays(1).plusHours(2), ReservationState.PENDING_APPROVAL);
        when(reservationRepository.findById(3L)).thenReturn(Optional.of(pending));

        Reservation result = service.rejectReservation(3L);

        assertThat(result.getState()).isEqualTo(ReservationState.REJECTED);
        verify(notificationService).notifyReservationRejected(result);
    }

    private Reservation reservation(LocalDateTime start, LocalDateTime end, ReservationState state) {
        return Reservation.builder()
                .id(3L)
                .vehicle(vehicle)
                .user(user)
                .startTime(start)
                .endTime(end)
                .state(state)
                .createdAt(NOW)
                .build();
    }

    private ReservationForm form(LocalDateTime start, LocalDateTime end) {
        return new ReservationForm(
                1L,
                2L,
                start.toLocalDate(),
                start.getHour(),
                end.toLocalDate(),
                end.getHour());
    }
}
