package rw.rra.roomiq.booking.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rw.rra.roomiq.booking.domain.dto.ReservationLifecycleResponse;
import rw.rra.roomiq.booking.domain.enums.ReservationStatus;
import rw.rra.roomiq.booking.domain.service.ReservationLifecycleService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationLifecycleControllerTests {
    @Mock
    private ReservationLifecycleService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationLifecycleController(service)).build();
    }

    @Test
    void checkInHasNoClientSuppliedActorOrTimestampAndReturnsServerState() throws Exception {
        UUID reservationId = UUID.randomUUID();
        Instant checkedInAt = Instant.parse("2026-10-09T21:00:00Z");
        when(service.checkIn(reservationId)).thenReturn(new ReservationLifecycleResponse(
                reservationId, ReservationStatus.IN_PROGRESS, checkedInAt, null));

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/check-in", reservationId)
                        .contentType("application/json")
                        .content("""
                                {"actorUserId":"%s","checkedInAt":"2000-01-01T00:00:00Z"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationId").value(reservationId.toString()))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.checkedInAt").value(checkedInAt.toString()))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());

        verify(service).checkIn(reservationId);
    }

    @Test
    void completionReturnsCompletionTimeAndPreservesCheckInTime() throws Exception {
        UUID reservationId = UUID.randomUUID();
        Instant checkedInAt = Instant.parse("2026-10-09T20:00:00Z");
        Instant completedAt = Instant.parse("2026-10-09T21:00:00Z");
        when(service.complete(reservationId)).thenReturn(new ReservationLifecycleResponse(
                reservationId, ReservationStatus.COMPLETED, checkedInAt, completedAt));

        mockMvc.perform(post("/api/v1/reservations/{reservationId}/complete", reservationId)
                        .contentType("application/json")
                        .content("""
                                {"actorUserId":"%s","completedAt":"2000-01-01T00:00:00Z"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.checkedInAt").value(checkedInAt.toString()))
                .andExpect(jsonPath("$.data.completedAt").value(completedAt.toString()));

        verify(service).complete(reservationId);
    }
}
