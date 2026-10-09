package rw.rra.roomiq.booking.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.booking.domain.dto.BookingRequestPageQuery;
import rw.rra.roomiq.booking.domain.dto.BookingRequestPageResponse;
import rw.rra.roomiq.booking.domain.dto.BookingRequestResponse;
import rw.rra.roomiq.booking.domain.dto.CreateBookingRequest;
import rw.rra.roomiq.booking.domain.service.BookingRequestService;
import rw.rra.roomiq.common.web.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking-requests")
@Tag(name = "Booking requests")
@SecurityRequirement(name = "bearerAuth")
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401", description = "Authentication required")
public class BookingRequestController {
    private final BookingRequestService service;

    public BookingRequestController(BookingRequestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking request",
            description = "The authenticated Identity account supplies the requester; current Organization, Room, and Scheduling owner APIs validate all referenced data before a DRAFT is persisted.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Validated booking request created in DRAFT state")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Booking request permission or scope denied")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "422", description = "Authoritative booking policy rejected the request")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503", description = "An authoritative owner service is unavailable")
    public ApiResponse<BookingRequestResponse> create(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.success("Booking request created", service.create(request, idempotencyKey));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a draft booking request",
            description = "Revalidates current Organization, Room, Scheduling, and Identity policy before atomically moving the request from DRAFT to PENDING_APPROVAL. Only its requester may submit it.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Booking request submitted for approval")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Only the requester with current permission and scope may submit")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Booking request is not in DRAFT state")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "503", description = "An authoritative owner service is unavailable")
    public ApiResponse<BookingRequestResponse> submit(@PathVariable UUID id) {
        return ApiResponse.success("Booking request submitted for approval", service.submit(id));
    }

    @GetMapping
    @Operation(summary = "List authorized booking requests",
            description = "Without officeBuildingId, returns only the authenticated requester's requests. With officeBuildingId, requires current in-scope booking approval authority.")
    public ApiResponse<BookingRequestPageResponse> list(@Valid @ModelAttribute BookingRequestPageQuery query) {
        return ApiResponse.success("Booking requests retrieved", service.list(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an authorized booking request",
            description = "The requester may read their own request within current scope; an authorized reviewer may read requests in the reviewer's building scope.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Booking request is outside the authenticated user's scope")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Booking request was not found")
    public ApiResponse<BookingRequestResponse> get(@PathVariable UUID id) {
        return ApiResponse.success("Booking request retrieved", service.get(id));
    }
}
