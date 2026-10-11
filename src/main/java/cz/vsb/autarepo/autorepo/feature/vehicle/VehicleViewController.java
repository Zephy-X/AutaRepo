package cz.vsb.autarepo.autorepo.feature.vehicle;

import cz.vsb.autarepo.autorepo.feature.vehicle.dto.VehicleForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/vehicles")
public class VehicleViewController {
    private static final String LIST_VIEW = "feature/vehicle/list";
    private static final String FORM_VIEW = "feature/vehicle/form";
    private static final String VEHICLES_REDIRECT = "redirect:/vehicles";
    private static final String PAGE_TITLE = "Fleet Management";
    private static final String CREATE_TITLE = "Add Vehicle";
    private static final String EDIT_TITLE = "Edit Vehicle";

    private final VehicleService vehicleService;

    public VehicleViewController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public String list(Model model) {
        List<Vehicle> vehicles = vehicleService.findAll();
        model.addAttribute("vehicles", vehicles);
        model.addAttribute("pageTitle", PAGE_TITLE);
        return LIST_VIEW;
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("vehicleForm", new VehicleForm("", "", 2024, "", ""));
        return renderForm(model, CREATE_TITLE);
    }

    @PostMapping
    public String createVehicle(@Valid @ModelAttribute("vehicleForm") VehicleForm vehicleForm,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderForm(model, CREATE_TITLE);
        }

        vehicleService.createVehicle(vehicleForm);
        redirectAttributes.addFlashAttribute("successMessage", "Vehicle added successfully");
        return VEHICLES_REDIRECT;
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        Vehicle vehicle = vehicleService.findById(id);
        VehicleForm form = new VehicleForm(
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getVin(),
                vehicle.getLicensePlate()
        );
        model.addAttribute("vehicleForm", form);
        model.addAttribute("vehicleId", id);
        return renderForm(model, EDIT_TITLE);
    }

    @PostMapping("/{id}")
    public String updateVehicle(@PathVariable Long id,
                                @Valid @ModelAttribute("vehicleForm") VehicleForm vehicleForm,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("vehicleId", id);
            return renderForm(model, EDIT_TITLE);
        }

        vehicleService.updateVehicle(id, vehicleForm);
        redirectAttributes.addFlashAttribute("successMessage", "Vehicle updated successfully");
        return VEHICLES_REDIRECT;
    }

    @PostMapping("/{id}/delete")
    public String deleteVehicle(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        vehicleService.deleteVehicle(id);
        redirectAttributes.addFlashAttribute("successMessage", "Vehicle deleted successfully");
        return VEHICLES_REDIRECT;
    }

    @PostMapping("/{id}/toggle-availability")
    public String toggleAvailability(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        vehicleService.toggleAvailability(id);
        redirectAttributes.addFlashAttribute("successMessage", "Availability updated");
        return VEHICLES_REDIRECT;
    }

    @PostMapping("/{id}/toggle-luxury")
    public String toggleLuxury(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        vehicleService.toggleLuxury(id);
        redirectAttributes.addFlashAttribute("successMessage", "Luxury/approval status updated");
        return VEHICLES_REDIRECT;
    }

    private String renderForm(Model model, String pageTitle) {
        model.addAttribute("pageTitle", pageTitle);
        return FORM_VIEW;
    }
}