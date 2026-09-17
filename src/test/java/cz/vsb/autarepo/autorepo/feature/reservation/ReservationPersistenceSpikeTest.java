package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.feature.user.User;
import cz.vsb.autarepo.autorepo.feature.user.UserRepository;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
public class ReservationPersistenceSpikeTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void testReservationPersistenceWithRelationships() {
        User user = User.builder()
                .fullName("John Doe")
                .email("john.doe@example.com")
                .driverLicenseNumber("DL123456789")
                .build();
        User savedUser = userRepository.save(user);

        Vehicle vehicle = Vehicle.builder()
                .brand("Toyota")
                .model("Corolla")
                .year(2024)
                .vin("12345678901234567")
                .licensePlate("ABC-1234")
                .isAvailable(true)
                .build();
        Vehicle savedVehicle = vehicleRepository.save(vehicle);

        LocalDateTime startTime = LocalDateTime.now().plusHours(3);
        LocalDateTime endTime = startTime.plusDays(2);

        Reservation reservation = Reservation.builder()
                .vehicle(savedVehicle)
                .user(savedUser)
                .startTime(startTime)
                .endTime(endTime)
                .state(ReservationState.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build();
        Reservation savedReservation = reservationRepository.save(reservation);

        Reservation fetchedReservation = reservationRepository.findById(savedReservation.getId())
                .orElseThrow(() -> new AssertionError("Reservation not found"));

        assertThat(fetchedReservation.getId()).isNotNull();
        assertThat(fetchedReservation.getState()).isEqualTo(ReservationState.CONFIRMED);

        assertThat(fetchedReservation.getUser())
                .isNotNull()
                .extracting(User::getFullName)
                .isEqualTo("John Doe");

        assertThat(fetchedReservation.getVehicle())
                .isNotNull()
                .extracting(Vehicle::getVin)
                .isEqualTo("12345678901234567");

        assertThat(fetchedReservation.getStartTime()).isEqualTo(startTime);
        assertThat(fetchedReservation.getEndTime()).isEqualTo(endTime);
        assertThat(fetchedReservation.getCreatedAt()).isNotNull();
    }
}
