package rw.rra.roomiq.booking.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.booking.domain.dto.CancellationRequest;
import rw.rra.roomiq.booking.domain.dto.CancellationResponse;
import rw.rra.roomiq.booking.domain.service.BookingCancellationService;

import java.util.UUID;

@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Booking cancellations")
public class BookingCancellationController {
    private final BookingCancellationService cancellationService;

    public BookingCancellationController(BookingCancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @PostMapping("/api/v1/booking-requests/{requestId}/cancel")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Cancel an eligible request without a reservation",
            description = "Cancels a DRAFT, PENDING_APPROVAL, or APPROVED request only when no reservation exists. Uses current Identity owner/admin authorization, persists cancellation history and retains the request. Repeated calls return the original cancellation.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Booking request cancellation recorded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Current cancellation authority or scope is denied")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "The request is not cancellable or already has a reservation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503", description = "Identity authorization is unavailable")
    public rw.rra.roomiq.common.web.ApiResponse<CancellationResponse> cancelRequest(
            @PathVariable UUID requestId, @Valid @RequestBody CancellationRequest request) {
        return rw.rra.roomiq.common.web.ApiResponse.success("Booking request cancelled",
                cancellationService.cancelRequest(requestId, request));
    }

    @PostMapping("/api/v1/reservations/{reservationId}/cancel")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Cancel a confirmed or in-progress reservation",
            description = "Rechecks current Identity and Room cancellation policy, records the deadline snapshot and any authorized administrator override, then atomically cancels the request/reservation and releases every occurrence in the series without deleting history.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Reservation cancellation recorded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Current cancellation authority, scope, or deadline override is denied")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "The reservation is stale or not cancellable")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503", description = "An authoritative owner service is unavailable")
    public rw.rra.roomiq.common.web.ApiResponse<CancellationResponse> cancelReservation(
            @PathVariable UUID reservationId, @Valid @RequestBody CancellationRequest request) {
        return rw.rra.roomiq.common.web.ApiResponse.success("Reservation cancelled",
                cancellationService.cancelReservation(reservationId, request));
    }
}
