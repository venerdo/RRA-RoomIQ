package rw.rra.roomiq.booking.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rw.rra.roomiq.booking.domain.dto.DirectBookingResponse;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.enums.BookingRequestType;
import rw.rra.roomiq.booking.domain.service.BookingDirectBookingService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookingDirectBookingControllerTests {
    @Mock
    private BookingDirectBookingService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookingDirectBookingController(service)).build();
    }

    @Test
    void directBookingRouteReturnsPendingWhenRoomApprovalIsRequired() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(service.create(any(), isNull())).thenReturn(new DirectBookingResponse(
                requestId, "BR-direct", BookingRequestType.ADMIN_DIRECT_BOOKING,
                BookingRequestStatus.PENDING_APPROVAL, null, null, 2));

        mockMvc.perform(post("/api/v1/bookings/direct")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "departmentId":"%s",
                                  "roomId":"%s",
                                  "officeBuildingId":"%s",
                                  "title":"Planning meeting",
                                  "requestedStart":"2030-04-10T10:00:00Z",
                                  "requestedEnd":"2030-04-10T11:00:00Z",
                                  "attendeeCount":4
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.reservationId").doesNotExist())
                .andExpect(jsonPath("$.data.meetingId").doesNotExist())
                .andExpect(jsonPath("$.data.occurrenceCount").value(2));

        verify(service).create(any(), isNull());
    }

    @Test
    void directBookingPayloadCannotOverrideServerDerivedStateOrIdentity() throws Exception {
        when(service.create(any(), isNull())).thenReturn(new DirectBookingResponse(
                UUID.randomUUID(), "BR-direct", BookingRequestType.ADMIN_DIRECT_BOOKING,
                BookingRequestStatus.PENDING_APPROVAL, null, null, 1));

        mockMvc.perform(post("/api/v1/bookings/direct")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "departmentId":"%s",
                                  "roomId":"%s",
                                  "officeBuildingId":"%s",
                                  "title":"Planning meeting",
                                  "requestedStart":"2030-04-10T10:00:00Z",
                                  "requestedEnd":"2030-04-10T11:00:00Z",
                                  "attendeeCount":4,
                                  "requestedByUserId":"%s",
                                  "status":"APPROVED"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.requestedByUserId").doesNotExist());

        verify(service).create(any(), isNull());
    }
}
