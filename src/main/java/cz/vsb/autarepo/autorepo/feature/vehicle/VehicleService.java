package cz.vsb.autarepo.autorepo.feature.vehicle;

import cz.vsb.autarepo.autorepo.common.exception.ResourceNotFoundException;
import cz.vsb.autarepo.autorepo.feature.vehicle.dto.VehicleForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
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
    public List<Vehicle> findAvailable() {
        return vehicleRepository.findByIsActiveTrue();
    }

    public Vehicle createVehicle(VehicleForm form) {
        Vehicle vehicle = Vehicle.builder()
                .brand(form.brand())
                .model(form.model())
                .year(form.year())
                .vin(form.vin())
                .licensePlate(form.licensePlate())
                .isActive(true)
                .build();
        return vehicleRepository.save(vehicle);
    }

    public Vehicle updateVehicle(Long id, VehicleForm form) {
        Vehicle vehicle = findById(id);
        vehicle.setBrand(form.brand());
        vehicle.setModel(form.model());
        vehicle.setYear(form.year());
        vehicle.setVin(form.vin());
        vehicle.setLicensePlate(form.licensePlate());
        return vehicleRepository.save(vehicle);
    }

    public void toggleAvailability(Long id) {
        Vehicle vehicle = findById(id);
        vehicle.setActive(!vehicle.isActive());
        vehicleRepository.save(vehicle);
    }

    public void deleteVehicle(Long id) {
        Vehicle vehicle = findById(id);
        vehicleRepository.delete(vehicle);
    }
}
