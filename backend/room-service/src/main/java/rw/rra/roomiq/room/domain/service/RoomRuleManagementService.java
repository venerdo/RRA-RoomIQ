package rw.rra.roomiq.room.domain.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.room.domain.dto.RoomRuleDto;
import rw.rra.roomiq.room.domain.dto.SetRoomRuleRequest;
import rw.rra.roomiq.room.domain.entity.Room;
import rw.rra.roomiq.room.domain.entity.RoomRule;
import rw.rra.roomiq.room.domain.entity.RoomRuleAllowedDepartment;
import rw.rra.roomiq.room.domain.mapper.RoomMapper;
import rw.rra.roomiq.room.domain.repository.RoomRepository;
import rw.rra.roomiq.room.domain.repository.RoomRuleAllowedDepartmentRepository;
import rw.rra.roomiq.room.domain.repository.RoomRuleRepository;
import rw.rra.roomiq.room.integration.OrganizationDirectoryClient;
import rw.rra.roomiq.room.integration.RoomPhotoAuthorizationClient;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RoomRuleManagementService {
    private static final int DEFAULT_RELEASE_BUFFER_MINUTES = 5;

    private final RoomRepository roomRepository;
    private final RoomRuleRepository roomRuleRepository;
    private final RoomRuleAllowedDepartmentRepository allowedDepartmentRepository;
    private final RoomMapper roomMapper;
    private final OrganizationDirectoryClient organizationDirectoryClient;
    private final RoomPhotoAuthorizationClient authorizationClient;

    public RoomRuleManagementService(RoomRepository roomRepository,
                                     RoomRuleRepository roomRuleRepository,
                                     RoomRuleAllowedDepartmentRepository allowedDepartmentRepository,
                                     RoomMapper roomMapper,
                                     OrganizationDirectoryClient organizationDirectoryClient,
                                     RoomPhotoAuthorizationClient authorizationClient) {
        this.roomRepository = roomRepository;
        this.roomRuleRepository = roomRuleRepository;
        this.allowedDepartmentRepository = allowedDepartmentRepository;
        this.roomMapper = roomMapper;
        this.organizationDirectoryClient = organizationDirectoryClient;
        this.authorizationClient = authorizationClient;
    }

    public List<RoomRuleDto> listForRoom(UUID roomId) {
        requireRoom(roomId);
        return roomRuleRepository.findAllByRoom_IdOrderByEffectiveFromDesc(roomId)
                .stream().map(this::toDto).toList();
    }

    public List<RoomRuleDto> listBuildingDefaults(UUID officeBuildingId) {
        organizationDirectoryClient.validateOfficeBuilding(officeBuildingId);
        return roomRuleRepository.findAllByRoomIsNullAndOfficeBuildingIdOrderByEffectiveFromDesc(officeBuildingId)
                .stream().map(this::toDto).toList();
    }

    public RoomRuleDto get(UUID ruleId) {
        return toDto(requireRule(ruleId));
    }

    @Transactional
    public RoomRuleDto createForRoom(UUID roomId, SetRoomRuleRequest request) {
        Room room = requireRoom(roomId);
        authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        validateRequest(request);
        validateDepartments(request.allowedDepartmentIds(), room.getOfficeBuildingId());
        RoomRule rule = new RoomRule(room, null, request.minDurationMinutes(), request.maxDurationMinutes(),
                request.minAdvanceMinutes(), request.maxAdvanceDays(), request.cancellationDeadlineMinutes(),
                request.recurringAllowed(), request.externalGuestsAllowed(), request.approvalRequired(),
                request.outsideHoursAllowed(), releaseBuffer(request), request.active(), request.effectiveFrom());
        return save(rule, request.allowedDepartmentIds());
    }

    @Transactional
    public RoomRuleDto createBuildingDefault(UUID officeBuildingId, SetRoomRuleRequest request) {
        authorizationClient.authorizeRoomManagement(officeBuildingId);
        organizationDirectoryClient.validateOfficeBuilding(officeBuildingId);
        validateRequest(request);
        validateDepartments(request.allowedDepartmentIds(), officeBuildingId);
        RoomRule rule = new RoomRule(null, officeBuildingId, request.minDurationMinutes(),
                request.maxDurationMinutes(), request.minAdvanceMinutes(), request.maxAdvanceDays(),
                request.cancellationDeadlineMinutes(), request.recurringAllowed(), request.externalGuestsAllowed(),
                request.approvalRequired(), request.outsideHoursAllowed(), releaseBuffer(request),
                request.active(), request.effectiveFrom());
        return save(rule, request.allowedDepartmentIds());
    }

    @Transactional
    public RoomRuleDto update(UUID ruleId, SetRoomRuleRequest request) {
        RoomRule rule = requireRule(ruleId);
        UUID buildingId;
        if (rule.getRoom() == null) {
            buildingId = rule.getOfficeBuildingId();
        } else {
            Room room = requireRoom(rule.getRoom().getId());
            buildingId = room.getOfficeBuildingId();
        }
        authorizationClient.authorizeRoomManagement(buildingId);
        if (rule.getRoom() == null) {
            organizationDirectoryClient.validateOfficeBuilding(buildingId);
        }
        validateRequest(request);
        validateDepartments(request.allowedDepartmentIds(), buildingId);

        rule.updateDetails(request.minDurationMinutes(), request.maxDurationMinutes(),
                request.minAdvanceMinutes(), request.maxAdvanceDays(), request.cancellationDeadlineMinutes(),
                request.recurringAllowed(), request.externalGuestsAllowed(), request.approvalRequired(),
                request.outsideHoursAllowed(), releaseBuffer(request), request.active(), request.effectiveFrom());
        return save(rule, request.allowedDepartmentIds());
    }

    private RoomRuleDto save(RoomRule rule, List<UUID> departmentIds) {
        RoomRule savedRule = roomRuleRepository.saveAndFlush(rule);
        allowedDepartmentRepository.deleteAllByRoomRule_Id(savedRule.getId());
        allowedDepartmentRepository.saveAllAndFlush(departmentIds.stream()
                .map(departmentId -> new RoomRuleAllowedDepartment(savedRule, departmentId))
                .toList());
        return roomMapper.toDto(savedRule, departmentIds);
    }

    private RoomRuleDto toDto(RoomRule rule) {
        List<UUID> departmentIds = allowedDepartmentRepository.findAllByRoomRule_IdOrderByDepartmentIdAsc(rule.getId())
                .stream().map(RoomRuleAllowedDepartment::getDepartmentId).toList();
        return roomMapper.toDto(rule, departmentIds);
    }

    private void validateDepartments(List<UUID> departmentIds, UUID officeBuildingId) {
        if (new HashSet<>(departmentIds).size() != departmentIds.size()) {
            throw invalid("ROOM_RULE_DUPLICATE_DEPARTMENT", "Allowed department IDs must be unique");
        }
        departmentIds.forEach(departmentId ->
                organizationDirectoryClient.validateDepartment(departmentId, officeBuildingId));
    }

    private static void validateRequest(SetRoomRuleRequest request) {
        if (request.minDurationMinutes() != null && request.maxDurationMinutes() != null
                && request.minDurationMinutes() > request.maxDurationMinutes()) {
            throw invalid("ROOM_RULE_DURATION_BOUNDS", "Minimum duration cannot exceed maximum duration");
        }
        if (request.minAdvanceMinutes() != null && request.maxAdvanceDays() != null
                && request.minAdvanceMinutes() > (long) request.maxAdvanceDays() * 24 * 60) {
            throw invalid("ROOM_RULE_ADVANCE_BOUNDS", "Minimum advance cannot exceed maximum advance");
        }
    }

    private static int releaseBuffer(SetRoomRuleRequest request) {
        return request.releaseBufferMinutes() == null
                ? DEFAULT_RELEASE_BUFFER_MINUTES
                : request.releaseBufferMinutes();
    }

    private Room requireRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room"));
        if (room.getDeletedAt() != null) {
            throw notFound("ROOM_NOT_FOUND", "Room");
        }
        return room;
    }

    private RoomRule requireRule(UUID ruleId) {
        return roomRuleRepository.findById(ruleId)
                .orElseThrow(() -> notFound("ROOM_RULE_NOT_FOUND", "Room rule"));
    }

    private static DomainException notFound(String code, String label) {
        return new DomainException(HttpStatus.NOT_FOUND, code, label + " not found");
    }

    private static DomainException invalid(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }
}