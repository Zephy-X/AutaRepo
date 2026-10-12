package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.feature.user.UserService;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0);

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private VehicleService vehicleService;

    @Mock
    private UserService userService;

    @Mock
    private NotificationServiceStub notificationService;

    private ReservationService reservationService;
    private Vehicle activeVehicle;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        reservationService = new ReservationService(
                reservationRepository,
                vehicleService,
                userService,
                notificationService,
                clock
        );

        activeVehicle = Vehicle.builder()
                .id(7L)
                .isActive(true)
                .requiresApproval(false)
                .build();
    }

    @Test
    void rejectsIntervalWhenStartEqualsEnd() {
        LocalDateTime start = NOW.plusDays(1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> reservationService.isAvailable(7L, start, start))
                .withMessage("INVALID_INTERVAL");

        verify(vehicleService, never()).findById(any());
    }

    @Test
    void rejectsIntervalWhenStartIsInThePast() {
        LocalDateTime start = NOW.minusMinutes(1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> reservationService.isAvailable(
                        7L, start, start.plusHours(1)))
                .withMessage("INTERVAL_IN_PAST");

        verify(vehicleService, never()).findById(any());
    }

    @Test
    void rejectsReservationLongerThanFourteenDays() {
        LocalDateTime start = NOW.plusHours(1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> reservationService.isAvailable(
                        7L, start, start.plusDays(14).plusMinutes(1)))
                .withMessage("MAX_DURATION_EXCEEDED");
    }

    @Test
    void treatsAdjacentConfirmedIntervalAsAvailable() {
        LocalDateTime start = NOW.plusDays(1);
        LocalDateTime end = start.plusHours(2);
        when(vehicleService.findById(7L)).thenReturn(activeVehicle);

        when(reservationRepository
                .existsByVehicleIdAndStateAndStartTimeLessThanAndEndTimeGreaterThan(
                        7L, ReservationState.CONFIRMED, end, start))
                .thenReturn(false);

        boolean available = reservationService.isAvailable(7L, start, end);

        assertThat(available).isTrue();
        verify(reservationRepository).existsByVehicleIdAndStateAndStartTimeLessThanAndEndTimeGreaterThan(
                7L, ReservationState.CONFIRMED, end, start);
    }

    @Test
    void treatsStrictlyInsideConfirmedIntervalAsUnavailable() {
        LocalDateTime start = NOW.plusDays(1);
        LocalDateTime end = start.plusHours(2);
        when(vehicleService.findById(7L)).thenReturn(activeVehicle);

        when(reservationRepository
                .existsByVehicleIdAndStateAndStartTimeLessThanAndEndTimeGreaterThan(
                        7L, ReservationState.CONFIRMED, end, start))
                .thenReturn(true);

        boolean available = reservationService.isAvailable(7L, start, end);

        assertThat(available).isFalse();
    }

    @Test
    void routesApprovalRequiredDraftToPendingApproval() {
        activeVehicle.setRequiresApproval(true);
        Reservation draft = reservation(activeVehicle, ReservationState.DRAFT, NOW.plusDays(1));
        when(reservationRepository.findById(11L)).thenReturn(Optional.of(draft));
        when(reservationRepository.save(draft)).thenReturn(draft);

        Reservation result = reservationService.confirmReservation(11L);

        assertThat(result.getState()).isEqualTo(ReservationState.PENDING_APPROVAL);
        verify(reservationRepository).save(draft);
        verify(reservationRepository, never())
                .existsByVehicleIdAndStateAndStartTimeLessThanAndEndTimeGreaterThan(
                        any(), any(), any(), any());
        verify(notificationService, never()).notifyReservationConfirmed(any());
    }

    @Test
    void expiresPendingApprovalAtStartTime() {
        Reservation pending = reservation(activeVehicle, ReservationState.PENDING_APPROVAL, NOW);
        when(reservationRepository.findById(12L)).thenReturn(Optional.of(pending));
        when(reservationRepository.save(pending)).thenReturn(pending);

        assertThatIllegalStateException()
                .isThrownBy(() -> reservationService.approveReservation(12L))
                .withMessage("APPROVAL_WINDOW_EXPIRED");

        assertThat(pending.getState()).isEqualTo(ReservationState.EXPIRED);
        verify(reservationRepository).save(pending);
        verify(reservationRepository, never())
                .existsByVehicleIdAndStateAndStartTimeLessThanAndEndTimeGreaterThan(
                        any(), any(), any(), any());
    }

    @Test
    void cancelsConfirmedReservationBeforeStartAndNotifies() {
        Reservation confirmed = reservation(activeVehicle, ReservationState.CONFIRMED, NOW.plusHours(2));
        when(reservationRepository.findById(13L)).thenReturn(Optional.of(confirmed));
        when(reservationRepository.save(confirmed)).thenReturn(confirmed);

        Reservation result = reservationService.cancelReservation(13L);

        assertThat(result.getState()).isEqualTo(ReservationState.CANCELLED);
        verify(reservationRepository).save(confirmed);
        verify(notificationService).notifyReservationCancelled(confirmed);
    }

    private Reservation reservation(Vehicle vehicle, ReservationState state, LocalDateTime start) {
        return Reservation.builder()
                .id(1L)
                .vehicle(vehicle)
                .startTime(start)
                .endTime(start.plusHours(2))
                .state(state)
                .createdAt(NOW)
                .build();
    }
}
