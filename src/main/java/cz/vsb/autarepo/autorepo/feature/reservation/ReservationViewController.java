package cz.vsb.autarepo.autorepo.feature.reservation;

import cz.vsb.autarepo.autorepo.common.exception.ApprovalWindowExpiredException;
import cz.vsb.autarepo.autorepo.common.exception.CancellationWindowClosedException;
import cz.vsb.autarepo.autorepo.common.exception.ConflictOverlapException;
import cz.vsb.autarepo.autorepo.common.exception.DomainErrorMessages;
import cz.vsb.autarepo.autorepo.common.exception.ResourceNotFoundException;
import cz.vsb.autarepo.autorepo.feature.reservation.dto.ReservationForm;
import cz.vsb.autarepo.autorepo.feature.user.UserService;
import cz.vsb.autarepo.autorepo.feature.vehicle.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Function;

@Controller
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationViewController {

    private static final String VIEW = "feature/reservation/list";
    private static final String FRAGMENT_SECTION = VIEW + " :: section";
    private static final String FRAGMENT_AVAILABILITY = VIEW + " :: availability-status";

    private final ReservationService reservationService;
    private final VehicleService vehicleService;
    private final UserService userService;
    private final Clock clock;

    @ModelAttribute
    public void populateSharedModel(Model model) {
        populateReservationModel(model);
    }

    private void populateReservationModel(Model model) {
        model.addAttribute("vehicles", vehicleService.findAll());
        model.addAttribute("users", userService.findAll());
        model.addAttribute("reservations", reservationService.findAll());
        model.addAttribute("draftReservations", reservationService.findDrafts());
        model.addAttribute("pendingApprovals", reservationService.findPendingApprovals());
        model.addAttribute("canCancelNow", now());
        model.addAttribute("pageTitle", "Reservations");
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("reservationForm", defaultForm());
        model.addAttribute("availabilityStatus", null);
        return VIEW;
    }

    @GetMapping("/approvals")
    public String approvals(Model model) {
        model.addAttribute("pendingApprovals", reservationService.findPendingApprovals());
        return "feature/reservation/approvals";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("reservation", reservationService.findById(id));
        return "feature/reservation/detail";
    }

    @PostMapping
    public String createReservation(@Valid @ModelAttribute("reservationForm") ReservationForm form,
                                    BindingResult result,
                                    @RequestHeader(value = "HX-Request", required = false) boolean isHtmx,
                                    Model model,
                                    RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return isHtmx ? FRAGMENT_SECTION : VIEW;
        }

        try {
            Reservation reservation = reservationService.createReservation(form);
            String message = "Draft created (Reservation #" + reservation.getId() + "). Note: Vehicle is not locked yet!";
            if (isHtmx) {
                model.addAttribute("reservationForm", defaultForm());
                model.addAttribute("successMessage", message);
                populateReservationModel(model);
                return FRAGMENT_SECTION;
            }
            redirect.addFlashAttribute("successMessage", message);
            return "redirect:/reservations";
        } catch (IllegalArgumentException e) {
            result.rejectValue("startDate", "reservation.startDate.invalid", DomainErrorMessages.resolve(e.getMessage()));
            return isHtmx ? FRAGMENT_SECTION : VIEW;
        }
    }

    @PostMapping("/availability")
    public String checkAvailability(@Valid @ModelAttribute("reservationForm") ReservationForm form,
                                    BindingResult result,
                                    @RequestHeader(value = "HX-Request", required = false) boolean isHtmx,
                                    Model model) {
        String status;
        if (result.hasErrors()) {
            status = "INVALID";
        } else {
            try {
                status = reservationService.checkAvailabilityStatus(form.vehicleId(), form.toStartDateTime(), form.toEndDateTime());
            } catch (IllegalArgumentException e) {
                result.rejectValue("startDate", "reservation.window.invalid", DomainErrorMessages.resolve(e.getMessage()));
                status = "INVALID";
            }
        }

        model.addAttribute("availabilityStatus", status);
        return isHtmx ? FRAGMENT_AVAILABILITY : VIEW;
    }

    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes redirect) {
        return handleAction(id, redirect, reservationService::confirmReservation, r ->
                r.getState() == ReservationState.PENDING_APPROVAL
                        ? "Luxury vehicle requires approval. Request submitted to Fleet Manager."
                        : "Reservation confirmed.");
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes redirect) {
        return handleAction(id, redirect, reservationService::approveReservation,
                r -> "Reservation #" + r.getId() + " approved and confirmed.");
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, RedirectAttributes redirect) {
        return handleAction(id, redirect, reservationService::rejectReservation,
                r -> "Reservation #" + r.getId() + " rejected.");
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirect) {
        return handleAction(id, redirect, reservationService::cancelReservation,
                r -> "Reservation cancelled.");
    }

    // HELPER FUNCTIONS: //

    private String handleAction(Long id,
                                RedirectAttributes redirect,
                                Function<Long, Reservation> action,
                                Function<Reservation, String> successMsg) {
        try {
            Reservation res = action.apply(id);
            redirect.addFlashAttribute("successMessage", successMsg.apply(res));
        } catch (IllegalStateException | ConflictOverlapException | CancellationWindowClosedException |
                 ApprovalWindowExpiredException | ResourceNotFoundException e) {
            redirect.addFlashAttribute("errorMessage", DomainErrorMessages.resolve(e.getMessage()));
        }
        return "redirect:/reservations";
    }

    private ReservationForm defaultForm() {
        LocalDateTime start = now().plusHours(3).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1);
        return new ReservationForm(null, null, start.toLocalDate(), start.getHour(), end.toLocalDate(), end.getHour());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}