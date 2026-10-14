package rw.rra.roomiq.booking.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import rw.rra.roomiq.booking.domain.dto.RoomEventRecipientsRequest;
import rw.rra.roomiq.booking.domain.repository.ReservationOccurrenceRepository;
import rw.rra.roomiq.booking.integration.BookingAuthorizationClient;
import rw.rra.roomiq.common.web.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingRoomEventRecipientsServiceTests {
    @Mock
    private BookingAuthorizationClient authorizationClient;

    @Mock
    private ReservationOccurrenceRepository occurrenceRepository;

    @InjectMocks
    private BookingRoomEventRecipientsService service;

    @Test
    void requiresIdentityRoomManagementAuthorizationBeforeReadingRecipientData() {
        UUID roomId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        when(authorizationClient.authorizeRoomManagement(buildingId))
                .thenThrow(new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Denied"));

        assertThatThrownBy(() -> service.resolve(new RoomEventRecipientsRequest(roomId, buildingId,
                Instant.parse("2030-04-10T10:00:00Z"), null, "v1")))
                .isInstanceOf(DomainException.class);

        verify(authorizationClient).authorizeRoomManagement(buildingId);
        verifyNoInteractions(occurrenceRepository);
    }

    @Test
    void returnsTheAuthorizedRoomAudienceFromBookingOwnedData() {
        UUID roomId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Instant startsAt = Instant.parse("2030-04-10T10:00:00Z");
        Instant endsAt = Instant.parse("2030-04-10T11:00:00Z");
        when(occurrenceRepository.findActiveInternalRecipientsForRoomEvent(roomId, buildingId, startsAt, endsAt))
                .thenReturn(List.of(recipientId));

        var result = service.resolve(new RoomEventRecipientsRequest(roomId, buildingId, startsAt, endsAt, "v1"));

        assertThat(result.contractVersion()).isEqualTo("v1");
        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.recipientUserIds()).containsExactly(recipientId);
        verify(authorizationClient).authorizeRoomManagement(buildingId);
    }
}
