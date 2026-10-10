package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.feature.user.User;
import cz.vsb.autarepo.autorepo.feature.user.UserRepository;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationLifecycleIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void luxuryReservationInPendingApprovalBlocksAvailability() {
        User user = userRepository.save(User.builder()
                .fullName("Luxury User")
                .email("luxury@example.com")
                .driverLicenseNumber("LU-777")
                .build());

        Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                .brand("Mercedes")
                .model("S-Class")
                .year(2025)
                .vin("VIN-LUX-001")
                .licensePlate("AP-12345")
                .isActive(true)
                .requiresApproval(true)
                .build());

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(4);

        Reservation draft = Reservation.builder()
                .vehicle(vehicle)
                .user(user)
                .startTime(start)
                .endTime(end)
                .state(ReservationState.DRAFT)
                .createdAt(LocalDateTime.now())
                .build();
        reservationRepository.save(draft);

        Reservation submitted = reservationService.confirmReservation(draft.getId());
        assertThat(submitted.getState()).isEqualTo(ReservationState.PENDING_APPROVAL);
        assertThat(reservationService.isAvailable(vehicle.getId(), start, end)).isFalse();

        Reservation another = Reservation.builder()
                .vehicle(vehicle)
                .user(user)
                .startTime(start.plusHours(1))
                .endTime(end.plusHours(1))
                .state(ReservationState.DRAFT)
                .createdAt(LocalDateTime.now())
                .build();
        reservationRepository.save(another);

        assertThatThrownBy(() -> reservationService.confirmReservation(another.getId()))
                .isInstanceOf(Exception.class);
    }

    @Test
    void stalePendingApprovalExpires() {
        User user = userRepository.save(User.builder()
                .fullName("Expiring User")
                .email("expire@example.com")
                .driverLicenseNumber("EX-111")
                .build());

        Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                .brand("BMW")
                .model("5 Series")
                .year(2024)
                .vin("VIN-EXP-002")
                .licensePlate("EX-777")
                .isActive(true)
                .requiresApproval(true)
                .build());

        LocalDateTime nowUtc = LocalDateTime.now(Clock.systemUTC());
        LocalDateTime start = nowUtc.plusDays(3);
        Reservation stale = Reservation.builder()
                .vehicle(vehicle)
                .user(user)
                .startTime(start)
                .endTime(start.plusHours(3))
                .state(ReservationState.PENDING_APPROVAL)
                .submittedAt(nowUtc.minusDays(2))
                .reviewDeadline(nowUtc.minusMinutes(5))
                .createdAt(nowUtc)
                .build();
        reservationRepository.save(stale);

        reservationService.expireStaleReservations();

        Reservation refreshed = reservationRepository.findById(stale.getId()).orElseThrow();
        assertThat(refreshed.getState()).isEqualTo(ReservationState.EXPIRED);
    }
}
