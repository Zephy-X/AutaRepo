package cz.vsb.autarepo.autorepo.feature.vehicle;

import cz.vsb.autarepo.autorepo.feature.vehicle.dto.VehicleForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/vehicles")
public class VehicleViewController {
    private final VehicleService vehicleService;

    public VehicleViewController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public String list(Model model) {
        List<Vehicle> vehicles = vehicleService.findAll();
        model.addAttribute("vehicles", vehicles);
        model.addAttribute("pageTitle", "Fleet Management");
        return "feature/vehicle/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("vehicleForm", new VehicleForm("", "", 2024, "", ""));
        model.addAttribute("pageTitle", "Add Vehicle");
        return "feature/vehicle/form";
    }

    @PostMapping
    public String createVehicle(@Valid @ModelAttribute("vehicleForm") VehicleForm vehicleForm,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Add Vehicle");
            return "feature/vehicle/form";
        }

        vehicleService.createVehicle(vehicleForm);
        redirectAttributes.addFlashAttribute("successMessage", "Vehicle added successfully");
        return "redirect:/vehicles";
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
        model.addAttribute("pageTitle", "Edit Vehicle");
        return "feature/vehicle/form";
    }

    @PostMapping("/{id}")
    public String updateVehicle(@PathVariable Long id,
                                @Valid @ModelAttribute("vehicleForm") VehicleForm vehicleForm,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("vehicleId", id);
            model.addAttribute("pageTitle", "Edit Vehicle");
            return "feature/vehicle/form";
        }

        vehicleService.updateVehicle(id, vehicleForm);
        redirectAttributes.addFlashAttribute("successMessage", "Vehicle updated successfully");
        return "redirect:/vehicles";
    }

    @PostMapping("/{id}/delete")
    public String deleteVehicle(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        vehicleService.deleteVehicle(id);
        redirectAttributes.addFlashAttribute("successMessage", "Vehicle deleted successfully");
        return "redirect:/vehicles";
    }

    @PostMapping("/{id}/toggle-availability")
    public String toggleAvailability(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        vehicleService.toggleAvailability(id);
        redirectAttributes.addFlashAttribute("successMessage", "Availability updated");
        return "redirect:/vehicles";
    }

    @PostMapping("/{id}/toggle-luxury")
    public String toggleLuxury(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        vehicleService.toggleLuxury(id);
        redirectAttributes.addFlashAttribute("successMessage", "Luxury/approval status updated");
        return "redirect:/vehicles";
    }
}