package rw.rra.roomiq.identity.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import rw.rra.roomiq.identity.domain.entity.AppUser;
import rw.rra.roomiq.identity.domain.entity.UserStatus;
import rw.rra.roomiq.identity.domain.repository.AppUserRepository;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity-organization-authorization-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "roomiq.auth.jwt-secret=test-only-signing-key-at-least-32-bytes-long",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.flyway.clean-disabled=false")
class OrganizationAuthorizationControllerTests {
    private static final String AUTHORIZATION_PATH = "/api/v1/internal/authorization/organization";
        private static final String SCHEDULING_AUTHORIZATION_PATH = "/api/v1/internal/authorization/scheduling";
    private static final String SYSTEM_ADMIN = "IDENTITY_SYSTEM_ADMIN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Test
    void activeUsersCanReadButCannotManageOrganization() throws Exception {
        AppUser user = createUser("organization-reader@rra.rw", UserStatus.ACTIVE);

        mockMvc.perform(post(AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"READ\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"MANAGE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemAdministratorCanManageOrganization() throws Exception {
        AppUser user = createUser("organization-admin@rra.rw", UserStatus.ACTIVE);

        mockMvc.perform(post(AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString()))
                                .authorities(new SimpleGrantedAuthority(SYSTEM_ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"MANAGE\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void inactiveUsersCannotReadOrganization() throws Exception {
        AppUser user = createUser("organization-inactive@rra.rw", UserStatus.SUSPENDED);

        mockMvc.perform(post(AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"READ\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void activeUsersCanReadButCannotManageScheduling() throws Exception {
        AppUser user = createUser("scheduling-reader@rra.rw", UserStatus.ACTIVE);

        mockMvc.perform(post(SCHEDULING_AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"READ\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.actorUserId").value(user.getId().toString()));
        mockMvc.perform(post(SCHEDULING_AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"MANAGE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveUsersCannotReadScheduling() throws Exception {
        AppUser user = createUser("scheduling-inactive@rra.rw", UserStatus.SUSPENDED);

        mockMvc.perform(post(SCHEDULING_AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"READ\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemAdministratorCanManageScheduling() throws Exception {
        AppUser user = createUser("scheduling-admin@rra.rw", UserStatus.ACTIVE);

        mockMvc.perform(post(SCHEDULING_AUTHORIZATION_PATH)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString()))
                                .authorities(new SimpleGrantedAuthority(SYSTEM_ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"MANAGE\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.actorUserId").value(user.getId().toString()));
    }

    private AppUser createUser(String email, UserStatus status) {
        AppUser user = new AppUser(email, "Organization Authorization Test", status);
        return users.saveAndFlush(user);
    }
}