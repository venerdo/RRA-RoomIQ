package rw.rra.roomiq.organization.domain.mapper;

import org.springframework.stereotype.Component;
import rw.rra.roomiq.organization.domain.dto.CountryResponse;
import rw.rra.roomiq.organization.domain.dto.CreateCountryRequest;
import rw.rra.roomiq.organization.domain.dto.CreateDepartmentRequest;
import rw.rra.roomiq.organization.domain.dto.CreateDistrictRequest;
import rw.rra.roomiq.organization.domain.dto.CreateFloorRequest;
import rw.rra.roomiq.organization.domain.dto.CreateOfficeBuildingRequest;
import rw.rra.roomiq.organization.domain.dto.CreateProvinceRequest;
import rw.rra.roomiq.organization.domain.dto.DepartmentResponse;
import rw.rra.roomiq.organization.domain.dto.DistrictResponse;
import rw.rra.roomiq.organization.domain.dto.FloorResponse;
import rw.rra.roomiq.organization.domain.dto.OfficeBuildingResponse;
import rw.rra.roomiq.organization.domain.dto.ProvinceResponse;
import rw.rra.roomiq.organization.domain.entity.Country;
import rw.rra.roomiq.organization.domain.entity.Department;
import rw.rra.roomiq.organization.domain.entity.DepartmentStatus;
import rw.rra.roomiq.organization.domain.entity.District;
import rw.rra.roomiq.organization.domain.entity.Floor;
import rw.rra.roomiq.organization.domain.entity.OfficeBuilding;
import rw.rra.roomiq.organization.domain.entity.Province;

@Component
public class OrganizationMapper {
    public Country toEntity(CreateCountryRequest request) {
        return new Country(request.name().trim(), normalizeIsoCode(request.isoCode()), activeOrDefault(request.active()));
    }

    public CountryResponse toResponse(Country country) {
        return new CountryResponse(country.getId(), country.getName(), country.getIsoCode(), country.isActive(),
                country.getCreatedAt(), country.getUpdatedAt());
    }

    public Province toEntity(CreateProvinceRequest request, Country country) {
        return new Province(country, request.name().trim(), activeOrDefault(request.active()));
    }

    public ProvinceResponse toResponse(Province province) {
        return new ProvinceResponse(province.getId(), province.getCountry().getId(), province.getName(),
                province.isActive(), province.getCreatedAt());
    }

    public District toEntity(CreateDistrictRequest request, Province province) {
        return new District(province, request.name().trim(), activeOrDefault(request.active()));
    }

    public DistrictResponse toResponse(District district) {
        return new DistrictResponse(district.getId(), district.getProvince().getId(), district.getName(),
                district.isActive(), district.getCreatedAt());
    }

    public OfficeBuilding toEntity(CreateOfficeBuildingRequest request, District district) {
        return new OfficeBuilding(district, request.name().trim(), request.code().trim(), normalize(request.address()),
            normalize(request.timezone()), request.workingCalendarId(), activeOrDefault(request.active()));
    }

    public OfficeBuildingResponse toResponse(OfficeBuilding officeBuilding) {
        return new OfficeBuildingResponse(officeBuilding.getId(), officeBuilding.getDistrict().getId(),
                officeBuilding.getName(), officeBuilding.getCode(), officeBuilding.getAddress(),
                officeBuilding.getTimezone(), officeBuilding.getWorkingCalendarId(), officeBuilding.isActive(),
                officeBuilding.getCreatedAt());
    }

    public Floor toEntity(CreateFloorRequest request, OfficeBuilding officeBuilding) {
        return new Floor(officeBuilding, request.name().trim(), request.level(), activeOrDefault(request.active()));
    }

    public FloorResponse toResponse(Floor floor) {
        return new FloorResponse(floor.getId(), floor.getOfficeBuilding().getId(), floor.getName(), floor.getLevel(),
                floor.isActive(), floor.getCreatedAt());
    }

    public Department toEntity(CreateDepartmentRequest request, OfficeBuilding officeBuilding,
                               Department parentDepartment) {
        return new Department(officeBuilding, parentDepartment, request.name().trim(), request.code().trim(),
                request.status() == null ? DepartmentStatus.ACTIVE : request.status());
    }

    public DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(department.getId(),
                department.getOfficeBuilding() == null ? null : department.getOfficeBuilding().getId(),
                department.getParentDepartment() == null ? null : department.getParentDepartment().getId(),
                department.getName(), department.getCode(), department.getStatus(), department.getCreatedAt());
    }

    private boolean activeOrDefault(Boolean active) {
        return active == null || active;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeIsoCode(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }
}