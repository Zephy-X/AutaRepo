package cz.vsb.autarepo.autorepo.feature.vehicle;

import cz.vsb.autarepo.autorepo.common.exception.ResourceNotFoundException;
import cz.vsb.autarepo.autorepo.feature.vehicle.dto.VehicleForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class VehicleService {
    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public List<Vehicle> findAll() {
        return vehicleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Vehicle findById(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Vehicle findByIdForUpdate(Long id) {
        return vehicleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Vehicle> findAvailable() {
        return vehicleRepository.findByIsActiveTrue();
    }

    @Transactional
    public Vehicle createVehicle(VehicleForm form) {
        Vehicle vehicle = new Vehicle();
        applyForm(vehicle, form);
        vehicle.setActive(true);
        vehicle.setRequiresApproval(false);
        return vehicleRepository.save(vehicle);
    }

    @Transactional
    public Vehicle updateVehicle(Long id, VehicleForm form) {
        Vehicle vehicle = findById(id);
        applyForm(vehicle, form);
        return vehicleRepository.save(vehicle);
    }

    private void applyForm(Vehicle vehicle, VehicleForm form) {
        vehicle.setBrand(form.brand());
        vehicle.setModel(form.model());
        vehicle.setYear(form.year());
        vehicle.setVin(form.vin());
        vehicle.setLicensePlate(form.licensePlate());
    }

    @Transactional
    public void toggleAvailability(Long id) {
        Vehicle vehicle = findById(id);
        vehicle.setActive(!vehicle.isActive());
        vehicleRepository.save(vehicle);
    }

    @Transactional
    public void toggleLuxury(Long id) {
        Vehicle vehicle = findById(id);
        vehicle.setRequiresApproval(!vehicle.isRequiresApproval());
        vehicleRepository.save(vehicle);
    }

    @Transactional
    public void deleteVehicle(Long id) {
        Vehicle vehicle = findById(id);
        vehicleRepository.delete(vehicle);
    }
}