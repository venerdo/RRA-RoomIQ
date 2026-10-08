package rw.rra.roomiq.organization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.organization.domain.dto.CreateCountryRequest;
import rw.rra.roomiq.organization.domain.dto.CreateDepartmentRequest;
import rw.rra.roomiq.organization.domain.dto.CreateDistrictRequest;
import rw.rra.roomiq.organization.domain.dto.CreateFloorRequest;
import rw.rra.roomiq.organization.domain.dto.CreateOfficeBuildingRequest;
import rw.rra.roomiq.organization.domain.dto.CreateProvinceRequest;
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
import rw.rra.roomiq.organization.domain.service.DepartmentHierarchyService;
import rw.rra.roomiq.organization.domain.service.OrganizationManagementService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OrganizationDomainPersistenceTests {
    @Autowired
    private OrganizationMapper mapper;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private DistrictRepository districtRepository;

    @Autowired
    private OfficeBuildingRepository officeBuildingRepository;

    @Autowired
    private FloorRepository floorRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

        @Autowired
        private DepartmentHierarchyService departmentHierarchyService;

        @Autowired
        private Validator validator;

        @Autowired
        private OrganizationManagementService organizationManagementService;

    @Test
    void mapsAndPersistsOrganizationHierarchy() {
        Country country = countryRepository.saveAndFlush(
                mapper.toEntity(new CreateCountryRequest("Rwanda", "RW", null)));
        Province province = provinceRepository.saveAndFlush(
                mapper.toEntity(new CreateProvinceRequest(country.getId(), "Kigali City", false), country));
        District district = districtRepository.saveAndFlush(
                mapper.toEntity(new CreateDistrictRequest(province.getId(), "Gasabo", null), province));

        UUID workingCalendarId = UUID.randomUUID();
        OfficeBuilding officeBuilding = officeBuildingRepository.saveAndFlush(mapper.toEntity(
                new CreateOfficeBuildingRequest(district.getId(), "RRA Headquarters", "HQ", "Kigali",
                        null, workingCalendarId, null), district));
        Floor floor = floorRepository.saveAndFlush(mapper.toEntity(
                new CreateFloorRequest(officeBuilding.getId(), "Ground Floor", (short) 0, false), officeBuilding));

        Department parent = departmentRepository.saveAndFlush(mapper.toEntity(
                new CreateDepartmentRequest(officeBuilding.getId(), null, "Finance", "FIN",
                        DepartmentStatus.ACTIVE), officeBuilding, null));
        Department child = departmentRepository.saveAndFlush(mapper.toEntity(
                new CreateDepartmentRequest(officeBuilding.getId(), parent.getId(), "Revenue", "REV",
                        DepartmentStatus.INACTIVE), officeBuilding, parent));

        assertThat(country.getId()).isNotNull();
        assertThat(country.getCreatedAt()).isNotNull();
        assertThat(country.isActive()).isTrue();
        assertThat(country.getUpdatedAt()).isNull();
        assertThat(province.getCreatedAt()).isNotNull();
        assertThat(province.isActive()).isFalse();
        assertThat(district.getCreatedAt()).isNotNull();
        assertThat(district.isActive()).isTrue();
        assertThat(officeBuilding.getCreatedAt()).isNotNull();
        assertThat(officeBuilding.getTimezone()).isEqualTo(OfficeBuilding.DEFAULT_TIMEZONE);
        assertThat(officeBuilding.getWorkingCalendarId()).isEqualTo(workingCalendarId);
        assertThat(floor.getCreatedAt()).isNotNull();
        assertThat(floor.isActive()).isFalse();
        assertThat(child.getCreatedAt()).isNotNull();
        assertThat(child.getStatus()).isEqualTo(DepartmentStatus.INACTIVE);

        assertThat(provinceRepository.findAllByCountry_IdOrderByNameAsc(country.getId()))
                .extracting(Province::getId).containsExactly(province.getId());
        assertThat(districtRepository.findAllByProvince_IdOrderByNameAsc(province.getId()))
                .extracting(District::getId).containsExactly(district.getId());
        assertThat(officeBuildingRepository.findAllByDistrict_IdOrderByNameAsc(district.getId()))
                .extracting(OfficeBuilding::getId).containsExactly(officeBuilding.getId());
        assertThat(floorRepository.findAllByOfficeBuilding_IdOrderByLevelAsc(officeBuilding.getId()))
                .extracting(Floor::getId).containsExactly(floor.getId());
        assertThat(departmentRepository.findAllByParentDepartment_IdOrderByCodeAsc(parent.getId()))
                .extracting(Department::getId).containsExactly(child.getId());

        assertThat(mapper.toResponse(country).createdAt()).isEqualTo(country.getCreatedAt());
        assertThat(mapper.toResponse(province).countryId()).isEqualTo(country.getId());
        assertThat(mapper.toResponse(district).provinceId()).isEqualTo(province.getId());
        assertThat(mapper.toResponse(officeBuilding).workingCalendarId()).isEqualTo(workingCalendarId);
        assertThat(mapper.toResponse(floor).officeBuildingId()).isEqualTo(officeBuilding.getId());
        assertThat(mapper.toResponse(child).parentDepartmentId()).isEqualTo(parent.getId());
    }

        @Test
        void officeBuildingTimezoneMustBeAnAvailableIanaZone() {
                CreateOfficeBuildingRequest invalidTimezone = new CreateOfficeBuildingRequest(
                                UUID.randomUUID(), "Timezone Test Building", "TIMEZONE-TEST", null,
                                "Mars/Olympus", null, null);
                CreateOfficeBuildingRequest validTimezone = new CreateOfficeBuildingRequest(
                        UUID.randomUUID(), "Timezone Test Building", "TIMEZONE-VALID", "   ",
                        " Europe/Paris ", null, null);

                assertThat(validator.validate(invalidTimezone))
                                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                                                .isEqualTo("timezone"));
                assertThat(validator.validate(validTimezone)).isEmpty();
                CreateCountryRequest blankIsoCode = new CreateCountryRequest("Blank ISO Country", "  ", null);
                assertThat(validator.validate(blankIsoCode)).isEmpty();
                assertThat(mapper.toEntity(blankIsoCode).getIsoCode()).isNull();
                OfficeBuilding normalized = mapper.toEntity(validTimezone, null);
                assertThat(normalized.getTimezone()).isEqualTo("Europe/Paris");
                assertThat(normalized.getAddress()).isNull();

                Country country = countryRepository.saveAndFlush(
                        mapper.toEntity(new CreateCountryRequest("Timezone Service Country", null, null)));
                Province province = provinceRepository.saveAndFlush(mapper.toEntity(
                        new CreateProvinceRequest(country.getId(), "Timezone Service Province", null), country));
                District district = districtRepository.saveAndFlush(mapper.toEntity(
                        new CreateDistrictRequest(province.getId(), "Timezone Service District", null), province));
                CreateOfficeBuildingRequest serviceInvalidTimezone = new CreateOfficeBuildingRequest(
                        district.getId(), "Invalid Service Timezone", "INVALID-SERVICE-TZ", null,
                        "Mars/Olympus", null, null);
                assertThatThrownBy(() -> organizationManagementService.createOfficeBuilding(serviceInvalidTimezone))
                        .isInstanceOfSatisfying(DomainException.class, exception ->
                                assertThat(exception.code()).isEqualTo("INVALID_TIMEZONE"));
        }

            @Test
            void organizationDtosRejectBlankAndOversizedNamesAndCodes() {
                CreateOfficeBuildingRequest blankBuildingFields = new CreateOfficeBuildingRequest(
                        UUID.randomUUID(), " ", " ", null, null, null, null);
                CreateOfficeBuildingRequest oversizedBuildingFields = new CreateOfficeBuildingRequest(
                        UUID.randomUUID(), "N".repeat(201), "C".repeat(65), null, null, null, null);
                CreateDepartmentRequest blankDepartmentFields = new CreateDepartmentRequest(
                        null, null, " ", " ", DepartmentStatus.ACTIVE);
                CreateDepartmentRequest oversizedDepartmentFields = new CreateDepartmentRequest(
                        null, null, "N".repeat(151), "C".repeat(65), DepartmentStatus.ACTIVE);

                assertThat(validator.validate(blankBuildingFields))
                        .extracting(violation -> violation.getPropertyPath().toString())
                        .contains("name", "code");
                assertThat(validator.validate(oversizedBuildingFields))
                        .extracting(violation -> violation.getPropertyPath().toString())
                        .contains("name", "code");
                assertThat(validator.validate(blankDepartmentFields))
                        .extracting(violation -> violation.getPropertyPath().toString())
                        .contains("name", "code");
                assertThat(validator.validate(oversizedDepartmentFields))
                        .extracting(violation -> violation.getPropertyPath().toString())
                        .contains("name", "code");
            }

            @Test
            void parentDeactivationRejectsActiveDescendantsBehindInactiveIntermediateRecords() {
                Country country = countryRepository.saveAndFlush(
                        mapper.toEntity(new CreateCountryRequest("Legacy Lifecycle Country", null, null)));
                Province inactiveProvince = provinceRepository.saveAndFlush(mapper.toEntity(
                        new CreateProvinceRequest(country.getId(), "Legacy Inactive Province", false), country));
                District activeDistrict = districtRepository.saveAndFlush(mapper.toEntity(
                        new CreateDistrictRequest(inactiveProvince.getId(), "Legacy Active District", true), inactiveProvince));
                assertThatThrownBy(() -> organizationManagementService.setCountryActive(country.getId(), false))
                        .isInstanceOfSatisfying(DomainException.class, exception ->
                                assertThat(exception.code()).isEqualTo("ORGANIZATION_ACTIVE_CHILDREN"));

                Province province = provinceRepository.saveAndFlush(mapper.toEntity(
                        new CreateProvinceRequest(country.getId(), "Second Legacy Province", true), country));
                District inactiveDistrict = districtRepository.saveAndFlush(mapper.toEntity(
                        new CreateDistrictRequest(province.getId(), "Legacy Inactive District", false), province));
                OfficeBuilding activeBuilding = officeBuildingRepository.saveAndFlush(mapper.toEntity(
                        new CreateOfficeBuildingRequest(inactiveDistrict.getId(), "Legacy Active Building",
                                "LEGACY-ACTIVE-BUILDING", null, null, null, true), inactiveDistrict));
                assertThatThrownBy(() -> organizationManagementService.setProvinceActive(province.getId(), false))
                        .isInstanceOfSatisfying(DomainException.class, exception ->
                                assertThat(exception.code()).isEqualTo("ORGANIZATION_ACTIVE_CHILDREN"));

                District district = districtRepository.saveAndFlush(mapper.toEntity(
                        new CreateDistrictRequest(province.getId(), "Second Legacy District", true), province));
                OfficeBuilding inactiveBuilding = officeBuildingRepository.saveAndFlush(mapper.toEntity(
                        new CreateOfficeBuildingRequest(district.getId(), "Legacy Inactive Building",
                                "LEGACY-INACTIVE-BUILDING", null, null, null, false), district));
                floorRepository.saveAndFlush(mapper.toEntity(new CreateFloorRequest(
                        inactiveBuilding.getId(), "Legacy Active Floor", (short) 1, true), inactiveBuilding));
                assertThatThrownBy(() -> organizationManagementService.setDistrictActive(district.getId(), false))
                        .isInstanceOfSatisfying(DomainException.class, exception ->
                                assertThat(exception.code()).isEqualTo("ORGANIZATION_ACTIVE_CHILDREN"));

                assertThat(activeDistrict.getId()).isNotNull();
                assertThat(activeBuilding.getId()).isNotNull();
            }

    @Test
    void departmentReparentingRejectsCyclesMissingParentsAndCrossScopeLinks() {
        Country country = countryRepository.saveAndFlush(
                mapper.toEntity(new CreateCountryRequest("Test Country", "TC", null)));
        Province province = provinceRepository.saveAndFlush(
                mapper.toEntity(new CreateProvinceRequest(country.getId(), "Test Province", null), country));
        District district = districtRepository.saveAndFlush(
                mapper.toEntity(new CreateDistrictRequest(province.getId(), "Test District", null), province));
        OfficeBuilding building = officeBuildingRepository.saveAndFlush(mapper.toEntity(
                new CreateOfficeBuildingRequest(district.getId(), "Building One", "BUILDING-ONE", null,
                        null, null, null), district));
        OfficeBuilding otherBuilding = officeBuildingRepository.saveAndFlush(mapper.toEntity(
                new CreateOfficeBuildingRequest(district.getId(), "Building Two", "BUILDING-TWO", null,
                        null, null, null), district));

        Department parent = departmentRepository.saveAndFlush(mapper.toEntity(
                new CreateDepartmentRequest(building.getId(), null, "Parent", "PARENT",
                        DepartmentStatus.ACTIVE), building, null));
        Department child = departmentRepository.saveAndFlush(mapper.toEntity(
                new CreateDepartmentRequest(building.getId(), parent.getId(), "Child", "CHILD",
                        DepartmentStatus.ACTIVE), building, parent));
        Department otherScope = departmentRepository.saveAndFlush(mapper.toEntity(
                new CreateDepartmentRequest(otherBuilding.getId(), null, "Other Scope", "OTHER-SCOPE",
                        DepartmentStatus.ACTIVE), otherBuilding, null));

        assertThatThrownBy(() -> departmentHierarchyService.reparent(parent.getId(), child.getId()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> departmentHierarchyService.reparent(child.getId(), otherScope.getId()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> departmentHierarchyService.reparent(child.getId(), UUID.randomUUID()))
                .isInstanceOf(DomainException.class);

        assertThatThrownBy(() -> departmentHierarchyService.updateProfile(parent.getId(), otherBuilding.getId(), null,
                "Parent Moved", "PARENT-MOVED", DepartmentStatus.ACTIVE))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.code()).isEqualTo("DEPARTMENT_SCOPE_HAS_CHILDREN"));
        assertThatThrownBy(() -> departmentHierarchyService.setStatus(parent.getId(), DepartmentStatus.INACTIVE))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.code()).isEqualTo("ORGANIZATION_ACTIVE_CHILDREN"));

        departmentHierarchyService.setStatus(child.getId(), DepartmentStatus.INACTIVE);
        departmentHierarchyService.setStatus(parent.getId(), DepartmentStatus.INACTIVE);
        assertThatThrownBy(() -> departmentHierarchyService.setStatus(child.getId(), DepartmentStatus.ACTIVE))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.code()).isEqualTo("ORGANIZATION_PARENT_INACTIVE"));
        departmentHierarchyService.setStatus(parent.getId(), DepartmentStatus.ACTIVE);
        departmentHierarchyService.setStatus(child.getId(), DepartmentStatus.ACTIVE);

        Department detachedChild = departmentHierarchyService.reparent(child.getId(), null);
        assertThat(detachedChild.getParentDepartment()).isNull();
    }
}