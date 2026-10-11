package rw.rra.roomiq.booking.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.rra.roomiq.booking.domain.entity.MeetingParticipant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, UUID> {
    List<MeetingParticipant> findAllByMeeting_IdOrderById(UUID meetingId);
    Optional<MeetingParticipant> findByIdAndMeeting_Id(UUID participantId, UUID meetingId);
    boolean existsByMeeting_IdAndUserId(UUID meetingId, UUID userId);
    boolean existsByMeeting_IdAndExternalEmailIgnoreCase(UUID meetingId, String externalEmail);
}
