package rw.rra.roomiq.organization.domain.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.organization.domain.entity.Department;
import rw.rra.roomiq.organization.domain.entity.DepartmentStatus;
import rw.rra.roomiq.organization.domain.entity.OfficeBuilding;
import rw.rra.roomiq.organization.domain.repository.DepartmentRepository;
import rw.rra.roomiq.organization.domain.repository.OfficeBuildingRepository;

import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DepartmentHierarchyService {
    private final DepartmentRepository departmentRepository;
    private final OfficeBuildingRepository officeBuildingRepository;

    public DepartmentHierarchyService(DepartmentRepository departmentRepository,
                                      OfficeBuildingRepository officeBuildingRepository) {
        this.departmentRepository = departmentRepository;
        this.officeBuildingRepository = officeBuildingRepository;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Department reparent(UUID departmentId, UUID parentDepartmentId) {
        Map<UUID, Department> departments = departmentRepository.findAllForHierarchyUpdate().stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));

        Department department = departments.get(departmentId);
        if (department == null) {
            throw new DomainException(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND",
                    "Department was not found");
        }

        Department parent = parentDepartmentId == null ? null : departments.get(parentDepartmentId);
        if (parentDepartmentId != null && parent == null) {
            throw new DomainException(HttpStatus.NOT_FOUND, "DEPARTMENT_PARENT_NOT_FOUND",
                    "Parent department was not found");
        }

        try {
            department.reparent(parent);
        } catch (IllegalArgumentException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "INVALID_DEPARTMENT_PARENT", exception.getMessage());
        }
        ensureActiveParents(department, parent, department.getStatus());
        return save(department);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Department updateProfile(UUID departmentId, UUID officeBuildingId, UUID parentDepartmentId,
                                    String name, String code, DepartmentStatus status) {
        departmentRepository.findById(departmentId)
            .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND",
                "Department was not found"));
        OfficeBuilding officeBuilding = officeBuildingId == null ? null
            : officeBuildingRepository.findByIdForUpdate(officeBuildingId)
            .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "OFFICE_BUILDING_NOT_FOUND",
                "Office building was not found"));
        Map<UUID, Department> departments = departmentRepository.findAllForHierarchyUpdate().stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));

        Department department = departments.get(departmentId);
        if (department == null) {
            throw new DomainException(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND",
                    "Department was not found");
        }

        Department parent = parentDepartmentId == null ? null : departments.get(parentDepartmentId);
        if (parentDepartmentId != null && parent == null) {
            throw new DomainException(HttpStatus.NOT_FOUND, "DEPARTMENT_PARENT_NOT_FOUND",
                    "Parent department was not found");
        }

        boolean scopeChanged = !Objects.equals(idOf(department.getOfficeBuilding()), officeBuildingId);
        if (scopeChanged && departments.values().stream().anyMatch(candidate ->
                candidate.getParentDepartment() != null
                        && candidate.getParentDepartment().getId().equals(departmentId))) {
            throw new DomainException(HttpStatus.CONFLICT, "DEPARTMENT_SCOPE_HAS_CHILDREN",
                    "A department with children cannot change office-building scope");
        }
                if (status == DepartmentStatus.INACTIVE && hasActiveDescendant(departmentId, departments)) {
                    throw new DomainException(HttpStatus.CONFLICT, "ORGANIZATION_ACTIVE_CHILDREN",
                        "Deactivate active child departments before deactivating this department");
                }

        try {
            department.updateProfile(officeBuilding, parent, name, code, status);
        } catch (IllegalArgumentException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "INVALID_DEPARTMENT_PARENT", exception.getMessage());
        }
        ensureActiveParents(department, parent, status);
        return save(department);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Department setStatus(UUID departmentId, DepartmentStatus status) {
        Department existing = departmentRepository.findById(departmentId)
            .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND",
                "Department was not found"));
        if (existing.getOfficeBuilding() != null) {
            officeBuildingRepository.findByIdForUpdate(existing.getOfficeBuilding().getId())
                .orElseThrow(() -> new DomainException(HttpStatus.NOT_FOUND, "OFFICE_BUILDING_NOT_FOUND",
                    "Office building was not found"));
        }
        Map<UUID, Department> departments = departmentRepository.findAllForHierarchyUpdate().stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        Department department = departments.get(departmentId);
        if (department == null) {
            throw new DomainException(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND",
                    "Department was not found");
        }

        if (status == DepartmentStatus.ACTIVE) {
            ensureActiveParents(department, department.getParentDepartment(), status);
        } else if (hasActiveDescendant(departmentId, departments)) {
            throw new DomainException(HttpStatus.CONFLICT, "ORGANIZATION_ACTIVE_CHILDREN",
                    "Deactivate active child departments before deactivating this department");
        }

        department.setStatus(status);
        return save(department);
    }

    private void ensureActiveParents(Department department, Department parent, DepartmentStatus status) {
        if (status != DepartmentStatus.ACTIVE) {
            return;
        }
        if (parent != null && parent.getStatus() != DepartmentStatus.ACTIVE) {
            throw new DomainException(HttpStatus.CONFLICT, "ORGANIZATION_PARENT_INACTIVE",
                    "An active department requires an active parent department");
        }
        OfficeBuilding building = department.getOfficeBuilding();
        if (building != null && !building.isActive()) {
            throw new DomainException(HttpStatus.CONFLICT, "ORGANIZATION_PARENT_INACTIVE",
                    "An active department requires an active office building");
        }
    }

    private boolean hasActiveDescendant(UUID departmentId, Map<UUID, Department> departments) {
        Map<UUID, List<Department>> children = departments.values().stream()
                .filter(candidate -> candidate.getParentDepartment() != null)
                .collect(Collectors.groupingBy(candidate -> candidate.getParentDepartment().getId()));
        Deque<Department> pending = new ArrayDeque<>(children.getOrDefault(departmentId, List.of()));
        while (!pending.isEmpty()) {
            Department child = pending.removeFirst();
            if (child.getStatus() == DepartmentStatus.ACTIVE) {
                return true;
            }
            pending.addAll(children.getOrDefault(child.getId(), List.of()));
        }
        return false;
    }

    private UUID idOf(OfficeBuilding building) {
        return building == null ? null : building.getId();
    }

    private Department save(Department department) {
        try {
            return departmentRepository.saveAndFlush(department);
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException(HttpStatus.CONFLICT, "ORGANIZATION_RESOURCE_CONFLICT",
                    "The organization record conflicts with an existing record or is referenced by another record");
        }
    }
}