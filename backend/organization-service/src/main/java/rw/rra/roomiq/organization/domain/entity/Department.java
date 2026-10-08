package rw.rra.roomiq.organization.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "department", uniqueConstraints = @UniqueConstraint(
        name = "uq_department_code", columnNames = "code"))
public class Department extends OrganizationEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_building_id")
    private OfficeBuilding officeBuilding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_department_id")
    private Department parentDepartment;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepartmentStatus status;

    protected Department() {
    }

    public Department(OfficeBuilding officeBuilding, Department parentDepartment,
                      String name, String code, DepartmentStatus status) {
        this.officeBuilding = officeBuilding;
        this.name = name;
        this.code = code;
        this.status = status;
        reparent(parentDepartment);
    }

    public void reparent(Department parentDepartment) {
        validateParent(parentDepartment);
        this.parentDepartment = parentDepartment;
    }

    public void updateProfile(OfficeBuilding officeBuilding, Department parentDepartment,
                              String name, String code, DepartmentStatus status) {
        OfficeBuilding previousOfficeBuilding = this.officeBuilding;
        this.officeBuilding = officeBuilding;
        try {
            validateParent(parentDepartment);
        } catch (IllegalArgumentException exception) {
            this.officeBuilding = previousOfficeBuilding;
            throw exception;
        }
        this.parentDepartment = parentDepartment;
        this.name = name;
        this.code = code;
        this.status = status;
    }

    public void setStatus(DepartmentStatus status) {
        this.status = status;
    }

    private void validateParent(Department proposedParent) {
        if (proposedParent == null) {
            return;
        }

        Set<Department> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Department ancestor = proposedParent;
        UUID departmentId = getId();
        while (ancestor != null) {
            if (ancestor == this || (departmentId != null && departmentId.equals(ancestor.getId()))) {
                throw new IllegalArgumentException("A department cannot be its own ancestor");
            }
            if (!visited.add(ancestor)) {
                throw new IllegalArgumentException("The proposed parent hierarchy already contains a cycle");
            }
            if (!sameOfficeBuildingScope(officeBuilding, ancestor.getOfficeBuilding())) {
                throw new IllegalArgumentException("A department parent must have the same office-building scope");
            }
            ancestor = ancestor.getParentDepartment();
        }
    }

    private boolean sameOfficeBuildingScope(OfficeBuilding first, OfficeBuilding second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        UUID firstId = first.getId();
        UUID secondId = second.getId();
        return firstId != null && firstId.equals(secondId);
    }

    public OfficeBuilding getOfficeBuilding() {
        return officeBuilding;
    }

    public Department getParentDepartment() {
        return parentDepartment;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public DepartmentStatus getStatus() {
        return status;
    }
}