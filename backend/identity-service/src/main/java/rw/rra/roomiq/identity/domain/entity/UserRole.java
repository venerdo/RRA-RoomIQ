package rw.rra.roomiq.identity.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_role")
public class UserRole {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "scope_office_building_id")
    private UUID scopeOfficeBuildingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id")
    private AppUser grantedBy;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    protected UserRole() {
    }

    public UserRole(AppUser user, Role role, UUID scopeOfficeBuildingId, AppUser grantedBy) {
        this.user = user;
        this.role = role;
        this.scopeOfficeBuildingId = scopeOfficeBuildingId;
        this.grantedBy = grantedBy;
        this.grantedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public Role getRole() { return role; }
    public UUID getScopeOfficeBuildingId() { return scopeOfficeBuildingId; }
    public AppUser getGrantedBy() { return grantedBy; }
    public Instant getGrantedAt() { return grantedAt; }
}
