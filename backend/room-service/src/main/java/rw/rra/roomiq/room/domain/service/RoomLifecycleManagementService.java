package rw.rra.roomiq.room.domain.service;

import org.postgresql.util.PGobject;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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

    public RoomLifecycleManagementService(RoomRepository roomRepository,
                                          RoomStatusHistoryRepository statusHistoryRepository,
                                          MaintenancePeriodRepository maintenancePeriodRepository,
                                          RoomMapper roomMapper,
                                          RoomPhotoAuthorizationClient authorizationClient) {
        this.roomRepository = roomRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.maintenancePeriodRepository = maintenancePeriodRepository;
        this.roomMapper = roomMapper;
        this.authorizationClient = authorizationClient;
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
        roomRepository.saveAndFlush(room);
        RoomStatusHistory history = statusHistoryRepository.saveAndFlush(new RoomStatusHistory(
            room, previousStatus, request.status(), actorUserId, request.reason().trim()));
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

        try {
            MaintenancePeriod period = new MaintenancePeriod(room, range(request.startsAt(), request.endsAt()),
                    request.reason() == null ? null : request.reason().trim(), actorUserId);
            return roomMapper.toDto(maintenancePeriodRepository.saveAndFlush(period));
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "ROOM_MAINTENANCE_PERIOD_OVERLAP",
                    "Maintenance periods for the same room cannot overlap");
        }
    }

    @Transactional
    public void deleteMaintenancePeriod(UUID roomId, UUID maintenancePeriodId) {
        Room room = requireActiveRoomForUpdate(roomId);
        authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        MaintenancePeriod period = maintenancePeriodRepository.findByIdAndRoom_Id(maintenancePeriodId, roomId)
                .orElseThrow(() -> notFound("ROOM_MAINTENANCE_PERIOD_NOT_FOUND", "Maintenance period"));
        maintenancePeriodRepository.delete(period);
        maintenancePeriodRepository.flush();
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

    private static DomainException notFound(String code, String label) {
        return new DomainException(HttpStatus.NOT_FOUND, code, label + " not found");
    }
}