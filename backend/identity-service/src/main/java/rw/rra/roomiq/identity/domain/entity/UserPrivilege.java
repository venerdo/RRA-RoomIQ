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
@Table(name = "user_privilege")
public class UserPrivilege {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "privilege_code", nullable = false, length = 128)
    private String privilegeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id")
    private AppUser grantedBy;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected UserPrivilege() {
    }

    public UserPrivilege(AppUser user, String privilegeCode, AppUser grantedBy, boolean active) {
        this(user, privilegeCode, grantedBy, null, null, active);
    }

    public UserPrivilege(AppUser user, String privilegeCode, AppUser grantedBy,
                         Instant validFrom, Instant validTo, boolean active) {
        this.user = user;
        this.privilegeCode = privilegeCode;
        this.grantedBy = grantedBy;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.active = active;
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public String getPrivilegeCode() { return privilegeCode; }
    public AppUser getGrantedBy() { return grantedBy; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public boolean isActive() { return active; }

    public boolean isEffectiveAt(Instant instant) {
        return active
                && (validFrom == null || !instant.isBefore(validFrom))
                && (validTo == null || instant.isBefore(validTo));
    }

    public void deactivate() {
        active = false;
    }
}
