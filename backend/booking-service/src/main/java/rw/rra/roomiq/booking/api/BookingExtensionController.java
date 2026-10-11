package rw.rra.roomiq.booking.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.booking.domain.dto.BookingExtensionDecisionRequest;
import rw.rra.roomiq.booking.domain.dto.BookingExtensionResponse;
import rw.rra.roomiq.booking.domain.dto.CreateBookingExtensionRequest;
import rw.rra.roomiq.booking.domain.service.BookingExtensionService;
import rw.rra.roomiq.common.web.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Booking extensions")
@SecurityRequirement(name = "bearerAuth")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401", description = "Authentication required")
public class BookingExtensionController {
    private final BookingExtensionService service;

    public BookingExtensionController(BookingExtensionService service) {
        this.service = service;
    }

    @PostMapping("/reservations/{reservationId}/extensions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request a reservation extension",
            description = "Creates a pending extension for the authenticated reservation organizer. Recurring reservations are not eligible. An Idempotency-Key may be provided.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Pending extension request created or replayed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Current extension permission or reservation ownership denied")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "A pending extension exists or the idempotency key conflicts")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422", description = "Extension is invalid or the reservation is recurring")
    public ApiResponse<BookingExtensionResponse> request(
            @PathVariable UUID reservationId,
            @Valid @RequestBody CreateBookingExtensionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.success("Booking extension requested",
                service.request(reservationId, request, idempotencyKey));
    }

    @PostMapping("/booking-extensions/{extensionId}/decision")
    @Operation(summary = "Approve or reject an extension",
            description = "Requires current non-self scoped extension decision authority. Approval revalidates Scheduling and Room data, release-buffer occupancy, and conflicting reservations before atomically extending the reservation.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Extension decision recorded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Decision permission denied or self-decision attempted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "The extension is stale, already decided, or occupancy conflicts")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422", description = "Current Room or Scheduling policy rejects the extension")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503", description = "An authoritative owner service is unavailable or inconsistent")
    public ApiResponse<BookingExtensionResponse> decide(
            @PathVariable UUID extensionId,
            @Valid @RequestBody BookingExtensionDecisionRequest request) {
        return ApiResponse.success("Booking extension decision recorded", service.decide(extensionId, request));
    }

    @PostMapping("/booking-extensions/{extensionId}/cancel")
    @Operation(summary = "Cancel a pending extension request",
            description = "Only the authenticated requester with current extension permission may cancel a pending extension; the request history is retained.")
    public ApiResponse<BookingExtensionResponse> cancel(@PathVariable UUID extensionId) {
        return ApiResponse.success("Booking extension request cancelled", service.cancel(extensionId));
    }
}
