package rw.rra.roomiq.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.entity.Permission;
import rw.rra.roomiq.identity.domain.entity.Role;
import rw.rra.roomiq.identity.domain.entity.RolePermission;
import rw.rra.roomiq.identity.domain.entity.UserPrivilege;
import rw.rra.roomiq.identity.domain.entity.UserRole;
import rw.rra.roomiq.identity.domain.entity.UserSession;
import rw.rra.roomiq.identity.domain.entity.UserStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:identity-test",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"roomiq.auth.jwt-secret=test-only-signing-key-at-least-32-bytes-long",
		"spring.flyway.enabled=true",
		"spring.flyway.locations=classpath:db/migration",
		"spring.flyway.clean-disabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class IdentityServiceApplicationTests {
	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void identityEntitiesPersistAuditFieldsAndSoftDeleteUsers() {
		assertThat(entityManager.getMetamodel().getEntities())
				.extracting(entityType -> entityType.getJavaType().getSimpleName())
				.containsExactlyInAnyOrder(
						"AppUser", "Role", "Permission", "RolePermission",
						"UserRole", "UserPrivilege", "UserSession", "IdentityAuditOutboxEntry");

		AppUser user = new AppUser("entity-test@rra.rw", "Entity Test", UserStatus.ACTIVE);
		Role role = new Role("TEST_ROLE", "Test role", false);
		Permission permission = new Permission("TEST_READ", "Test read");
		entityManager.persist(user);
		entityManager.persist(role);
		entityManager.persist(permission);
		entityManager.persist(new RolePermission(role, permission));
		entityManager.persist(new UserRole(user, role, null, null));
		entityManager.persist(new UserPrivilege(user, "CG_BOOKING", null, true));
		entityManager.persist(new UserSession(user, "hashed-refresh-token", Instant.now(), Instant.now().plusSeconds(3600)));
		entityManager.flush();

		assertThat(user.getId()).isNotNull();
		assertThat(user.getCreatedAt()).isNotNull();
		assertThat(user.getVersion()).isZero();

		user.markDeleted();
		entityManager.flush();
		entityManager.clear();

		assertThat(entityManager.find(AppUser.class, user.getId())).isNull();
	}

}
