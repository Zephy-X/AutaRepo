package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.feature.reservation.dto.ReservationForm;
import cz.vsb.autarepo.autorepo.feature.user.User;
import cz.vsb.autarepo.autorepo.feature.user.UserService;
import cz.vsb.autarepo.autorepo.feature.vehicle.Vehicle;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/reservations")
public class ReservationViewController {
    private final ReservationService reservationService;
    private final VehicleService vehicleService;
    private final UserService userService;

    public ReservationViewController(ReservationService reservationService,
                                    VehicleService vehicleService,
                                    UserService userService) {
        this.reservationService = reservationService;
        this.vehicleService = vehicleService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        List<Reservation> reservations = reservationService.findAll();
        model.addAttribute("reservations", reservations);
        model.addAttribute("pageTitle", "Reservations");
        return "feature/reservation/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        List<Vehicle> vehicles = vehicleService.findAll();
        List<User> users = userService.findAll();

        model.addAttribute("reservationForm", new ReservationForm(null, null, null, null));
        model.addAttribute("vehicles", vehicles);
        model.addAttribute("users", users);
        model.addAttribute("pageTitle", "New Reservation");
        return "feature/reservation/form";
    }

    @PostMapping
    public String createReservation(@Valid @ModelAttribute ReservationForm reservationForm,
                                   RedirectAttributes redirectAttributes) {
        try {
            reservationService.createReservation(reservationForm);
            redirectAttributes.addFlashAttribute("successMessage", "Reservation created successfully");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/reservations/create";
        }
        return "redirect:/reservations";
    }

    @PostMapping("/{id}/confirm")
    public String confirmReservation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reservationService.confirmReservation(id);
            redirectAttributes.addFlashAttribute("successMessage", "Reservation confirmed");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/reservations";
    }

    @PostMapping("/{id}/cancel")
    public String cancelReservation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reservationService.cancelReservation(id);
            redirectAttributes.addFlashAttribute("successMessage", "Reservation cancelled");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/reservations";
    }
}
