package cz.vsb.autarepo.autorepo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationRepository repository;

    public ReservationController(ReservationRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public Long createReservation(@RequestBody Reservation reservation) {
        Reservation saved = repository.save(reservation);
        return saved.getId();
    }

    @GetMapping
    public List<Reservation> getAllReservations() {
        return repository.findAll();
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<Reservation> confirmReservation(@PathVariable Long id) {
        return repository.findById(id).map(reservation -> {
            reservation.setState(ReservationState.CONFIRMED);
            Reservation updated = repository.save(reservation);
            return ResponseEntity.ok(updated);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}