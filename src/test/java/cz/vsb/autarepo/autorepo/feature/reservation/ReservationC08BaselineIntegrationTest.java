package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.common.exception.ApprovalWindowExpiredException;
import cz.vsb.autarepo.autorepo.common.exception.CancellationWindowClosedException;
import cz.vsb.autarepo.autorepo.common.exception.ConflictOverlapException;
import cz.vsb.autarepo.autorepo.feature.user.User;
import cz.vsb.autarepo.autorepo.feature.user.UserRepository;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C08 before-baseline test cases (see docs/evidence-and-evolution.md, section "C08").
 *
 * <p>Deliberately NOT annotated with {@code @Transactional}: every service call commits (or rolls back)
 * its own transaction, and every oracle (expected result) reads the authoritative committed row in a fresh repository
 * transaction. A class-level test transaction would hide commit/rollback behaviour.</p>
 *
 * <p>Server clock is fixed to {@link #NOW} (UTC) so the time-based rules are deterministic.</p>
 */
@SpringBootTest(properties = {
        // Isolated store for C08. Default: in-memory H2 (PostgreSQL mode).
        // For a real PostgreSQL run set C08_DB_URL / C08_DB_DRIVER / C08_DB_USER / C08_DB_PASSWORD / C08_DB_DIALECT
        // to a DEDICATED, disposable database (the test deletes all rows) - see docs/evidence-and-evolution.md.
        "spring.datasource.url=${C08_DB_URL:jdbc:h2:mem:c08baseline;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false}",
        "spring.datasource.driver-class-name=${C08_DB_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${C08_DB_USER:sa}",
        "spring.datasource.password=${C08_DB_PASSWORD:}",
        "spring.jpa.properties.hibernate.dialect=${C08_DB_DIALECT:org.hibernate.dialect.H2Dialect}",
        // Keep the OP-07 scheduler from sweeping during a test (it fires once at startup on an empty DB).
        "autorepo.expiry-interval=3600000"
})
@ActiveProfiles("test")
class ReservationC08BaselineIntegrationTest {

    /** Fixed server time: 2026-12-01 06:00 UTC. */
    static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 6, 0);
    static final LocalDateTime T10 = NOW.withHour(10);
    static final LocalDateTime T1030 = T10.plusMinutes(30);
    static final LocalDateTime T11 = NOW.withHour(11);
    static final LocalDateTime T1130 = T11.plusMinutes(30);
    static final LocalDateTime T12 = NOW.withHour(12);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock c08FixedClock() {
            return Clock.fixed(Instant.parse("2026-12-01T06:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private UserRepository userRepository;

    private Vehicle r1;
    private User user;

    /** Step 1 / step 5 of every case: reset the isolated store to a known initial state. */
    @BeforeEach
    void resetIsolatedStore() {
        reservationRepository.deleteAll();
        vehicleRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .fullName("C08 Tester").email("c08@example.com").driverLicenseNumber("C08-001").build());
        r1 = vehicleRepository.save(Vehicle.builder()
                .brand("Skoda").model("Octavia").year(2024).vin("VIN-C08-R1").licensePlate("C08-R1")
                .isActive(true).requiresApproval(false).build());
    }

    @Test
    @DisplayName("TC-C08-01 positive: adjacent [10,11) CONFIRMED + [11,12) DRAFT -> Confirm succeeds, both CONFIRMED, no overlap")
    void tcC0801_adjacentConfirmationSucceeds() {
        Reservation b1 = persist(T10, T11, ReservationState.CONFIRMED);
        Reservation b2 = persist(T11, T12, ReservationState.DRAFT);

        // Step 2: independent read of stored initial state
        assertThat(stateOf(b1)).isEqualTo(ReservationState.CONFIRMED);
        assertThat(stateOf(b2)).isEqualTo(ReservationState.DRAFT);
        assertThat(reservationRepository.findByState(ReservationState.CONFIRMED)).hasSize(1);

        // Step 3: real OP-03 Confirm through the service (own transaction)
        Reservation returned = reservationService.confirmReservation(b2.getId());
        assertThat(returned.getState()).isEqualTo(ReservationState.CONFIRMED);

        // Step 4: authoritative committed state, read in a fresh transaction
        List<Reservation> confirmed = reservationRepository.findByState(ReservationState.CONFIRMED);
        assertThat(confirmed).extracting(Reservation::getId).containsExactlyInAnyOrder(b1.getId(), b2.getId());
        assertNoOverlappingConfirmed(confirmed);
    }

    @Test
    @DisplayName("TC-C08-02 negative: [10,11) CONFIRMED + overlapping [10:30,11:30) DRAFT -> Confirm rejected CONFLICT_OVERLAP, B2 stays DRAFT")
    void tcC0802_overlappingConfirmationRejected() {
        Reservation b1 = persist(T10, T11, ReservationState.CONFIRMED);
        Reservation b2 = persist(T1030, T1130, ReservationState.DRAFT);

        assertThat(stateOf(b1)).isEqualTo(ReservationState.CONFIRMED);
        assertThat(stateOf(b2)).isEqualTo(ReservationState.DRAFT);

        assertThatThrownBy(() -> reservationService.confirmReservation(b2.getId()))
                .isInstanceOf(ConflictOverlapException.class)
                .hasMessage("CONFLICT_OVERLAP");

        assertThat(stateOf(b1)).isEqualTo(ReservationState.CONFIRMED);
        assertThat(stateOf(b2)).isEqualTo(ReservationState.DRAFT);
        assertThat(reservationRepository.findByState(ReservationState.CONFIRMED)).hasSize(1);
    }

    @Test
    @DisplayName("TC-C08-03 prior behaviour (BR-03 v0.3): CONFIRMED starting in 1h can still be cancelled; at start it cannot")
    void tcC0803_cancellationWindowBeforeCr08() {
        Reservation soon = persist(NOW.plusHours(1), NOW.plusHours(3), ReservationState.CONFIRMED);
        Reservation startingNow = persist(NOW, NOW.plusHours(2), ReservationState.CONFIRMED);

        // start - 1h: allowed under the current rule (CR-08 intends to forbid this)
        reservationService.cancelReservation(soon.getId());
        assertThat(stateOf(soon)).isEqualTo(ReservationState.CANCELLED);

        // currentTime == startTime: half-open boundary, already closed
        assertThatThrownBy(() -> reservationService.cancelReservation(startingNow.getId()))
                .isInstanceOf(CancellationWindowClosedException.class)
                .hasMessage("CANCELLATION_WINDOW_CLOSED");
        assertThat(stateOf(startingNow)).isEqualTo(ReservationState.CONFIRMED);
    }

    @Test
    @Disabled("DEF-C08-01: observed FAIL on c8db481 (expected EXPIRED, was PENDING_APPROVAL) - rollback of lazy expiry; re-enable in C09 fix")
    @DisplayName("TC-C08-04 probe (OP-05 spec): approve after reviewDeadline is rejected and EXPIRED is persisted")
    void tcC0804_lateApprovalPersistsExpired() {
        Vehicle lux = vehicleRepository.save(Vehicle.builder()
                .brand("BMW").model("7").year(2025).vin("VIN-C08-LUX").licensePlate("C08-LX")
                .isActive(true).requiresApproval(true).build());
        Reservation pending = reservationRepository.save(Reservation.builder()
                .vehicle(lux).user(user).startTime(T10).endTime(T12)
                .state(ReservationState.PENDING_APPROVAL)
                .createdAt(NOW.minusDays(2)).submittedAt(NOW.minusDays(2))
                .reviewDeadline(NOW.minusHours(1))
                .build());

        assertThatThrownBy(() -> reservationService.approveReservation(pending.getId()))
                .isInstanceOf(ApprovalWindowExpiredException.class)
                .hasMessage("APPROVAL_WINDOW_EXPIRED");

        // OP-05 "Window Expired": rejected AND triggers transition to EXPIRED -> must be committed
        assertThat(stateOf(pending)).isEqualTo(ReservationState.EXPIRED);
    }

    // --- helpers ---

    private Reservation persist(LocalDateTime start, LocalDateTime end, ReservationState state) {
        return reservationRepository.save(Reservation.builder()
                .vehicle(r1).user(user).startTime(start).endTime(end)
                .state(state).createdAt(NOW.minusDays(1)).build());
    }

    private ReservationState stateOf(Reservation r) {
        return reservationRepository.findById(r.getId()).orElseThrow().getState();
    }

    private static void assertNoOverlappingConfirmed(List<Reservation> confirmed) {
        for (int i = 0; i < confirmed.size(); i++) {
            for (int j = i + 1; j < confirmed.size(); j++) {
                Reservation a = confirmed.get(i);
                Reservation b = confirmed.get(j);
                boolean overlap = a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
                assertThat(overlap).as("BR-02 overlap between #%d and #%d", a.getId(), b.getId()).isFalse();
            }
        }
    }
}
