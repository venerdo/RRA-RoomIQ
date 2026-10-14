package rw.rra.roomiq.booking.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.booking.domain.dto.RoomEventRecipientsRequest;
import rw.rra.roomiq.booking.domain.dto.RoomEventRecipientsResponse;
import rw.rra.roomiq.booking.domain.repository.ReservationOccurrenceRepository;
import rw.rra.roomiq.booking.integration.BookingAuthorizationClient;

@Service
public class BookingRoomEventRecipientsService {
    private final BookingAuthorizationClient authorizationClient;
    private final ReservationOccurrenceRepository occurrenceRepository;

    public BookingRoomEventRecipientsService(BookingAuthorizationClient authorizationClient,
                                             ReservationOccurrenceRepository occurrenceRepository) {
        this.authorizationClient = authorizationClient;
        this.occurrenceRepository = occurrenceRepository;
    }

    @Transactional(readOnly = true)
    public RoomEventRecipientsResponse resolve(RoomEventRecipientsRequest request) {
        authorizationClient.authorizeRoomManagement(request.officeBuildingId());
        return new RoomEventRecipientsResponse("v1", request.roomId(),
                occurrenceRepository.findActiveInternalRecipientsForRoomEvent(
                        request.roomId(), request.officeBuildingId(), request.startsAt(), request.endsAt()));
    }
}
