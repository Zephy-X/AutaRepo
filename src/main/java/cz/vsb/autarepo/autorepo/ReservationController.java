package cz.vsb.autarepo.autorepo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationRepository repository;

    public ReservationController(ReservationRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Long> createReservation(@RequestBody Reservation reservation) {
        Reservation saved = repository.save(reservation);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.getId());
    }
    @GetMapping
    public java.util.List<Reservation> getAllReservations() {
        return repository.findAll();
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    @PutMapping("/{id}/confirm")
    public ResponseEntity<Reservation> confirmReservation(@PathVariable Long id) {
        return repository.findById(id).map(reservation -> {
            reservation.setState(ReservationState.CONFIRMED);
            Reservation updated = repository.save(reservation);
            return ResponseEntity.ok(updated);
        }).orElse(ResponseEntity.notFound().build());
    }
}