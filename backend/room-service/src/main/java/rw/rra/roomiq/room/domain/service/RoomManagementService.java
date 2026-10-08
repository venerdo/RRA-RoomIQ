package rw.rra.roomiq.room.domain.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.room.domain.dto.CatalogPageResponse;
import rw.rra.roomiq.room.domain.dto.CreateFacilityTypeRequest;
import rw.rra.roomiq.room.domain.dto.CreateRoomRequest;
import rw.rra.roomiq.room.domain.dto.CreateRoomTypeRequest;
import rw.rra.roomiq.room.domain.dto.FacilityTypeDto;
import rw.rra.roomiq.room.domain.dto.FacilityTypePageQuery;
import rw.rra.roomiq.room.domain.dto.RoomDto;
import rw.rra.roomiq.room.domain.dto.RoomPageQuery;
import rw.rra.roomiq.room.domain.dto.RoomTypeDto;
import rw.rra.roomiq.room.domain.dto.RoomTypePageQuery;
import rw.rra.roomiq.room.domain.entity.FacilityType;
import rw.rra.roomiq.room.domain.entity.Room;
import rw.rra.roomiq.room.domain.entity.RoomClass;
import rw.rra.roomiq.room.domain.entity.RoomStatus;
import rw.rra.roomiq.room.domain.entity.RoomType;
import rw.rra.roomiq.room.domain.mapper.RoomMapper;
import rw.rra.roomiq.room.domain.repository.FacilityTypeRepository;
import rw.rra.roomiq.room.domain.repository.RoomRepository;
import rw.rra.roomiq.room.domain.repository.RoomTypeRepository;
import rw.rra.roomiq.room.integration.OrganizationDirectoryClient;
import rw.rra.roomiq.room.integration.RoomPhotoAuthorizationClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
public class RoomManagementService {
    private final RoomTypeRepository roomTypeRepository;
    private final FacilityTypeRepository facilityTypeRepository;
    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;
    private final OrganizationDirectoryClient organizationDirectoryClient;
    private final RoomPhotoAuthorizationClient authorizationClient;

    public RoomManagementService(RoomTypeRepository roomTypeRepository,
                                FacilityTypeRepository facilityTypeRepository,
                                RoomRepository roomRepository,
                                RoomMapper roomMapper,
                                OrganizationDirectoryClient organizationDirectoryClient,
                                RoomPhotoAuthorizationClient authorizationClient) {
        this.roomTypeRepository = roomTypeRepository;
        this.facilityTypeRepository = facilityTypeRepository;
        this.roomRepository = roomRepository;
        this.roomMapper = roomMapper;
        this.organizationDirectoryClient = organizationDirectoryClient;
        this.authorizationClient = authorizationClient;
    }

    public CatalogPageResponse<RoomTypeDto> listRoomTypes(RoomTypePageQuery query) {
        Specification<RoomType> specification = (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.active() != null) {
                predicates.add(builder.equal(root.get("active"), query.active()));
            }
            if (query.searchTerm() != null) {
                String pattern = likePattern(query.searchTerm());
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, '\\'),
                        builder.like(builder.lower(root.get("code")), pattern, '\\')));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        Page<RoomType> page = roomTypeRepository.findAll(specification,
                pageRequest(query, Set.of("code", "name", "active", "createdAt"), "name"));
        return toPageResponse(page, query.sortBy(), query.sortDirection(), roomMapper::toDto);
    }

    public RoomTypeDto getRoomType(UUID id) {
        return roomMapper.toDto(requireRoomType(id));
    }

    @Transactional
    public RoomTypeDto createRoomType(CreateRoomTypeRequest request) {
        authorizationClient.authorizeRoomManagement(null);
        String normalizedCode = normalizeCode(request.code());
        String normalizedName = normalizeName(request.name());
        if (roomTypeRepository.findByCodeIgnoreCase(normalizedCode).isPresent()) {
            throw duplicate("ROOM_TYPE_DUPLICATE", "Room type code already exists");
        }
        RoomType roomType = new RoomType(normalizedCode, normalizedName, request.activeFlag());
        return roomMapper.toDto(roomTypeRepository.saveAndFlush(roomType));
    }

    @Transactional
    public RoomTypeDto updateRoomType(UUID id, CreateRoomTypeRequest request) {
        authorizationClient.authorizeRoomManagement(null);
        RoomType roomType = requireRoomType(id);
        String normalizedCode = normalizeCode(request.code());
        if (!normalizedCode.equalsIgnoreCase(roomType.getCode())
                && roomTypeRepository.existsByCodeIgnoreCaseAndIdNot(normalizedCode, id)) {
            throw duplicate("ROOM_TYPE_DUPLICATE", "Room type code already exists");
        }
        roomType.update(normalizedCode, normalizeName(request.name()), request.activeFlag());
        return roomMapper.toDto(roomTypeRepository.saveAndFlush(roomType));
    }

    public CatalogPageResponse<FacilityTypeDto> listFacilityTypes(FacilityTypePageQuery query) {
        Specification<FacilityType> specification = (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.active() != null) {
                predicates.add(builder.equal(root.get("active"), query.active()));
            }
            if (query.searchTerm() != null) {
                String pattern = likePattern(query.searchTerm());
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, '\\'),
                        builder.like(builder.lower(root.get("code")), pattern, '\\'),
                        builder.like(builder.lower(root.get("category")), pattern, '\\')));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        Page<FacilityType> page = facilityTypeRepository.findAll(specification,
                pageRequest(query, Set.of("code", "name", "category", "active", "createdAt"), "name"));
        return toPageResponse(page, query.sortBy(), query.sortDirection(), roomMapper::toDto);
    }

    public FacilityTypeDto getFacilityType(UUID id) {
        return roomMapper.toDto(requireFacilityType(id));
    }

    @Transactional
    public FacilityTypeDto createFacilityType(CreateFacilityTypeRequest request) {
        authorizationClient.authorizeRoomManagement(null);
        String normalizedCode = normalizeCode(request.code());
        if (facilityTypeRepository.findByCodeIgnoreCase(normalizedCode).isPresent()) {
            throw duplicate("FACILITY_TYPE_DUPLICATE", "Facility type code already exists");
        }
        String category = request.category() == null ? null : request.category().trim();
        FacilityType facilityType = new FacilityType(normalizedCode, normalizeName(request.name()), category,
                request.activeFlag());
        return roomMapper.toDto(facilityTypeRepository.saveAndFlush(facilityType));
    }

    @Transactional
    public FacilityTypeDto updateFacilityType(UUID id, CreateFacilityTypeRequest request) {
        authorizationClient.authorizeRoomManagement(null);
        FacilityType facilityType = requireFacilityType(id);
        String normalizedCode = normalizeCode(request.code());
        if (!normalizedCode.equalsIgnoreCase(facilityType.getCode())
                && facilityTypeRepository.existsByCodeIgnoreCaseAndIdNot(normalizedCode, id)) {
            throw duplicate("FACILITY_TYPE_DUPLICATE", "Facility type code already exists");
        }
        String category = request.category() == null ? facilityType.getCategory() : request.category().trim();
        facilityType.update(normalizedCode, normalizeName(request.name()), category, request.activeFlag());
        return roomMapper.toDto(facilityTypeRepository.saveAndFlush(facilityType));
    }

    public CatalogPageResponse<RoomDto> listRooms(RoomPageQuery query) {
        Specification<Room> specification = (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.roomTypeId() != null) {
                predicates.add(builder.equal(root.get("roomType").get("id"), query.roomTypeId()));
            }
            if (query.officeBuildingId() != null) {
                predicates.add(builder.equal(root.get("officeBuildingId"), query.officeBuildingId()));
            }
            if (query.floorId() != null) {
                predicates.add(builder.equal(root.get("floorId"), query.floorId()));
            }
            if (query.roomClass() != null) {
                predicates.add(builder.equal(root.get("roomClass"), query.roomClass()));
            }
            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }
            predicates.add(builder.isNull(root.get("deletedAt")));
            if (query.searchTerm() != null) {
                String pattern = likePattern(query.searchTerm());
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, '\\'),
                        builder.like(builder.lower(root.get("code")), pattern, '\\')));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        Page<Room> page = roomRepository.findAll(specification,
                pageRequest(query, Set.of("name", "code", "capacity", "status", "roomClass", "createdAt"), "code"));
        return toPageResponse(page, query.sortBy(), query.sortDirection(), roomMapper::toDto);
    }

    public RoomDto getRoom(UUID id) {
        return roomMapper.toDto(requireRoom(id));
    }

    @Transactional
    public RoomDto createRoom(CreateRoomRequest request) {
        authorizationClient.authorizeRoomManagement(request.officeBuildingId());
        RoomType roomType = resolveRoomType(request.roomTypeId(), request.roomTypeCode());
        String normalizedName = normalizeName(request.name());
        String normalizedCode = normalizeCode(request.code());
        organizationDirectoryClient.validateBuildingAndFloor(request.officeBuildingId(), request.floorId());
        if (roomRepository.existsByOfficeBuildingIdAndNameIgnoreCase(request.officeBuildingId(), normalizedName)) {
            throw duplicate("ROOM_NAME_DUPLICATE", "Room name already exists in this building");
        }
        if (roomRepository.existsByOfficeBuildingIdAndCodeIgnoreCase(request.officeBuildingId(), normalizedCode)) {
            throw duplicate("ROOM_CODE_DUPLICATE", "Room code already exists in this building");
        }
        if (request.capacity() <= 0) {
            throw invalid("ROOM_CAPACITY_INVALID", "Room capacity must be positive");
        }
        Room room = new Room(roomType, request.floorId(), request.officeBuildingId(), normalizedName, normalizedCode,
                request.description() == null ? null : request.description().trim(), request.capacity(),
                request.roomClass(), request.status());
        return roomMapper.toDto(roomRepository.saveAndFlush(room));
    }

    @Transactional
    public RoomDto updateRoom(UUID id, CreateRoomRequest request) {
        Room room = requireRoom(id);
        authorizationClient.authorizeRoomManagement(room.getOfficeBuildingId());
        if (!room.getOfficeBuildingId().equals(request.officeBuildingId())) {
            authorizationClient.authorizeRoomManagement(request.officeBuildingId());
        }
        if (room.getStatus() == RoomStatus.DECOMMISSIONED) {
            throw new DomainException(HttpStatus.CONFLICT, "ROOM_DECOMMISSIONED",
                    "A decommissioned room cannot be updated");
        }
        if (request.status() != room.getStatus()) {
            throw new DomainException(HttpStatus.CONFLICT, "ROOM_STATUS_CHANGE_REQUIRED",
                    "Room status must be changed through the status lifecycle endpoint");
        }
        RoomType roomType = resolveRoomType(request.roomTypeId(), request.roomTypeCode());
        String normalizedName = normalizeName(request.name());
        String normalizedCode = normalizeCode(request.code());
        organizationDirectoryClient.validateBuildingAndFloor(request.officeBuildingId(), request.floorId());
        if ((!request.officeBuildingId().equals(room.getOfficeBuildingId())
            || !normalizedName.equalsIgnoreCase(room.getName()))
            && roomRepository.existsByOfficeBuildingIdAndNameIgnoreCaseAndIdNot(
                request.officeBuildingId(), normalizedName, id)) {
            throw duplicate("ROOM_NAME_DUPLICATE", "Room name already exists in this building");
        }
        if ((!request.officeBuildingId().equals(room.getOfficeBuildingId())
            || !normalizedCode.equalsIgnoreCase(room.getCode()))
            && roomRepository.existsByOfficeBuildingIdAndCodeIgnoreCaseAndIdNot(
                request.officeBuildingId(), normalizedCode, id)) {
            throw duplicate("ROOM_CODE_DUPLICATE", "Room code already exists in this building");
        }
        if (request.capacity() <= 0) {
            throw invalid("ROOM_CAPACITY_INVALID", "Room capacity must be positive");
        }

        room.updateDetails(roomType, request.floorId(), request.officeBuildingId(), normalizedName,
                normalizedCode, request.description() == null ? null : request.description().trim(),
                request.capacity(), request.roomClass(), request.status());
        return roomMapper.toDto(roomRepository.saveAndFlush(room));
    }

    private static <E, T> CatalogPageResponse<T> toPageResponse(Page<E> page, String sortBy, String sortDirection,
                                                                Function<E, T> mapper) {
        return new CatalogPageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                sortBy, sortDirection == null ? "ASC" : sortDirection.toUpperCase(Locale.ROOT));
    }

    private static PageRequest pageRequest(RoomTypePageQuery query, Set<String> allowedSorts, String defaultSort) {
        String sortBy = resolveSortBy(query.sortBy(), allowedSorts, defaultSort);
        Sort.Direction direction = query.sortDirection() == null
                ? Sort.Direction.ASC
                : Sort.Direction.fromString(query.sortDirection());
        return PageRequest.of(query.pageIndex(), query.pageSize(), Sort.by(direction, sortBy));
    }

    private static PageRequest pageRequest(FacilityTypePageQuery query, Set<String> allowedSorts, String defaultSort) {
        String sortBy = resolveSortBy(query.sortBy(), allowedSorts, defaultSort);
        Sort.Direction direction = query.sortDirection() == null
                ? Sort.Direction.ASC
                : Sort.Direction.fromString(query.sortDirection());
        return PageRequest.of(query.pageIndex(), query.pageSize(), Sort.by(direction, sortBy));
    }

    private static PageRequest pageRequest(RoomPageQuery query, Set<String> allowedSorts, String defaultSort) {
        String sortBy = resolveSortBy(query.sortBy(), allowedSorts, defaultSort);
        Sort.Direction direction = query.sortDirection() == null
                ? Sort.Direction.ASC
                : Sort.Direction.fromString(query.sortDirection());
        return PageRequest.of(query.pageIndex(), query.pageSize(), Sort.by(direction, sortBy));
    }

    private static String resolveSortBy(String sortBy, Set<String> allowedSorts, String defaultSort) {
        String normalized = sortBy == null ? defaultSort : sortBy.trim();
        if (!allowedSorts.contains(normalized)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD",
                    "Sort field must be one of: " + String.join(", ", allowedSorts));
        }
        return normalized;
    }

    private static String likePattern(String text) {
        return "%" + text.toLowerCase(Locale.ROOT).replace("%", "\\%") + "%";
    }

    private RoomType resolveRoomType(UUID roomTypeId, String roomTypeCode) {
        if (roomTypeId != null) {
            return requireRoomType(roomTypeId);
        }
        if (roomTypeCode != null && !roomTypeCode.isBlank()) {
            return roomTypeRepository.findByCodeIgnoreCase(normalizeCode(roomTypeCode))
                    .orElseThrow(() -> notFound("ROOM_TYPE_NOT_FOUND", "Room type"));
        }
        throw invalid("ROOM_TYPE_REQUIRED", "Room type is required");
    }

    private RoomType requireRoomType(UUID id) {
        return roomTypeRepository.findById(id).orElseThrow(() -> notFound("ROOM_TYPE_NOT_FOUND", "Room type"));
    }

    private FacilityType requireFacilityType(UUID id) {
        return facilityTypeRepository.findById(id).orElseThrow(() -> notFound("FACILITY_TYPE_NOT_FOUND", "Facility type"));
    }

    private Room requireRoom(UUID id) {
        Room room = roomRepository.findById(id).orElseThrow(() -> notFound("ROOM_NOT_FOUND", "Room"));
        if (room.getDeletedAt() != null) {
            throw notFound("ROOM_NOT_FOUND", "Room");
        }
        return room;
    }

    private static String normalizeCode(String value) {
        if (value == null) {
            throw invalid("INVALID_CODE", "Code is required");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw invalid("INVALID_CODE", "Code is required");
        }
        return normalized;
    }

    private static String normalizeName(String value) {
        if (value == null) {
            throw invalid("INVALID_NAME", "Name is required");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw invalid("INVALID_NAME", "Name is required");
        }
        return normalized;
    }

    private static DomainException notFound(String code, String label) {
        return new DomainException(HttpStatus.NOT_FOUND, code, label + " not found");
    }

    private static DomainException duplicate(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    private static DomainException invalid(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }
}
