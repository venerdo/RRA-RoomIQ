package rw.rra.roomiq.room.domain.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.room.domain.dto.ChangeRoomStatusRequest;
import rw.rra.roomiq.room.domain.dto.CreateMaintenancePeriodRequest;
import rw.rra.roomiq.room.domain.dto.MaintenancePeriodDto;
import rw.rra.roomiq.room.domain.dto.RoomStatusHistoryDto;
import rw.rra.roomiq.room.domain.entity.MaintenancePeriod;
import rw.rra.roomiq.room.domain.entity.Room;
import rw.rra.roomiq.room.domain.entity.RoomStatus;
import rw.rra.roomiq.room.domain.entity.RoomStatusHistory;
import rw.rra.roomiq.room.domain.mapper.RoomMapper;
import rw.rra.roomiq.room.domain.repository.MaintenancePeriodRepository;
import rw.rra.roomiq.room.domain.repository.RoomRepository;
import rw.rra.roomiq.room.domain.repository.RoomStatusHistoryRepository;
import rw.rra.roomiq.room.integration.BookingEventRecipientClient;
import rw.rra.roomiq.room.integration.RoomPhotoAuthorizationClient;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RoomLifecycleManagementService {
    private final RoomRepository roomRepository;
    private final RoomStatusHistoryRepository statusHistoryRepository;
    private final MaintenancePeriodRepository maintenancePeriodRepository;
    private final RoomMapper roomMapper;
    private final RoomPhotoAuthorizationClient authorizationClient;
    private final RoomEventOutboxService eventOutbox;
    private final BookingEventRecipientClient bookingEventRecipientClient;
    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public RoomLifecycleManagementService(RoomRepository roomRepository,
                                          RoomStatusHistoryRepository statusHistoryRepository,
                                          MaintenancePeriodRepository maintenancePeriodRepository,
                                          RoomMapper roomMapper,
                                          RoomPhotoAuthorizationClient authorizationClient,
                                          RoomEventOutboxService eventOutbox,
                                          BookingEventRecipientClient bookingEventRecipientClient,
                                          JdbcTemplate jdbcTemplate) {
        this.roomRepository = roomRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.maintenancePeriodRepository = maintenancePeriodRepository;
        this.roomMapper = roomMapper;
        this.authorizationClient = authorizationClient;
        this.eventOutbox = eventOutbox;
        this.bookingEventRecipientClient = bookingEventRecipientClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RoomStatusHistoryDto> listStatusHistory(UUID roomId) {
        roomRepository.findById(roomId)
            .orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room"));
        return statusHistoryRepository.findAllByRoom_IdOrderByChangedAtAsc(roomId).stream()
                .map(roomMapper::toDto).toList();
    }

    @Transactional
    public RoomStatusHistoryDto changeStatus(UUID roomId, ChangeRoomStatusRequest request) {
        Room room = requireActiveRoomForUpdate(roomId);
        UUID actorUserId = authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        RoomStatus previousStatus = room.getStatus();
        try {
            room.changeStatus(request.status());
        } catch (IllegalArgumentException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "ROOM_STATUS_TRANSITION_INVALID",
                    "Room status cannot transition from " + previousStatus + " to " + request.status());
        }
        Instant occurredAt = Instant.now();
        List<UUID> recipients = bookingEventRecipientClient.resolveRoomEventRecipients(
                room.getId(), room.getOfficeBuildingId(), occurredAt, null);
        roomRepository.saveAndFlush(room);
        RoomStatusHistory history = statusHistoryRepository.saveAndFlush(new RoomStatusHistory(
            room, previousStatus, request.status(), actorUserId, request.reason().trim()));
        eventOutbox.recordStatusChanged(room.getId(), room.getVersion(), request.status().name(), occurredAt, recipients);
        return roomMapper.toDto(history);
    }

    @Transactional
    public void softDelete(UUID roomId) {
        Room room = requireActiveRoomForUpdate(roomId);
        authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        room.softDelete(Instant.now());
        roomRepository.saveAndFlush(room);
    }

    public List<MaintenancePeriodDto> listMaintenancePeriods(UUID roomId) {
        requireActiveRoom(roomId);
        return maintenancePeriodRepository.findAllByRoomIdOrderByStart(roomId).stream()
                .map(roomMapper::toDto).toList();
    }

    @Transactional
    public MaintenancePeriodDto createMaintenancePeriod(UUID roomId, CreateMaintenancePeriodRequest request) {
        Room room = requireActiveRoomForUpdate(roomId);
        UUID actorUserId = authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "ROOM_MAINTENANCE_PERIOD_INVALID",
                    "Maintenance period end must be after its start");
        }
        if (room.getStatus() == RoomStatus.DECOMMISSIONED) {
            throw new DomainException(HttpStatus.CONFLICT, "ROOM_DECOMMISSIONED",
                    "Maintenance cannot be scheduled for a decommissioned room");
        }

        List<UUID> recipients = bookingEventRecipientClient.resolveRoomEventRecipients(
                room.getId(), room.getOfficeBuildingId(), request.startsAt(), request.endsAt());
        entityManager.lock(room, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
        MaintenancePeriod saved;
        try {
            MaintenancePeriod period = new MaintenancePeriod(room, range(request.startsAt(), request.endsAt()),
                    request.reason() == null ? null : request.reason().trim(), actorUserId);
            saved = maintenancePeriodRepository.saveAndFlush(period);
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "ROOM_MAINTENANCE_PERIOD_OVERLAP",
                    "Maintenance periods for the same room cannot overlap");
        }
        eventOutbox.recordMaintenanceChanged(room.getId(), room.getVersion(), saved.getId(), "SCHEDULED",
                request.startsAt(), request.endsAt(), Instant.now(), recipients);
        return roomMapper.toDto(saved);
    }

    @Transactional
    public void deleteMaintenancePeriod(UUID roomId, UUID maintenancePeriodId) {
        Room room = requireActiveRoomForUpdate(roomId);
        authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        MaintenancePeriod period = maintenancePeriodRepository.findByIdAndRoom_Id(maintenancePeriodId, roomId)
                .orElseThrow(() -> notFound("ROOM_MAINTENANCE_PERIOD_NOT_FOUND", "Maintenance period"));
        MaintenanceBounds bounds = jdbcTemplate.queryForObject("""
                SELECT lower(period), upper(period)
                FROM maintenance_period
                WHERE id = ?
                """, (resultSet, rowNum) -> new MaintenanceBounds(
                resultSet.getObject(1, java.time.OffsetDateTime.class).toInstant(),
                resultSet.getObject(2, java.time.OffsetDateTime.class).toInstant()), maintenancePeriodId);
        if (bounds == null) {
            throw notFound("ROOM_MAINTENANCE_PERIOD_NOT_FOUND", "Maintenance period");
        }
        List<UUID> recipients = bookingEventRecipientClient.resolveRoomEventRecipients(
                room.getId(), room.getOfficeBuildingId(), bounds.startsAt(), bounds.endsAt());
        entityManager.lock(room, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
        maintenancePeriodRepository.delete(period);
        maintenancePeriodRepository.flush();
        eventOutbox.recordMaintenanceChanged(roomId, room.getVersion(), maintenancePeriodId, "REMOVED",
                bounds.startsAt(), bounds.endsAt(), Instant.now(), recipients);
    }

    private Room requireActiveRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room"));
        if (room.getDeletedAt() != null) {
            throw notFound("ROOM_NOT_FOUND", "Room");
        }
        return room;
    }

    private Room requireActiveRoomForUpdate(UUID roomId) {
        return roomRepository.findActiveByIdForUpdate(roomId)
                .orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room"));
    }

    private static PGobject range(Instant startsAt, Instant endsAt) {
        try {
            PGobject period = new PGobject();
            period.setType("tstzrange");
            period.setValue("[" + startsAt + "," + endsAt + ")");
            return period;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to represent the maintenance period", exception);
        }
    }

    private record MaintenanceBounds(Instant startsAt, Instant endsAt) {
    }

    private static DomainException notFound(String code, String label) {
        return new DomainException(HttpStatus.NOT_FOUND, code, label + " not found");
    }
}