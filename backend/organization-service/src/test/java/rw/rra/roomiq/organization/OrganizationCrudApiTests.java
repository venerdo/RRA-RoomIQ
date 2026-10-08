package rw.rra.roomiq.organization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import rw.rra.roomiq.common.web.DomainException;
import rw.rra.roomiq.organization.config.OrganizationAuthorizationClient;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationCrudApiTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrganizationAuthorizationClient authorizationClient;

    @BeforeEach
    void configureAuthenticatedRequests(WebApplicationContext context) {
        doNothing().when(authorizationClient).authorize(anyString(), anyString());
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/").header("Authorization", "Bearer organization-api-test"))
                .build();
    }

    @Test
    void openApiDocumentsOrganizationSecurityErrorsAndCorrelationIds() throws Exception {
        String response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode document = objectMapper.readTree(response);

        assertThat(document.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http");
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(document.at("/components/schemas/ApiError").isObject()).isTrue();
        assertThat(document.at("/paths/~1api~1v1~1office-buildings/post/requestBody/content/application~1json/schema/$ref")
                .asText()).contains("CreateOfficeBuildingRequest");

        JsonNode paths = document.path("paths");
        int operationCount = 0;
                Set<String> actualOperations = new HashSet<>();
                for (var pathEntry : paths.properties()) {
                        String path = pathEntry.getKey();
                        JsonNode pathItem = pathEntry.getValue();
                        for (var operationEntry : pathItem.properties()) {
                                JsonNode operation = operationEntry.getValue();
                if (!operation.has("operationId")) {
                    continue;
                }
                operationCount++;
                                actualOperations.add(operationEntry.getKey().toUpperCase() + " " + path);
                assertThat(operation.path("security").toString()).contains("bearerAuth");
                assertThat(operation.path("parameters").toString()).contains("X-Correlation-ID");
                                JsonNode successResponse = operation.path("responses").has("200")
                                        ? operation.path("responses").path("200") : operation.path("responses").path("201");
                                assertThat(successResponse.path("content").path("application/json").path("schema").toString())
                                        .as("success schema for %s %s", operationEntry.getKey(), path)
                                        .contains("ApiResponse");
                                for (String errorStatus : Set.of("400", "401", "403", "404", "409", "500", "503")) {
                                    assertThat(operation.path("responses").path(errorStatus).path("content")
                                            .path("application/json").path("schema").path("$ref").asText())
                                            .isEqualTo("#/components/schemas/ApiError");
                                }
                assertThat(operation.path("responses").path("409").path("description").asText())
                        .contains("ORGANIZATION_RESOURCE_CONFLICT");
                assertThat(operation.path("responses").path("404").path("description").asText())
                        .contains("DEPARTMENT_NOT_FOUND");
                assertThat(operation.path("responses").path("200").path("headers")
                        .has("X-Correlation-ID") || operation.path("responses").path("201").path("headers")
                        .has("X-Correlation-ID")).isTrue();
            }
        }
        assertThat(operationCount).isEqualTo(38);
                assertThat(actualOperations).containsExactlyInAnyOrderElementsOf(organizationOperations());
    }

        private Set<String> organizationOperations() {
                return Set.of(
                                "POST /api/v1/countries", "GET /api/v1/countries", "GET /api/v1/countries/{id}",
                                "PUT /api/v1/countries/{id}", "PATCH /api/v1/countries/{id}/status",
                                "DELETE /api/v1/countries/{id}", "GET /api/v1/countries/{id}/hierarchy",
                                "POST /api/v1/provinces", "GET /api/v1/provinces", "GET /api/v1/provinces/{id}",
                                "PUT /api/v1/provinces/{id}", "PATCH /api/v1/provinces/{id}/status",
                                "DELETE /api/v1/provinces/{id}",
                                "POST /api/v1/districts", "GET /api/v1/districts", "GET /api/v1/districts/{id}",
                                "PUT /api/v1/districts/{id}", "PATCH /api/v1/districts/{id}/status",
                                "DELETE /api/v1/districts/{id}",
                                "POST /api/v1/office-buildings", "GET /api/v1/office-buildings",
                                "GET /api/v1/office-buildings/{id}", "PUT /api/v1/office-buildings/{id}",
                                "PATCH /api/v1/office-buildings/{id}/status", "DELETE /api/v1/office-buildings/{id}",
                                "POST /api/v1/floors", "GET /api/v1/floors", "GET /api/v1/floors/{id}",
                                "PUT /api/v1/floors/{id}", "PATCH /api/v1/floors/{id}/status", "DELETE /api/v1/floors/{id}",
                                "POST /api/v1/departments", "GET /api/v1/departments", "GET /api/v1/departments/{id}",
                                "PUT /api/v1/departments/{id}", "PATCH /api/v1/departments/{id}/status",
                                "PATCH /api/v1/departments/{id}/parent", "DELETE /api/v1/departments/{id}");
        }

    @Test
    void organizationRoutesDelegateAuthorizationAndReturnSharedDenialEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/countries"))
                .andExpect(status().isOk());
        verify(authorizationClient).authorize("Bearer organization-api-test", "READ");

        clearInvocations(authorizationClient);
        doThrow(new DomainException(HttpStatus.SERVICE_UNAVAILABLE,
                "IDENTITY_AUTHORIZATION_UNAVAILABLE", "Identity authorization is unavailable"))
                .when(authorizationClient).authorize("Bearer organization-api-test", "READ");
        mockMvc.perform(get("/api/v1/countries"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IDENTITY_AUTHORIZATION_UNAVAILABLE"));

        clearInvocations(authorizationClient);
        doThrow(new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied"))
                .when(authorizationClient).authorize("Bearer organization-api-test", "MANAGE");
        mockMvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Denied country\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(authorizationClient).authorize("Bearer organization-api-test", "MANAGE");
    }

    @Test
    void organizationResourcesSupportCrudFiltersPagingSortingAndHierarchy() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String prefix = "Api" + suffix;

        String countryId = create("/api/v1/countries", """
                {"name":"%s Country"}
                """.formatted(prefix));
        String provinceId = create("/api/v1/provinces", """
                {"countryId":"%s","name":"%s Province"}
                """.formatted(countryId, prefix));
        String districtId = create("/api/v1/districts", """
                {"provinceId":"%s","name":"%s District"}
                """.formatted(provinceId, prefix));
        String buildingId = create("/api/v1/office-buildings", """
                {"districtId":"%s","name":"%s Building","code":"%s-BLDG"}
                """.formatted(districtId, prefix, prefix));
        String floorId = create("/api/v1/floors", """
                {"officeBuildingId":"%s","name":"%s Floor","level":2}
                """.formatted(buildingId, prefix));
        String parentDepartmentId = create("/api/v1/departments", """
                {"officeBuildingId":"%s","name":"%s Finance","code":"%s-FIN","status":"ACTIVE"}
                """.formatted(buildingId, prefix, prefix));
        String childDepartmentId = create("/api/v1/departments", """
                {"officeBuildingId":"%s","parentDepartmentId":"%s","name":"%s Revenue","code":"%s-REV","status":"INACTIVE"}
                """.formatted(buildingId, parentDepartmentId, prefix, prefix));

        mockMvc.perform(get("/api/v1/countries/{id}", countryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(prefix + " Country"));
        mockMvc.perform(get("/api/v1/provinces/{id}", provinceId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/districts/{id}", districtId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/office-buildings/{id}", buildingId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/floors/{id}", floorId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/departments/{id}", childDepartmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.officeBuildingId").value(buildingId))
                .andExpect(jsonPath("$.data.parentDepartmentId").value(parentDepartmentId));
        mockMvc.perform(get("/api/v1/departments/{id}", parentDepartmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.officeBuildingId").value(buildingId));

        mockMvc.perform(get("/api/v1/countries")
                        .param("search", prefix)
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sortBy", "name")
                        .param("sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1));
        mockMvc.perform(get("/api/v1/provinces").param("countryId", countryId).param("search", prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/districts").param("provinceId", provinceId).param("search", prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/office-buildings").param("districtId", districtId).param("search", prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/floors").param("officeBuildingId", buildingId).param("search", prefix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/departments")
                        .param("officeBuildingId", buildingId)
                        .param("status", "ACTIVE")
                        .param("search", "finance")
                        .param("sortBy", "code")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/countries").param("sortBy", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT_FIELD"));

        update("/api/v1/countries/" + countryId, """
                {"name":"%s Country Updated","active":true}
                """.formatted(prefix));
        update("/api/v1/provinces/" + provinceId, """
                {"countryId":"%s","name":"%s Province Updated","active":true}
                """.formatted(countryId, prefix));
        update("/api/v1/districts/" + districtId, """
                {"provinceId":"%s","name":"%s District Updated","active":true}
                """.formatted(provinceId, prefix));
        update("/api/v1/office-buildings/" + buildingId, """
                {"districtId":"%s","name":"%s Building Updated","code":"%s-BLDG","timezone":"Africa/Kigali"}
                """.formatted(districtId, prefix, prefix));
        update("/api/v1/floors/" + floorId, """
                {"officeBuildingId":"%s","name":"%s Floor Updated","level":3}
                """.formatted(buildingId, prefix));
        update("/api/v1/departments/" + childDepartmentId, """
                {"officeBuildingId":"%s","parentDepartmentId":"%s","name":"%s Revenue Updated","code":"%s-REV","status":"ACTIVE"}
                """.formatted(buildingId, parentDepartmentId, prefix, prefix));

        mockMvc.perform(patch("/api/v1/departments/{id}/status", childDepartmentId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/departments/{id}/status", parentDepartmentId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        setActive("/api/v1/floors/" + floorId + "/status", false);
        setActive("/api/v1/office-buildings/" + buildingId + "/status", false);
        setActive("/api/v1/districts/" + districtId + "/status", false);
        setActive("/api/v1/provinces/" + provinceId + "/status", false);
        setActive("/api/v1/countries/" + countryId + "/status", false);
        mockMvc.perform(get("/api/v1/countries").param("search", prefix).param("active", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        setActive("/api/v1/countries/" + countryId + "/status", true);
        setActive("/api/v1/provinces/" + provinceId + "/status", true);
        setActive("/api/v1/districts/" + districtId + "/status", true);
        setActive("/api/v1/office-buildings/" + buildingId + "/status", true);
        setActive("/api/v1/floors/" + floorId + "/status", true);
        mockMvc.perform(patch("/api/v1/departments/{id}/status", parentDepartmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/departments/{id}/status", childDepartmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/countries/{id}", countryId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(true));
        mockMvc.perform(patch("/api/v1/departments/{id}/status", childDepartmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INACTIVE"));
        mockMvc.perform(patch("/api/v1/departments/{id}/status", childDepartmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(patch("/api/v1/departments/{id}/parent", childDepartmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentDepartmentId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.parentDepartmentId").doesNotExist());
        mockMvc.perform(patch("/api/v1/departments/{id}/parent", childDepartmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentDepartmentId\":\"" + parentDepartmentId + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.parentDepartmentId").value(parentDepartmentId));

        mockMvc.perform(get("/api/v1/countries/{id}/hierarchy", countryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provinces[0].districts[0].officeBuildings[0].floors[0].name")
                        .value(prefix + " Floor Updated"))
                .andExpect(jsonPath("$.data.provinces[0].districts[0].officeBuildings[0].departments[0].children[0].department.name")
                        .value(prefix + " Revenue Updated"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/countries/{id}", countryId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_RESOURCE_CONFLICT"));

        delete("/api/v1/departments/" + childDepartmentId);
        delete("/api/v1/departments/" + parentDepartmentId);
        delete("/api/v1/floors/" + floorId);
        delete("/api/v1/office-buildings/" + buildingId);
        delete("/api/v1/districts/" + districtId);
        delete("/api/v1/provinces/" + provinceId);
        delete("/api/v1/countries/" + countryId);
    }

    @Test
    void requiredFieldsCountryCodesAndTimezonesAreValidated() throws Exception {
        mockMvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors[0].field").value("name"));
        mockMvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "N".repeat(151) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Invalid ISO\",\"isoCode\":\"R1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0].field").value("isoCode"));

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String countryId = create("/api/v1/countries", """
                {"name":"Timezone %s Country","isoCode":"rw"}
                """.formatted(suffix));
        mockMvc.perform(post("/api/v1/countries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Timezone " + suffix + " Country\",\"isoCode\":\"XY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_RESOURCE_CONFLICT"));
        mockMvc.perform(get("/api/v1/countries/{id}", countryId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isoCode").value("RW"));
        String provinceId = create("/api/v1/provinces", """
                {"countryId":"%s","name":"Timezone %s Province"}
                """.formatted(countryId, suffix));
        String districtId = create("/api/v1/districts", """
                {"provinceId":"%s","name":"Timezone %s District"}
                """.formatted(provinceId, suffix));

        mockMvc.perform(post("/api/v1/office-buildings").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"districtId":"%s","name":"Invalid Zone %s","code":"IZ-%s", "timezone":"Mars/Olympus"}
                                """.formatted(districtId, suffix, suffix)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors[0].field").value("timezone"));

        String buildingId = create("/api/v1/office-buildings", """
                {"districtId":"%s","name":"Default Zone %s","code":"DZ-%s","timezone":"   "}
                """.formatted(districtId, suffix, suffix));
        mockMvc.perform(get("/api/v1/office-buildings/{id}", buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("Africa/Kigali"));

        delete("/api/v1/office-buildings/" + buildingId);
        delete("/api/v1/districts/" + districtId);
        delete("/api/v1/provinces/" + provinceId);
        delete("/api/v1/countries/" + countryId);
    }

    @Test
    void invalidPaginationAndSortParametersReturnSharedValidationErrors() throws Exception {
        mockMvc.perform(get("/api/v1/countries").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors").isArray());
        mockMvc.perform(get("/api/v1/countries").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors").isArray());
        mockMvc.perform(get("/api/v1/countries").param("sortDirection", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void activeParentsAndChildrenMustRemainConsistent() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String countryId = create("/api/v1/countries", "{\"name\":\"Lifecycle " + suffix + " Country\"}");
        setActive("/api/v1/countries/" + countryId + "/status", false);

        mockMvc.perform(post("/api/v1/provinces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countryId\":\"" + countryId + "\",\"name\":\"Inactive Parent\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_PARENT_INACTIVE"));
        String provinceId = create("/api/v1/provinces", """
                {"countryId":"%s","name":"Lifecycle %s Province","active":false}
                """.formatted(countryId, suffix));
        mockMvc.perform(patch("/api/v1/provinces/{id}/status", provinceId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_PARENT_INACTIVE"));

        setActive("/api/v1/countries/" + countryId + "/status", true);
        setActive("/api/v1/provinces/" + provinceId + "/status", true);
        String districtId = create("/api/v1/districts", """
                {"provinceId":"%s","name":"Lifecycle %s District"}
                """.formatted(provinceId, suffix));
        mockMvc.perform(patch("/api/v1/countries/{id}/status", countryId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_ACTIVE_CHILDREN"));

        String buildingId = create("/api/v1/office-buildings", """
                {"districtId":"%s","name":"Lifecycle %s Building","code":"LC-%s"}
                """.formatted(districtId, suffix, suffix));
        String floorId = create("/api/v1/floors", """
                {"officeBuildingId":"%s","name":"Lifecycle %s Floor","level":1}
                """.formatted(buildingId, suffix));
        mockMvc.perform(patch("/api/v1/office-buildings/{id}/status", buildingId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_ACTIVE_CHILDREN"));
        setActive("/api/v1/floors/" + floorId + "/status", false);
        setActive("/api/v1/office-buildings/" + buildingId + "/status", false);
        mockMvc.perform(patch("/api/v1/floors/{id}/status", floorId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_PARENT_INACTIVE"));

        delete("/api/v1/floors/" + floorId);
        delete("/api/v1/office-buildings/" + buildingId);
        delete("/api/v1/districts/" + districtId);
        delete("/api/v1/provinces/" + provinceId);
        delete("/api/v1/countries/" + countryId);
    }

    @Test
    void updatesPreserveExplicitNullSemanticsAndRejectInvalidDepartmentSubtreeChanges() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String countryId = create("/api/v1/countries", """
                {"name":"Update %s Country","isoCode":"xy"}
                """.formatted(suffix));
        update("/api/v1/countries/" + countryId, """
                {"name":"Update %s Country","isoCode":""}
                """.formatted(suffix));
        mockMvc.perform(get("/api/v1/countries/{id}", countryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.isoCode").value(org.hamcrest.Matchers.nullValue()));
        String provinceId = create("/api/v1/provinces", """
                {"countryId":"%s","name":"Update %s Province"}
                """.formatted(countryId, suffix));
        String districtId = create("/api/v1/districts", """
                {"provinceId":"%s","name":"Update %s District"}
                """.formatted(provinceId, suffix));
        String buildingId = create("/api/v1/office-buildings", """
                {"districtId":"%s","name":"Update %s Building","code":"UP-%s",
                 "address":"Original address","timezone":"Europe/Paris","workingCalendarId":"%s"}
                """.formatted(districtId, suffix, suffix, UUID.randomUUID()));

        setActive("/api/v1/office-buildings/" + buildingId + "/status", false);
        update("/api/v1/office-buildings/" + buildingId, """
                {"districtId":"%s","name":"Updated %s Building","code":"UP-%s",
                 "address":"   ","timezone":""}
                """.formatted(districtId, suffix, suffix));
        mockMvc.perform(get("/api/v1/office-buildings/{id}", buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.timezone").value("Africa/Kigali"))
                .andExpect(jsonPath("$.data.address").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.workingCalendarId").value(org.hamcrest.Matchers.nullValue()));

        setActive("/api/v1/office-buildings/" + buildingId + "/status", true);
        String otherBuildingId = create("/api/v1/office-buildings", """
                {"districtId":"%s","name":"Other %s Building","code":"OTHER-%s"}
                """.formatted(districtId, suffix, suffix));
        String parentId = create("/api/v1/departments", """
                {"officeBuildingId":"%s","name":"Update %s Department","code":"UPD-%s","status":"ACTIVE"}
                """.formatted(buildingId, suffix, suffix));
        String childId = create("/api/v1/departments", """
                {"officeBuildingId":"%s","parentDepartmentId":"%s","name":"Update %s Child",
                 "code":"UPC-%s","status":"ACTIVE"}
                """.formatted(buildingId, parentId, suffix, suffix));

        mockMvc.perform(put("/api/v1/departments/{id}", parentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"officeBuildingId":"%s","name":"Moved %s Department",
                                 "code":"UPD-%s","status":"ACTIVE"}
                                """.formatted(otherBuildingId, suffix, suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_SCOPE_HAS_CHILDREN"));
        mockMvc.perform(put("/api/v1/departments/{id}", parentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"officeBuildingId":"%s","name":"Inactive Parent %s",
                                 "code":"UPD-%s","status":"INACTIVE"}
                                """.formatted(buildingId, suffix, suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_ACTIVE_CHILDREN"));
        mockMvc.perform(get("/api/v1/departments/{id}", parentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.officeBuildingId").value(buildingId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        delete("/api/v1/departments/" + childId);
        delete("/api/v1/departments/" + parentId);
        delete("/api/v1/office-buildings/" + otherBuildingId);
        delete("/api/v1/office-buildings/" + buildingId);
        delete("/api/v1/districts/" + districtId);
        delete("/api/v1/provinces/" + provinceId);
        delete("/api/v1/countries/" + countryId);
    }

    private String create(String path, String request) throws Exception {
        MvcResult result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("id").asText();
    }

    private void update(String path, String request) throws Exception {
        MvcResult result = mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(request))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("PUT " + path + " returned " + result.getResponse().getStatus()
                    + ": " + result.getResponse().getContentAsString());
        }
    }

    private void setActive(String path, boolean active) throws Exception {
        mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":" + active + "}"))
                .andExpect(status().isOk());
    }

    private void delete(String path) throws Exception {
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path))
                                .andExpect(status().isOk());
    }
}