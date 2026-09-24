package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.feature.reservation.dto.ReservationForm;
import cz.vsb.autarepo.autorepo.feature.user.UserService;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
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
        populateReservationFormData(model);
        model.addAttribute("reservations", reservationService.findAll());
        model.addAttribute("draftReservations", reservationService.findDrafts());

        LocalDate today = LocalDate.now();
        int nextHour = (LocalDateTime.now().getHour() + 1) % 24;
        ReservationForm defaultForm = new ReservationForm(null, null, today, nextHour, today.plusDays(1), nextHour);

        model.addAttribute("reservationForm", defaultForm);
        model.addAttribute("availabilityStatus", null);
        model.addAttribute("canCancelNow", LocalDateTime.now());
        model.addAttribute("pageTitle", "Reservations");
        return "feature/reservation/list";
    }

    @PostMapping
    public String createReservation(@Valid @ModelAttribute("reservationForm") ReservationForm reservationForm,
                                    BindingResult bindingResult,
                                    @RequestHeader(value = "HX-Request", required = false) boolean isHtmx,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        validateReservationDates(reservationForm, bindingResult);

        if (bindingResult.hasErrors()) {
            populateReservationFormData(model);
            model.addAttribute("reservations", reservationService.findAll());
            model.addAttribute("draftReservations", reservationService.findDrafts());
            model.addAttribute("availabilityStatus", null);
            model.addAttribute("canCancelNow", LocalDateTime.now());

            return isHtmx ? "feature/reservation/list :: section" : "feature/reservation/list";
        }

        try {
            Reservation reservation = reservationService.createReservation(reservationForm);
            if (isHtmx) {
                model.addAttribute("successMessage", "Draft created (Reservation #" + reservation.getId() + "). Note: Vehicle is not locked yet!");
                populateReservationFormData(model);
                model.addAttribute("reservations", reservationService.findAll());
                model.addAttribute("draftReservations", reservationService.findDrafts());
                model.addAttribute("canCancelNow", LocalDateTime.now());
                return "feature/reservation/list :: section";
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Draft created (Reservation #" + reservation.getId() + "). Note: Vehicle is not locked yet!");
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("startDate", "reservation.startDate.invalid", reservationService.resolveErrorMessage(e.getMessage()));
            populateReservationFormData(model);
            model.addAttribute("reservations", reservationService.findAll());
            model.addAttribute("draftReservations", reservationService.findDrafts());
            model.addAttribute("availabilityStatus", null);
            model.addAttribute("canCancelNow", LocalDateTime.now());
            return isHtmx ? "feature/reservation/list :: section" : "feature/reservation/list";
        }

        return "redirect:/reservations";
    }

    @PostMapping("/availability")
    public String checkAvailability(@Valid @ModelAttribute("reservationForm") ReservationForm reservationForm,
                                    BindingResult bindingResult,
                                    @RequestHeader(value = "HX-Request", required = false) boolean isHtmx,
                                    Model model) {
        validateReservationDates(reservationForm, bindingResult);

        if (bindingResult.hasErrors()) {
            model.addAttribute("availabilityStatus", "INVALID");
            return isHtmx ? "feature/reservation/list :: availability-status" : renderFullFallback(model);
        }

        LocalDateTime start = reservationForm.toStartDateTime();
        LocalDateTime end = reservationForm.toEndDateTime();

        String status = reservationService.checkAvailabilityStatus(
                reservationForm.vehicleId(),
                start,
                end
        );

        model.addAttribute("availabilityStatus", status);

        if (isHtmx) {
            return "feature/reservation/list :: availability-status";
        }

        return renderFullFallback(model);
    }

    @PostMapping("/{id}/confirm")
    public String confirmReservation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reservationService.confirmReservation(id);
            redirectAttributes.addFlashAttribute("successMessage", "Reservation confirmed.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", reservationService.resolveErrorMessage(e.getMessage()));
        }
        return "redirect:/reservations";
    }

    @PostMapping("/{id}/cancel")
    public String cancelReservation(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reservationService.cancelReservation(id);
            redirectAttributes.addFlashAttribute("successMessage", "Reservation cancelled.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", reservationService.resolveErrorMessage(e.getMessage()));
        }
        return "redirect:/reservations";
    }

    private void validateReservationDates(ReservationForm reservationForm, BindingResult bindingResult) {
        LocalDateTime start = reservationForm.toStartDateTime();
        LocalDateTime end = reservationForm.toEndDateTime();

        if (start == null || end == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!start.isAfter(now)) {
            bindingResult.rejectValue(
                    "startDate",
                    "reservation.startDate.future",
                    "Start time must be in the future"
            );
        }

        if (!end.isAfter(start)) {
            bindingResult.rejectValue(
                    "endDate",
                    "reservation.endDate.afterStart",
                    "End time must be after start time"
            );
        }
    }

    private void populateReservationFormData(Model model) {
        model.addAttribute("vehicles", vehicleService.findAll());
        model.addAttribute("users", userService.findAll());
        model.addAttribute("pageTitle", "Reservations");
    }

    private String renderFullFallback(Model model) {
        populateReservationFormData(model);
        model.addAttribute("reservations", reservationService.findAll());
        model.addAttribute("draftReservations", reservationService.findDrafts());
        model.addAttribute("canCancelNow", LocalDateTime.now());
        return "feature/reservation/list";
    }
}