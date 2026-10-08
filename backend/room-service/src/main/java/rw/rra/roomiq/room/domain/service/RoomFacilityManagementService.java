package rw.rra.roomiq.room.domain.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.room.domain.dto.RoomFacilityDto;
import rw.rra.roomiq.room.domain.dto.SetRoomFacilityRequest;
import rw.rra.roomiq.room.domain.entity.FacilityType;
import rw.rra.roomiq.room.domain.entity.Room;
import rw.rra.roomiq.room.domain.entity.RoomFacility;
import rw.rra.roomiq.room.domain.mapper.RoomMapper;
import rw.rra.roomiq.room.domain.repository.FacilityTypeRepository;
import rw.rra.roomiq.room.domain.repository.RoomFacilityRepository;
import rw.rra.roomiq.room.domain.repository.RoomRepository;
import rw.rra.roomiq.room.integration.RoomPhotoAuthorizationClient;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RoomFacilityManagementService {
    private final RoomRepository roomRepository;
    private final FacilityTypeRepository facilityTypeRepository;
    private final RoomFacilityRepository roomFacilityRepository;
    private final RoomMapper roomMapper;
    private final RoomPhotoAuthorizationClient authorizationClient;

    public RoomFacilityManagementService(RoomRepository roomRepository,
                                         FacilityTypeRepository facilityTypeRepository,
                                         RoomFacilityRepository roomFacilityRepository,
                                         RoomMapper roomMapper,
                                         RoomPhotoAuthorizationClient authorizationClient) {
        this.roomRepository = roomRepository;
        this.facilityTypeRepository = facilityTypeRepository;
        this.roomFacilityRepository = roomFacilityRepository;
        this.roomMapper = roomMapper;
        this.authorizationClient = authorizationClient;
    }

    public List<RoomFacilityDto> list(UUID roomId) {
        requireRoom(roomId);
        return roomFacilityRepository.findAllByRoom_IdOrderByFacilityType_NameAsc(roomId)
                .stream().map(roomMapper::toDto).toList();
    }

    @Transactional
    public RoomFacilityDto create(UUID roomId, SetRoomFacilityRequest request) {
        Room room = requireRoom(roomId);
        authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        FacilityType facilityType = requireActiveFacilityType(request.facilityTypeId());
        if (roomFacilityRepository.existsByRoom_IdAndFacilityType_Id(roomId, facilityType.getId())) {
            throw duplicate();
        }

        RoomFacility assignment = new RoomFacility(room, facilityType, request.quantity().shortValue(),
                request.state(), request.lastServicedAt());
        try {
            return roomMapper.toDto(roomFacilityRepository.saveAndFlush(assignment));
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    @Transactional
    public RoomFacilityDto update(UUID roomId, UUID assignmentId, SetRoomFacilityRequest request) {
        Room room = requireRoom(roomId);
        authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        RoomFacility assignment = roomFacilityRepository.findByIdAndRoom_Id(assignmentId, roomId)
                .orElseThrow(() -> notFound("ROOM_FACILITY_NOT_FOUND", "Room facility assignment"));
        FacilityType facilityType = requireActiveFacilityType(request.facilityTypeId());
        if (!facilityType.getId().equals(assignment.getFacilityType().getId())
                && roomFacilityRepository.existsByRoom_IdAndFacilityType_IdAndIdNot(
                        roomId, facilityType.getId(), assignmentId)) {
            throw duplicate();
        }

        assignment.updateDetails(facilityType, request.quantity().shortValue(), request.state(),
                request.lastServicedAt());
        try {
            return roomMapper.toDto(roomFacilityRepository.saveAndFlush(assignment));
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private Room requireRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room"));
        if (room.getDeletedAt() != null) {
            throw notFound("ROOM_NOT_FOUND", "Room");
        }
        return room;
    }

    private FacilityType requireActiveFacilityType(UUID facilityTypeId) {
        FacilityType facilityType = facilityTypeRepository.findById(facilityTypeId)
                .orElseThrow(() -> notFound("FACILITY_TYPE_NOT_FOUND", "Facility type"));
        if (!facilityType.isActive()) {
            throw new DomainException(HttpStatus.CONFLICT, "FACILITY_TYPE_INACTIVE",
                    "Inactive facility types cannot be assigned");
        }
        return facilityType;
    }

    private static DomainException notFound(String code, String label) {
        return new DomainException(HttpStatus.NOT_FOUND, code, label + " not found");
    }

    private static DomainException duplicate() {
        return new DomainException(HttpStatus.CONFLICT, "ROOM_FACILITY_DUPLICATE",
                "This facility type is already assigned to the room");
    }
}