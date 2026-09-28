package cz.vsb.autarepo.autorepo.feature.data;

import cz.vsb.autarepo.autorepo.feature.reservation.Reservation;
import cz.vsb.autarepo.autorepo.feature.reservation.ReservationRepository;
import cz.vsb.autarepo.autorepo.feature.reservation.ReservationState;
import cz.vsb.autarepo.autorepo.feature.user.User;
import cz.vsb.autarepo.autorepo.feature.user.UserRepository;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile({"local", "dev"})
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;

    public DataInitializer(UserRepository userRepository,
                           VehicleRepository vehicleRepository,
                           ReservationRepository reservationRepository) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            log.info("DataInitializer: users already present, skipping seeding.");
            return;
        }

        log.info("DataInitializer: database empty — inserting seed data.");

        // Create sample users (drivers)
        User john = User.builder()
                .fullName("John Doe")
                .email("john.doe@example.com")
                .driverLicenseNumber("D12345678")
                .build();

        User jane = User.builder()
                .fullName("Jane Smith")
                .email("jane.smith@example.com")
                .driverLicenseNumber("S87654321")
                .build();

        User alex = User.builder()
                .fullName("Alex Novak")
                .email("alex.novak@example.com")
                .driverLicenseNumber("N11223344")
                .build();

        List<User> users = userRepository.saveAll(List.of(john, jane, alex));

        // Create sample vehicles
        Vehicle octavia = Vehicle.builder()
                .brand("Skoda")
                .model("Octavia")
                .year(2019)
                .vin("TMBJG12Z1K1234567")
                .licensePlate("OV-123AB")
                .isActive(true)
                .requiresApproval(false)
                .build();

        Vehicle corolla = Vehicle.builder()
                .brand("Toyota")
                .model("Corolla")
                .year(2020)
                .vin("JTDBR32E700123456")
                .licensePlate("TK-987CD")
                .isActive(true)
                .requiresApproval(false)
                .build();

        Vehicle golf = Vehicle.builder()
                .brand("Volkswagen")
                .model("Golf")
                .year(2018)
                .vin("WVWZZZ1KZAW123456")
                .licensePlate("VG-456EF")
                .isActive(true)
                .requiresApproval(false)
                .build();

        Vehicle superb = Vehicle.builder()
                .brand("Skoda")
                .model("Superb")
                .year(2021)
                .vin("TMBJG45L2M7654321")
                .licensePlate("SB-001GH")
                .isActive(true)
                .requiresApproval(true) // Seeded as luxury / approval required
                .build();

        List<Vehicle> vehicles = vehicleRepository.saveAll(List.of(octavia, corolla, golf, superb));

        // Create realistic reservations linking users and vehicles
        LocalDateTime now = LocalDateTime.now();

        Reservation upcoming = Reservation.builder()
                .vehicle(vehicles.get(0))
                .user(users.get(0))
                .startTime(now.plusDays(3).withHour(9).withMinute(0).withSecond(0).withNano(0))
                .endTime(now.plusDays(5).withHour(17).withMinute(0).withSecond(0).withNano(0))
                .state(ReservationState.CONFIRMED)
                .createdAt(now)
                .build();

        Reservation draft = Reservation.builder()
                .vehicle(vehicles.get(1))
                .user(users.get(1))
                .startTime(now.plusDays(10).withHour(8).withMinute(0).withSecond(0).withNano(0))
                .endTime(now.plusDays(12).withHour(18).withMinute(0).withSecond(0).withNano(0))
                .state(ReservationState.DRAFT)
                .createdAt(now)
                .build();

        reservationRepository.saveAll(List.of(upcoming, draft));

        log.info("DataInitializer: seeded {} users, {} vehicles and {} reservations",
                users.size(), vehicles.size(), reservationRepository.count());
    }
}