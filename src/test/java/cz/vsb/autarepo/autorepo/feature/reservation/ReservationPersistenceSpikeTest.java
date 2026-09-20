package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.feature.user.User;
import cz.vsb.autarepo.autorepo.feature.user.UserRepository;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class ReservationPersistenceSpikeTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void testReservationPersistenceWithRelationships() {
        // 1. Prepare and persist related entities
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

        // 2. Persist target entity
        Reservation reservation = Reservation.builder()
                .vehicle(savedVehicle)
                .user(savedUser)
                .startTime(startTime)
                .endTime(endTime)
                .state(ReservationState.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build();
        Reservation savedReservation = reservationRepository.save(reservation);

        // 3. Force SQL INSERTs to PostgreSQL and clear the first-level session cache
        entityManager.flush();
        entityManager.clear();

        // 4. Force a real SQL SELECT from the database
        Reservation fetchedReservation = reservationRepository.findById(savedReservation.getId())
                .orElseThrow(() -> new AssertionError("Reservation not found in database"));

        // 5. Assert database mapping and relational integrity
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

        assertThat(fetchedReservation.getStartTime()).isEqualToIgnoringNanos(startTime);
        assertThat(fetchedReservation.getEndTime()).isEqualToIgnoringNanos(endTime);
        assertThat(fetchedReservation.getCreatedAt()).isNotNull();
    }
}