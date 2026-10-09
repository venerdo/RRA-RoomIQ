package rw.rra.roomiq.booking.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.booking.domain.dto.CreateBookingRequest;
import rw.rra.roomiq.booking.domain.dto.DirectBookingResponse;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.service.BookingDirectBookingService;
import rw.rra.roomiq.common.web.ApiResponse;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings")
@SecurityRequirement(name = "bearerAuth")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401", description = "Authentication required")
public class BookingDirectBookingController {
    private final BookingDirectBookingService service;

    public BookingDirectBookingController(BookingDirectBookingService service) {
        this.service = service;
    }

    @PostMapping("/direct")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an authorized direct booking",
            description = "Requires current Admin/Super Admin direct-booking authority or an eligible Secretary. Any initiator subject to the active room approval rule receives a PENDING_APPROVAL request without a reservation or meeting; permitted direct confirmations atomically create the reservation, every validated occurrence, and the private meeting.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Direct booking confirmed or submitted for required approval")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Current permission, privilege, or organizational scope denied")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Idempotency key conflict or occurrence occupancy conflict")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422", description = "Authoritative room or scheduling policy rejected the booking")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503", description = "An authoritative owner service is unavailable or inconsistent")
    public ApiResponse<DirectBookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        DirectBookingResponse response = service.create(request, idempotencyKey);
        String message = response.status() == BookingRequestStatus.PENDING_APPROVAL
                ? "Booking request submitted for approval" : "Direct booking confirmed";
        return ApiResponse.success(message, response);
    }
}
