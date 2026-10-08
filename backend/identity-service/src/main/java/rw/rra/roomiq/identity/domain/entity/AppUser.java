package rw.rra.roomiq.identity.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@SQLRestriction("deleted_at IS NULL")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "national_id", unique = true, length = 64)
    private String nationalId;

    @Column(unique = true, length = 32)
    private String phone;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "office_building_id")
    private UUID officeBuildingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<UserRole> roles = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<UserPrivilege> privileges = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<UserSession> sessions = new ArrayList<>();

    protected AppUser() {
    }

    public AppUser(String email, String fullName, UserStatus status) {
        this.email = email;
        this.fullName = fullName;
        this.status = status;
    }

    @PrePersist
    void initializeAuditFields() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (version == null) {
            version = 0;
        }
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getNationalId() { return nationalId; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getDisplayName() { return displayName; }
    public UUID getDepartmentId() { return departmentId; }
    public UUID getOfficeBuildingId() { return officeBuildingId; }
    public UserStatus getStatus() { return status; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Integer getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public List<UserRole> getRoles() { return roles; }
    public List<UserPrivilege> getPrivileges() { return privileges; }
    public List<UserSession> getSessions() { return sessions; }

    public void updateProfile(String email, String nationalId, String phone, String fullName,
                              String displayName, UUID departmentId, UUID officeBuildingId) {
        if (email != null) this.email = email;
        if (nationalId != null) this.nationalId = nationalId;
        if (phone != null) this.phone = phone;
        if (fullName != null) this.fullName = fullName;
        if (displayName != null) this.displayName = displayName;
        if (departmentId != null) this.departmentId = departmentId;
        if (officeBuildingId != null) this.officeBuildingId = officeBuildingId;
    }

    public void updateStatus(UserStatus status) {
        this.status = status;
    }

    public void replacePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void recordSuccessfulLogin(Instant loginTime) {
        this.lastLoginAt = loginTime;
    }

    public void markDeleted() {
        deletedAt = Instant.now();
        status = UserStatus.DISABLED;
    }
}
