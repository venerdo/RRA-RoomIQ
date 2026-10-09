package rw.rra.roomiq.booking.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rw.rra.roomiq.booking.domain.dto.BookingRequestPageResponse;
import rw.rra.roomiq.booking.domain.dto.BookingRequestResponse;
import rw.rra.roomiq.booking.domain.enums.BookingRequestStatus;
import rw.rra.roomiq.booking.domain.enums.BookingRequestType;
import rw.rra.roomiq.booking.domain.service.BookingRequestService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookingRequestControllerTests {
    @Mock
    private BookingRequestService service;

    private MockMvc mockMvc;
    private BookingRequestResponse requestResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookingRequestController(service)).build();
        requestResponse = new BookingRequestResponse(
                UUID.randomUUID(), "BR-test", BookingRequestType.SECRETARY_REQUEST, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "Planning meeting",
                null, Instant.parse("2026-10-10T10:00:00Z"), Instant.parse("2026-10-10T11:00:00Z"),
                6, false, BookingRequestStatus.PENDING_APPROVAL, 0, Instant.parse("2026-10-09T12:00:00Z"));
    }

    @Test
    void createEndpointAcceptsOnlyRequestDataAndReturnsServerDerivedState() throws Exception {
        when(service.create(any(), isNull())).thenReturn(requestResponse);

        mockMvc.perform(post("/api/v1/booking-requests")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "departmentId":"%s",
                                  "roomId":"%s",
                                  "officeBuildingId":"%s",
                                  "title":"Planning meeting",
                                  "requestedStart":"2026-10-10T10:00:00Z",
                                  "requestedEnd":"2026-10-10T11:00:00Z",
                                  "attendeeCount":6
                                }
                                """.formatted(requestResponse.departmentId(), requestResponse.roomId(),
                                requestResponse.officeBuildingId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestedByUserId")
                        .value(requestResponse.requestedByUserId().toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        verify(service).create(any(), isNull());
    }

    @Test
    void createEndpointRejectsInvalidIntervalsBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/booking-requests")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "departmentId":"%s",
                                  "roomId":"%s",
                                  "officeBuildingId":"%s",
                                  "title":"Planning meeting",
                                  "requestedStart":"2026-10-10T11:00:00Z",
                                  "requestedEnd":"2026-10-10T10:00:00Z",
                                  "attendeeCount":6
                                }
                                """.formatted(requestResponse.departmentId(), requestResponse.roomId(),
                                requestResponse.officeBuildingId())))
                .andExpect(status().isBadRequest());

        verify(service, never()).create(any(), any());
    }

    @Test
    void listAndGetEndpointsReturnTheServiceResponses() throws Exception {
        when(service.list(any())).thenReturn(new BookingRequestPageResponse(
                List.of(requestResponse), 0, 20, 1, 1));
        when(service.get(requestResponse.id())).thenReturn(requestResponse);

        mockMvc.perform(get("/api/v1/booking-requests").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(requestResponse.id().toString()));
        mockMvc.perform(get("/api/v1/booking-requests/{id}", requestResponse.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestReference").value("BR-test"));

        verify(service).list(any());
        verify(service).get(requestResponse.id());
    }

    @Test
    void submitEndpointDelegatesTheExplicitDraftTransition() throws Exception {
        when(service.submit(requestResponse.id())).thenReturn(requestResponse);

        mockMvc.perform(post("/api/v1/booking-requests/{id}/submit", requestResponse.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        verify(service).submit(requestResponse.id());
    }
}
