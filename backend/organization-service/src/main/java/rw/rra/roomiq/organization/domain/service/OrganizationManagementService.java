package rw.rra.roomiq.organization.domain.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.organization.domain.dto.CountryHierarchyResponse;
import rw.rra.roomiq.organization.domain.dto.CountryResponse;
import rw.rra.roomiq.organization.domain.dto.CreateCountryRequest;
import rw.rra.roomiq.organization.domain.dto.CreateDepartmentRequest;
import rw.rra.roomiq.organization.domain.dto.CreateDistrictRequest;
import rw.rra.roomiq.organization.domain.dto.CreateFloorRequest;
import rw.rra.roomiq.organization.domain.dto.CreateOfficeBuildingRequest;
import rw.rra.roomiq.organization.domain.dto.CreateProvinceRequest;
import rw.rra.roomiq.organization.domain.dto.DepartmentHierarchyResponse;
import rw.rra.roomiq.organization.domain.dto.DepartmentResponse;
import rw.rra.roomiq.organization.domain.dto.DistrictHierarchyResponse;
import rw.rra.roomiq.organization.domain.dto.DistrictResponse;
import rw.rra.roomiq.organization.domain.dto.FloorResponse;
import rw.rra.roomiq.organization.domain.dto.OfficeBuildingHierarchyResponse;
import rw.rra.roomiq.organization.domain.dto.OfficeBuildingResponse;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageQuery;
import rw.rra.roomiq.organization.domain.dto.OrganizationPageResponse;
import rw.rra.roomiq.organization.domain.dto.ProvinceHierarchyResponse;
import rw.rra.roomiq.organization.domain.dto.ProvinceResponse;
import rw.rra.roomiq.organization.domain.entity.Country;
import rw.rra.roomiq.organization.domain.entity.Department;
import rw.rra.roomiq.organization.domain.entity.DepartmentStatus;
import rw.rra.roomiq.organization.domain.entity.District;
import rw.rra.roomiq.organization.domain.entity.Floor;
import rw.rra.roomiq.organization.domain.entity.OfficeBuilding;
import rw.rra.roomiq.organization.domain.entity.Province;
import rw.rra.roomiq.organization.domain.mapper.OrganizationMapper;
import rw.rra.roomiq.organization.domain.repository.CountryRepository;
import rw.rra.roomiq.organization.domain.repository.DepartmentRepository;
import rw.rra.roomiq.organization.domain.repository.DistrictRepository;
import rw.rra.roomiq.organization.domain.repository.FloorRepository;
import rw.rra.roomiq.organization.domain.repository.OfficeBuildingRepository;
import rw.rra.roomiq.organization.domain.repository.ProvinceRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.ZoneId;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrganizationManagementService {
    private final CountryRepository countryRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final OfficeBuildingRepository officeBuildingRepository;
    private final FloorRepository floorRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentHierarchyService departmentHierarchyService;
    private final OrganizationMapper mapper;

    public OrganizationManagementService(CountryRepository countryRepository,
                                         ProvinceRepository provinceRepository,
                                         DistrictRepository districtRepository,
                                         OfficeBuildingRepository officeBuildingRepository,
                                         FloorRepository floorRepository,
                                         DepartmentRepository departmentRepository,
                                         DepartmentHierarchyService departmentHierarchyService,
                                         OrganizationMapper mapper) {
        this.countryRepository = countryRepository;
        this.provinceRepository = provinceRepository;
        this.districtRepository = districtRepository;
        this.officeBuildingRepository = officeBuildingRepository;
        this.floorRepository = floorRepository;
        this.departmentRepository = departmentRepository;
        this.departmentHierarchyService = departmentHierarchyService;
        this.mapper = mapper;
    }

    public OrganizationPageResponse<CountryResponse> listCountries(OrganizationPageQuery query) {
        Specification<Country> specification = (root, criteria, builder) -> builder.conjunction();
        if (query.active() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("active"), query.active()));
        }
        if (query.searchTerm() != null) {
            String pattern = likePattern(query.searchTerm());
            specification = specification.and((root, criteria, builder) -> builder.or(
                    builder.like(builder.lower(root.get("name")), pattern, '\\'),
                    builder.like(builder.lower(root.get("isoCode")), pattern, '\\')));
        }
        return page(countryRepository.findAll(specification,
                pageRequest(query, "name", Set.of("name", "isoCode", "active", "createdAt", "updatedAt"))),
                mapper::toResponse, query, "name");
    }

    public CountryResponse getCountry(UUID id) {
        return mapper.toResponse(require(countryRepository.findById(id), "COUNTRY_NOT_FOUND", "Country"));
    }

    @Transactional
    public CountryResponse createCountry(CreateCountryRequest request) {
        return mapper.toResponse(persist(() -> countryRepository.saveAndFlush(mapper.toEntity(request))));
    }

    @Transactional
    public CountryResponse updateCountry(UUID id, CreateCountryRequest request) {
        Country country = require(countryRepository.findByIdForUpdate(id), "COUNTRY_NOT_FOUND", "Country");
        boolean active = request.active() == null ? country.isActive() : request.active();
        ensureActiveChildrenAllowed(active, hasActiveCountryChildren(id));
        country.updateProfile(request.name().trim(), normalizeIsoCode(request.isoCode()));
        if (request.active() != null) {
            country.setActive(request.active());
        }
        return mapper.toResponse(persist(() -> countryRepository.saveAndFlush(country)));
    }

    @Transactional
    public CountryResponse setCountryActive(UUID id, boolean active) {
        Country country = require(countryRepository.findByIdForUpdate(id), "COUNTRY_NOT_FOUND", "Country");
        if (!active && hasActiveCountryChildren(id)) {
            throw activeChildrenConflict();
        }
        country.setActive(active);
        return mapper.toResponse(persist(() -> countryRepository.saveAndFlush(country)));
    }

    @Transactional
    public void deleteCountry(UUID id) {
        delete(countryRepository.findById(id).orElseThrow(() -> notFound("COUNTRY_NOT_FOUND", "Country")),
                countryRepository::delete);
    }

    public OrganizationPageResponse<ProvinceResponse> listProvinces(OrganizationPageQuery query) {
        Specification<Province> specification = (root, criteria, builder) -> builder.conjunction();
        if (query.countryId() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("country").get("id"), query.countryId()));
        }
        if (query.active() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("active"), query.active()));
        }
        if (query.searchTerm() != null) {
            String pattern = likePattern(query.searchTerm());
            specification = specification.and((root, criteria, builder) ->
                    builder.like(builder.lower(root.get("name")), pattern, '\\'));
        }
        return page(provinceRepository.findAll(specification,
                pageRequest(query, "name", Set.of("name", "active", "createdAt"))),
                mapper::toResponse, query, "name");
    }

    public ProvinceResponse getProvince(UUID id) {
        return mapper.toResponse(require(provinceRepository.findById(id), "PROVINCE_NOT_FOUND", "Province"));
    }

    @Transactional
    public ProvinceResponse createProvince(CreateProvinceRequest request) {
        Country country = require(countryRepository.findByIdForUpdate(request.countryId()),
            "COUNTRY_NOT_FOUND", "Country");
        ensureActiveParent(request.active() == null || request.active(), country.isActive());
        return mapper.toResponse(persist(() -> provinceRepository.saveAndFlush(mapper.toEntity(request, country))));
    }

    @Transactional
    public ProvinceResponse updateProvince(UUID id, CreateProvinceRequest request) {
        Province province = require(provinceRepository.findByIdForUpdate(id), "PROVINCE_NOT_FOUND", "Province");
        Country country = require(countryRepository.findByIdForUpdate(request.countryId()),
            "COUNTRY_NOT_FOUND", "Country");
        boolean active = request.active() == null ? province.isActive() : request.active();
        ensureActiveParent(active, country.isActive());
        ensureActiveChildrenAllowed(active, hasActiveProvinceChildren(id));
        province.updateProfile(country, request.name().trim());
        if (request.active() != null) {
            province.setActive(request.active());
        }
        return mapper.toResponse(persist(() -> provinceRepository.saveAndFlush(province)));
    }

    @Transactional
    public ProvinceResponse setProvinceActive(UUID id, boolean active) {
        Province province = require(provinceRepository.findByIdForUpdate(id), "PROVINCE_NOT_FOUND", "Province");
        if (active) {
            Country country = require(countryRepository.findByIdForUpdate(province.getCountry().getId()),
                    "COUNTRY_NOT_FOUND", "Country");
            ensureActiveParent(true, country.isActive());
        } else if (hasActiveProvinceChildren(id)) {
            throw activeChildrenConflict();
        }
        province.setActive(active);
        return mapper.toResponse(persist(() -> provinceRepository.saveAndFlush(province)));
    }

    @Transactional
    public void deleteProvince(UUID id) {
        delete(provinceRepository.findById(id).orElseThrow(() -> notFound("PROVINCE_NOT_FOUND", "Province")),
                provinceRepository::delete);
    }

    public OrganizationPageResponse<DistrictResponse> listDistricts(OrganizationPageQuery query) {
        Specification<District> specification = (root, criteria, builder) -> builder.conjunction();
        if (query.provinceId() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("province").get("id"), query.provinceId()));
        }
        if (query.active() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("active"), query.active()));
        }
        if (query.searchTerm() != null) {
            String pattern = likePattern(query.searchTerm());
            specification = specification.and((root, criteria, builder) ->
                    builder.like(builder.lower(root.get("name")), pattern, '\\'));
        }
        return page(districtRepository.findAll(specification,
                pageRequest(query, "name", Set.of("name", "active", "createdAt"))),
                mapper::toResponse, query, "name");
    }

    public DistrictResponse getDistrict(UUID id) {
        return mapper.toResponse(require(districtRepository.findById(id), "DISTRICT_NOT_FOUND", "District"));
    }

    @Transactional
    public DistrictResponse createDistrict(CreateDistrictRequest request) {
        Province province = require(provinceRepository.findByIdForUpdate(request.provinceId()),
                "PROVINCE_NOT_FOUND", "Province");
        ensureActiveParent(request.active() == null || request.active(), province.isActive());
        return mapper.toResponse(persist(() -> districtRepository.saveAndFlush(mapper.toEntity(request, province))));
    }

    @Transactional
    public DistrictResponse updateDistrict(UUID id, CreateDistrictRequest request) {
        District district = require(districtRepository.findByIdForUpdate(id), "DISTRICT_NOT_FOUND", "District");
        Province province = require(provinceRepository.findByIdForUpdate(request.provinceId()),
                "PROVINCE_NOT_FOUND", "Province");
        boolean active = request.active() == null ? district.isActive() : request.active();
        ensureActiveParent(active, province.isActive());
        ensureActiveChildrenAllowed(active, hasActiveDistrictChildren(id));
        district.updateProfile(province, request.name().trim());
        if (request.active() != null) {
            district.setActive(request.active());
        }
        return mapper.toResponse(persist(() -> districtRepository.saveAndFlush(district)));
    }

    @Transactional
    public DistrictResponse setDistrictActive(UUID id, boolean active) {
        District district = require(districtRepository.findByIdForUpdate(id), "DISTRICT_NOT_FOUND", "District");
        if (active) {
            Province province = require(provinceRepository.findByIdForUpdate(district.getProvince().getId()),
                    "PROVINCE_NOT_FOUND", "Province");
            ensureActiveParent(true, province.isActive());
        } else if (hasActiveDistrictChildren(id)) {
            throw activeChildrenConflict();
        }
        district.setActive(active);
        return mapper.toResponse(persist(() -> districtRepository.saveAndFlush(district)));
    }

    @Transactional
    public void deleteDistrict(UUID id) {
        delete(districtRepository.findById(id).orElseThrow(() -> notFound("DISTRICT_NOT_FOUND", "District")),
                districtRepository::delete);
    }

    public OrganizationPageResponse<OfficeBuildingResponse> listOfficeBuildings(OrganizationPageQuery query) {
        Specification<OfficeBuilding> specification = (root, criteria, builder) -> builder.conjunction();
        if (query.districtId() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("district").get("id"), query.districtId()));
        }
        if (query.active() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("active"), query.active()));
        }
        if (query.searchTerm() != null) {
            String pattern = likePattern(query.searchTerm());
            specification = specification.and((root, criteria, builder) -> builder.or(
                    builder.like(builder.lower(root.get("name")), pattern, '\\'),
                    builder.like(builder.lower(root.get("code")), pattern, '\\')));
        }
        return page(officeBuildingRepository.findAll(specification,
                pageRequest(query, "name", Set.of("name", "code", "timezone", "active", "createdAt"))),
                mapper::toResponse, query, "name");
    }

    public OfficeBuildingResponse getOfficeBuilding(UUID id) {
        return mapper.toResponse(require(officeBuildingRepository.findById(id),
                "OFFICE_BUILDING_NOT_FOUND", "Office building"));
    }

    @Transactional
    public OfficeBuildingResponse createOfficeBuilding(CreateOfficeBuildingRequest request) {
        District district = require(districtRepository.findByIdForUpdate(request.districtId()),
            "DISTRICT_NOT_FOUND", "District");
        ensureActiveParent(request.active() == null || request.active(), district.isActive());
        validateTimezone(request.timezone());
        return mapper.toResponse(persist(() -> officeBuildingRepository.saveAndFlush(mapper.toEntity(request, district))));
    }

    @Transactional
    public OfficeBuildingResponse updateOfficeBuilding(UUID id, CreateOfficeBuildingRequest request) {
        OfficeBuilding building = require(officeBuildingRepository.findByIdForUpdate(id),
                "OFFICE_BUILDING_NOT_FOUND", "Office building");
        District district = require(districtRepository.findByIdForUpdate(request.districtId()),
            "DISTRICT_NOT_FOUND", "District");
        boolean active = request.active() == null ? building.isActive() : request.active();
        ensureActiveParent(active, district.isActive());
        ensureActiveChildrenAllowed(active, floorRepository.existsByOfficeBuilding_IdAndActiveTrue(id)
            || departmentRepository.existsByOfficeBuilding_IdAndStatus(id, DepartmentStatus.ACTIVE));
        validateTimezone(request.timezone());
        building.updateProfile(district, request.name().trim(), request.code().trim(), normalize(request.address()),
            normalizeTimezone(request.timezone()), request.workingCalendarId());
        if (request.active() != null) {
            building.setActive(request.active());
        }
        return mapper.toResponse(persist(() -> officeBuildingRepository.saveAndFlush(building)));
    }

    @Transactional
    public OfficeBuildingResponse setOfficeBuildingActive(UUID id, boolean active) {
        OfficeBuilding building = require(officeBuildingRepository.findByIdForUpdate(id),
                "OFFICE_BUILDING_NOT_FOUND", "Office building");
        if (active) {
            District district = require(districtRepository.findByIdForUpdate(building.getDistrict().getId()),
                    "DISTRICT_NOT_FOUND", "District");
            ensureActiveParent(true, district.isActive());
        } else if (floorRepository.existsByOfficeBuilding_IdAndActiveTrue(id)
                || departmentRepository.existsByOfficeBuilding_IdAndStatus(id, DepartmentStatus.ACTIVE)) {
            throw activeChildrenConflict();
        }
        building.setActive(active);
        return mapper.toResponse(persist(() -> officeBuildingRepository.saveAndFlush(building)));
    }

    @Transactional
    public void deleteOfficeBuilding(UUID id) {
        delete(officeBuildingRepository.findById(id)
                .orElseThrow(() -> notFound("OFFICE_BUILDING_NOT_FOUND", "Office building")),
                officeBuildingRepository::delete);
    }

    public OrganizationPageResponse<FloorResponse> listFloors(OrganizationPageQuery query) {
        Specification<Floor> specification = (root, criteria, builder) -> builder.conjunction();
        if (query.officeBuildingId() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("officeBuilding").get("id"), query.officeBuildingId()));
        }
        if (query.active() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("active"), query.active()));
        }
        if (query.searchTerm() != null) {
            String pattern = likePattern(query.searchTerm());
            specification = specification.and((root, criteria, builder) ->
                    builder.like(builder.lower(root.get("name")), pattern, '\\'));
        }
        return page(floorRepository.findAll(specification,
                pageRequest(query, "level", Set.of("name", "level", "active", "createdAt"))),
                mapper::toResponse, query, "level");
    }

    public FloorResponse getFloor(UUID id) {
        return mapper.toResponse(require(floorRepository.findById(id), "FLOOR_NOT_FOUND", "Floor"));
    }

    @Transactional
    public FloorResponse createFloor(CreateFloorRequest request) {
        OfficeBuilding building = require(officeBuildingRepository.findByIdForUpdate(request.officeBuildingId()),
                "OFFICE_BUILDING_NOT_FOUND", "Office building");
        ensureActiveParent(request.active() == null || request.active(), building.isActive());
        return mapper.toResponse(persist(() -> floorRepository.saveAndFlush(mapper.toEntity(request, building))));
    }

    @Transactional
    public FloorResponse updateFloor(UUID id, CreateFloorRequest request) {
        Floor floor = require(floorRepository.findByIdForUpdate(id), "FLOOR_NOT_FOUND", "Floor");
        OfficeBuilding building = require(officeBuildingRepository.findByIdForUpdate(request.officeBuildingId()),
                "OFFICE_BUILDING_NOT_FOUND", "Office building");
        ensureActiveParent(request.active() == null ? floor.isActive() : request.active(), building.isActive());
        floor.updateProfile(building, request.name().trim(), request.level());
        if (request.active() != null) {
            floor.setActive(request.active());
        }
        return mapper.toResponse(persist(() -> floorRepository.saveAndFlush(floor)));
    }

    @Transactional
    public FloorResponse setFloorActive(UUID id, boolean active) {
        Floor floor = require(floorRepository.findByIdForUpdate(id), "FLOOR_NOT_FOUND", "Floor");
        if (active) {
            OfficeBuilding building = require(officeBuildingRepository.findByIdForUpdate(
                    floor.getOfficeBuilding().getId()), "OFFICE_BUILDING_NOT_FOUND", "Office building");
            ensureActiveParent(true, building.isActive());
        }
        floor.setActive(active);
        return mapper.toResponse(persist(() -> floorRepository.saveAndFlush(floor)));
    }

    @Transactional
    public void deleteFloor(UUID id) {
        delete(floorRepository.findById(id).orElseThrow(() -> notFound("FLOOR_NOT_FOUND", "Floor")),
                floorRepository::delete);
    }

    public OrganizationPageResponse<DepartmentResponse> listDepartments(OrganizationPageQuery query) {
        Specification<Department> specification = (root, criteria, builder) -> builder.conjunction();
        if (query.officeBuildingId() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("officeBuilding").get("id"), query.officeBuildingId()));
        }
        if (query.parentDepartmentId() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("parentDepartment").get("id"), query.parentDepartmentId()));
        }
        if (query.status() != null) {
            specification = specification.and((root, criteria, builder) ->
                    builder.equal(root.get("status"), query.status()));
        } else if (query.active() != null) {
            DepartmentStatus status = query.active() ? DepartmentStatus.ACTIVE : DepartmentStatus.INACTIVE;
            specification = specification.and((root, criteria, builder) -> builder.equal(root.get("status"), status));
        }
        if (query.searchTerm() != null) {
            String pattern = likePattern(query.searchTerm());
            specification = specification.and((root, criteria, builder) -> builder.or(
                    builder.like(builder.lower(root.get("name")), pattern, '\\'),
                    builder.like(builder.lower(root.get("code")), pattern, '\\')));
        }
        return page(departmentRepository.findAll(specification,
                pageRequest(query, "code", Set.of("name", "code", "status", "createdAt"))),
                mapper::toResponse, query, "code");
    }

    public DepartmentResponse getDepartment(UUID id) {
        return mapper.toResponse(require(departmentRepository.findById(id), "DEPARTMENT_NOT_FOUND", "Department"));
    }

    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        OfficeBuilding building = request.officeBuildingId() == null ? null
            : require(officeBuildingRepository.findByIdForUpdate(request.officeBuildingId()),
                "OFFICE_BUILDING_NOT_FOUND", "Office building");
        Department parent = request.parentDepartmentId() == null ? null
            : require(departmentRepository.findByIdForUpdate(request.parentDepartmentId()),
                "DEPARTMENT_PARENT_NOT_FOUND", "Parent department");
        boolean active = request.status() == null || request.status() == DepartmentStatus.ACTIVE;
        ensureActiveParent(active, building == null || building.isActive());
        ensureActiveParent(active, parent == null || parent.getStatus() == DepartmentStatus.ACTIVE);
        try {
            return mapper.toResponse(persist(() -> departmentRepository.saveAndFlush(
                mapper.toEntity(request, building, parent))));
        } catch (IllegalArgumentException exception) {
            throw invalidDepartmentParent();
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public DepartmentResponse updateDepartment(UUID id, CreateDepartmentRequest request) {
        Department updated = departmentHierarchyService.updateProfile(id, request.officeBuildingId(),
                request.parentDepartmentId(), request.name().trim(), request.code().trim(), request.status());
        return mapper.toResponse(updated);
    }

    @Transactional
    public DepartmentResponse setDepartmentStatus(UUID id, DepartmentStatus status) {
        return mapper.toResponse(departmentHierarchyService.setStatus(id, status));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public DepartmentResponse setDepartmentParent(UUID id, UUID parentDepartmentId) {
        return mapper.toResponse(departmentHierarchyService.reparent(id, parentDepartmentId));
    }

    @Transactional
    public void deleteDepartment(UUID id) {
        delete(departmentRepository.findById(id)
                .orElseThrow(() -> notFound("DEPARTMENT_NOT_FOUND", "Department")), departmentRepository::delete);
    }

    public CountryHierarchyResponse getCountryHierarchy(UUID countryId) {
        Country country = require(countryRepository.findById(countryId), "COUNTRY_NOT_FOUND", "Country");
        List<ProvinceHierarchyResponse> provinces = provinceRepository
                .findAllByCountry_IdOrderByNameAsc(countryId).stream()
                .map(this::provinceHierarchy)
                .toList();
        return new CountryHierarchyResponse(mapper.toResponse(country), provinces);
    }

    private ProvinceHierarchyResponse provinceHierarchy(Province province) {
        List<DistrictHierarchyResponse> districts = districtRepository
                .findAllByProvince_IdOrderByNameAsc(province.getId()).stream()
                .map(this::districtHierarchy)
                .toList();
        return new ProvinceHierarchyResponse(mapper.toResponse(province), districts);
    }

    private DistrictHierarchyResponse districtHierarchy(District district) {
        List<OfficeBuildingHierarchyResponse> buildings = officeBuildingRepository
                .findAllByDistrict_IdOrderByNameAsc(district.getId()).stream()
                .map(this::officeBuildingHierarchy)
                .toList();
        return new DistrictHierarchyResponse(mapper.toResponse(district), buildings);
    }

    private OfficeBuildingHierarchyResponse officeBuildingHierarchy(OfficeBuilding building) {
        List<FloorResponse> floors = floorRepository.findAllByOfficeBuilding_IdOrderByLevelAsc(building.getId())
                .stream().map(mapper::toResponse).toList();
        List<Department> departments = departmentRepository.findAllByOfficeBuilding_IdOrderByCodeAsc(building.getId());
        Map<UUID, List<Department>> children = departments.stream()
                .filter(department -> department.getParentDepartment() != null)
                .collect(Collectors.groupingBy(department -> department.getParentDepartment().getId()));
        List<DepartmentHierarchyResponse> roots = departments.stream()
                .filter(department -> department.getParentDepartment() == null)
                .map(department -> departmentHierarchy(department, children, new HashSet<>()))
                .toList();
        return new OfficeBuildingHierarchyResponse(mapper.toResponse(building), floors, roots);
    }

    private DepartmentHierarchyResponse departmentHierarchy(Department department,
                                                              Map<UUID, List<Department>> children,
                                                              Set<UUID> ancestors) {
        if (!ancestors.add(department.getId())) {
            throw new DomainException(HttpStatus.CONFLICT, "INVALID_DEPARTMENT_HIERARCHY",
                    "Department hierarchy contains a cycle");
        }
        List<DepartmentHierarchyResponse> childResponses = children
                .getOrDefault(department.getId(), Collections.emptyList()).stream()
                .map(child -> departmentHierarchy(child, children, ancestors))
                .toList();
        ancestors.remove(department.getId());
        return new DepartmentHierarchyResponse(mapper.toResponse(department), childResponses);
    }

    private PageRequest pageRequest(OrganizationPageQuery query, String defaultSort, Set<String> allowedSorts) {
        String sortBy = query.sortBy() == null || query.sortBy().isBlank() ? defaultSort : query.sortBy();
        if (!allowedSorts.contains(sortBy)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD", "Requested sort field is not supported");
        }
        Sort.Direction direction = query.sortDirection() == null
                ? Sort.Direction.ASC : Sort.Direction.fromString(query.sortDirection());
        return PageRequest.of(query.pageIndex(), query.pageSize(), Sort.by(direction, sortBy));
    }

    private <E, D> OrganizationPageResponse<D> page(Page<E> page, Function<E, D> mapper,
                                                     OrganizationPageQuery query, String defaultSort) {
        return new OrganizationPageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                query.sortBy() == null ? defaultSort : query.sortBy(),
                query.sortDirection() == null ? "ASC" : query.sortDirection().toUpperCase());
    }

    private String likePattern(String search) {
        return "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeIsoCode(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private void validateTimezone(String timezone) {
        if (timezone != null && !timezone.isBlank()
                && !ZoneId.getAvailableZoneIds().contains(timezone.trim())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE",
                    "Timezone must be a valid IANA timezone ID");
        }
    }

    private String normalizeTimezone(String timezone) {
        return timezone == null || timezone.isBlank() ? null : timezone.trim();
    }

    private void ensureActiveParent(boolean childActive, boolean parentActive) {
        if (childActive && !parentActive) {
            throw new DomainException(HttpStatus.CONFLICT, "ORGANIZATION_PARENT_INACTIVE",
                    "An active organization record requires an active parent");
        }
    }

    private void ensureActiveChildrenAllowed(boolean parentActive, boolean activeChildrenExist) {
        if (!parentActive && activeChildrenExist) {
            throw activeChildrenConflict();
        }
    }

    private DomainException activeChildrenConflict() {
        return new DomainException(HttpStatus.CONFLICT, "ORGANIZATION_ACTIVE_CHILDREN",
                "Deactivate active child records before deactivating this record");
    }

    private DomainException invalidDepartmentParent() {
        return new DomainException(HttpStatus.CONFLICT, "INVALID_DEPARTMENT_PARENT",
                "A department parent must have the same office-building scope and cannot create a cycle");
    }

        private boolean hasActiveCountryChildren(UUID countryId) {
        return provinceRepository.existsByCountry_IdAndActiveTrue(countryId)
            || districtRepository.existsByProvince_Country_IdAndActiveTrue(countryId)
            || officeBuildingRepository.existsByDistrict_Province_Country_IdAndActiveTrue(countryId)
            || floorRepository.existsByOfficeBuilding_District_Province_Country_IdAndActiveTrue(countryId)
            || departmentRepository.existsByOfficeBuilding_District_Province_Country_IdAndStatus(
                countryId, DepartmentStatus.ACTIVE);
        }

        private boolean hasActiveProvinceChildren(UUID provinceId) {
        return districtRepository.existsByProvince_IdAndActiveTrue(provinceId)
            || officeBuildingRepository.existsByDistrict_Province_IdAndActiveTrue(provinceId)
            || floorRepository.existsByOfficeBuilding_District_Province_IdAndActiveTrue(provinceId)
            || departmentRepository.existsByOfficeBuilding_District_Province_IdAndStatus(
                provinceId, DepartmentStatus.ACTIVE);
        }

        private boolean hasActiveDistrictChildren(UUID districtId) {
        return officeBuildingRepository.existsByDistrict_IdAndActiveTrue(districtId)
            || floorRepository.existsByOfficeBuilding_District_IdAndActiveTrue(districtId)
            || departmentRepository.existsByOfficeBuilding_District_IdAndStatus(
                districtId, DepartmentStatus.ACTIVE);
        }

    private <T> T require(java.util.Optional<T> value, String code, String resource) {
        return value.orElseThrow(() -> notFound(code, resource));
    }

    private DomainException notFound(String code, String resource) {
        return new DomainException(HttpStatus.NOT_FOUND, code, resource + " was not found");
    }

    private <T> T persist(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    private <T> void delete(T entity, java.util.function.Consumer<T> deleteOperation) {
        try {
            deleteOperation.accept(entity);
            countryRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    private DomainException conflict() {
        return new DomainException(HttpStatus.CONFLICT, "ORGANIZATION_RESOURCE_CONFLICT",
                "The organization record conflicts with an existing record or is referenced by another record");
    }
}