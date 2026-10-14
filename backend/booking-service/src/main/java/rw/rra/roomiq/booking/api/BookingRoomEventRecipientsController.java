package rw.rra.roomiq.booking.api;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.rra.roomiq.booking.domain.dto.RoomEventRecipientsRequest;
import rw.rra.roomiq.booking.domain.dto.RoomEventRecipientsResponse;
import rw.rra.roomiq.booking.domain.service.BookingRoomEventRecipientsService;
import rw.rra.roomiq.common.web.ApiResponse;

@RestController
@RequestMapping("/api/v1/internal/notifications")
@Tag(name = "Internal notification support")
@Hidden
@SecurityRequirement(name = "bearerAuth")
public class BookingRoomEventRecipientsController {
    private final BookingRoomEventRecipientsService recipientsService;

    public BookingRoomEventRecipientsController(BookingRoomEventRecipientsService recipientsService) {
        this.recipientsService = recipientsService;
    }

    @PostMapping("/room-event-recipients")
    public ApiResponse<RoomEventRecipientsResponse> recipients(
            @Valid @RequestBody RoomEventRecipientsRequest request) {
        return ApiResponse.success("Room event recipients resolved", recipientsService.resolve(request));
    }
}
