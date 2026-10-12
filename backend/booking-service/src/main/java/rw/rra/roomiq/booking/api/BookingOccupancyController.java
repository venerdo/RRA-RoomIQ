package rw.rra.roomiq.booking.api;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.booking.domain.dto.BookingOccupancyRequest;
import rw.rra.roomiq.booking.domain.dto.BookingOccupancySnapshotResponse;
import rw.rra.roomiq.booking.domain.service.BookingOccupancyService;
import rw.rra.roomiq.common.web.ApiResponse;

@RestController
@RequestMapping("/api/v1/internal/availability")
@Tag(name = "Internal availability")
@Hidden
@SecurityRequirement(name = "bearerAuth")
public class BookingOccupancyController {
    private final BookingOccupancyService occupancyService;

    public BookingOccupancyController(BookingOccupancyService occupancyService) {
        this.occupancyService = occupancyService;
    }

    @PostMapping("/occupancy")
    public ApiResponse<BookingOccupancySnapshotResponse> occupancy(
            @Valid @RequestBody BookingOccupancyRequest request) {
        return ApiResponse.success("Booking occupancy snapshot", occupancyService.snapshot(request));
    }
}
