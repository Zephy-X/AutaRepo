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
}