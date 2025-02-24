package me.enrico.carbooking.controllers;

import lombok.RequiredArgsConstructor;
import me.enrico.carbooking.dto.BookingDTO;
import me.enrico.carbooking.dto.CarDTO;
import me.enrico.carbooking.exception.ResourceNotFoundException;
import me.enrico.carbooking.model.Booking;
import me.enrico.carbooking.model.Car;
import me.enrico.carbooking.repositories.BookingRepository;
import me.enrico.carbooking.repositories.CarRepository;
import me.enrico.carbooking.request.CarBookingRequest;
import me.enrico.carbooking.service.BookingService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
public class CarController {

    private final CarRepository carRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private static final ZoneId ROME_ZONE = ZoneId.of("Europe/Rome");


    @Cacheable("cars")
    @GetMapping
    public ResponseEntity<List<CarDTO>> getAllCars() {
        List<Car> cars = carRepository.findAll();
        return ResponseEntity.ok(cars.stream()
                .map(this::convertToCarDTO)
                .collect(Collectors.toList()));
    }


    @Cacheable("occupiedCars")
    @GetMapping("/occupied")
    public ResponseEntity<List<BookingDTO>> getCurrentlyOccupiedCars() {
        LocalDateTime now = LocalDateTime.now(ROME_ZONE);
        List<Booking> occupiedCars = bookingRepository
                .findCurrentlyOccupiedWithCars(now);
        return ResponseEntity.ok(occupiedCars.stream()
                .map(this::convertToBookingDTO)
                .collect(Collectors.toList()));
    }

    @Cacheable("futureBookings")
    @GetMapping("/future-bookings")
    public ResponseEntity<List<BookingDTO>> getFutureBookedCars() {
        LocalDateTime now = LocalDateTime.now(ROME_ZONE);
        List<Booking> futureBookings = bookingRepository
                .findFutureBookingsWithCars(now);
        return ResponseEntity.ok(futureBookings.stream()
                .map(this::convertToBookingDTO)
                .collect(Collectors.toList()));
    }

    @CacheEvict(value = {"cars", "occupiedCars", "futureBookings"}, allEntries = true)
    @PostMapping("/book/{id}")
    public ResponseEntity<String> bookCar(
            @PathVariable Long id,
            @RequestBody CarBookingRequest request) {
        try {
            Car car = carRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Auto non trovata con id: " + id));

            String result = bookingService.createBooking(car, request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Errore durante la prenotazione: " + e.getMessage());
        }
    }


    @CacheEvict(value = {"cars", "occupiedCars", "futureBookings"}, allEntries = true)
    @PostMapping("/terminate/{id}")
    public ResponseEntity<String> terminateBooking(@PathVariable Long id) {
        return handleBookingStatusChange(id, "terminata");
    }

    @CacheEvict(value = {"cars", "occupiedCars", "futureBookings"}, allEntries = true)
    @DeleteMapping("/cancel/{id}")
    public ResponseEntity<String> cancelBooking(@PathVariable Long id) {
        return handleBookingStatusChange(id, "annullata");
    }

    private ResponseEntity<String> handleBookingStatusChange(Long id, String action) {
        try {
            Booking booking = bookingRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Prenotazione non trovata con id: " + id));

            if (!booking.isActive()) {
                return ResponseEntity.badRequest().body("La prenotazione è già stata " + action + "!");
            }

            booking.setActive(false);
            bookingRepository.save(booking);
            return ResponseEntity.ok("Prenotazione " + action + " con successo!");
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Errore durante l'operazione: " + e.getMessage());
        }
    }

    private CarDTO convertToCarDTO(Car car) {
        return CarDTO.builder()
                .id(car.getId())
                .name(car.getName())
                .seats(car.getSeats())
                .available(car.isAvailable())
                .build();
    }

    private BookingDTO convertToBookingDTO(Booking booking) {
        return BookingDTO.builder()
                .id(booking.getId())
                .car(convertToCarDTO(booking.getCar()))
                .bookedByName(booking.getBookedByName())
                .bookedAt(booking.getBookedAt())
                .startDateTime(booking.getStartDateTime())
                .endDateTime(booking.getEndDateTime())
                .duration(booking.getDuration())
                .reason(booking.getReason())
                .active(booking.isActive())
                .build();
    }
}