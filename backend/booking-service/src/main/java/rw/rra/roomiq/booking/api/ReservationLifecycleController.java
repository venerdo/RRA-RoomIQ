package rw.rra.roomiq.booking.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.booking.domain.dto.ReservationLifecycleResponse;
import rw.rra.roomiq.booking.domain.service.ReservationLifecycleService;
import rw.rra.roomiq.common.web.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations")
@SecurityRequirement(name = "bearerAuth")
public class ReservationLifecycleController {
    private final ReservationLifecycleService service;

    public ReservationLifecycleController(ReservationLifecycleService service) {
        this.service = service;
    }

    @PostMapping("/{reservationId}/check-in")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Check in to a confirmed reservation",
            description = "Requires current Identity authorization for the persisted organizer/building scope. The transition and server-generated checked-in timestamp are committed atomically.")
    public ApiResponse<ReservationLifecycleResponse> checkIn(@PathVariable UUID reservationId) {
        return ApiResponse.success("Reservation checked in", service.checkIn(reservationId));
    }

    @PostMapping("/{reservationId}/complete")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Complete an in-progress reservation",
            description = "Requires current Identity authorization for the persisted organizer/building scope. The transition and server-generated completion timestamp are committed atomically.")
    public ApiResponse<ReservationLifecycleResponse> complete(@PathVariable UUID reservationId) {
        return ApiResponse.success("Reservation completed", service.complete(reservationId));
    }

    @PostMapping("/{reservationId}/release")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Release an active reservation",
            description = "Requires current Identity authorization for the persisted organizer/building scope. Only confirmed or in-progress reservations can be released. The terminal state, occupancy release, and MeetingReleased event are committed atomically.")
    public ApiResponse<ReservationLifecycleResponse> release(@PathVariable UUID reservationId) {
        return ApiResponse.success("Reservation released", service.release(reservationId));
    }
}
