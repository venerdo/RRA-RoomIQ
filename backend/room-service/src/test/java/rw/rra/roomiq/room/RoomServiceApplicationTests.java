package rw.rra.roomiq.room;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.postgresql.util.PGobject;
import tools.jackson.databind.JsonNode;
import rw.rra.roomiq.room.domain.dto.FacilityTypeDto;
import rw.rra.roomiq.room.domain.dto.MaintenancePeriodDto;
import rw.rra.roomiq.room.domain.dto.RoomDto;
import rw.rra.roomiq.room.domain.entity.FacilityState;
import rw.rra.roomiq.room.domain.entity.FacilityType;
import rw.rra.roomiq.room.domain.entity.MaintenancePeriod;
import rw.rra.roomiq.room.domain.entity.Room;
import rw.rra.roomiq.room.domain.entity.RoomClass;
import rw.rra.roomiq.room.domain.entity.RoomFacility;
import rw.rra.roomiq.room.domain.entity.RoomPhoto;
import rw.rra.roomiq.room.domain.entity.RoomRule;
import rw.rra.roomiq.room.domain.entity.RoomRuleAllowedDepartment;
import rw.rra.roomiq.room.domain.entity.RoomStatus;
import rw.rra.roomiq.room.domain.entity.RoomStatusHistory;
import rw.rra.roomiq.room.domain.entity.RoomType;
import rw.rra.roomiq.room.domain.mapper.RoomMapper;
import rw.rra.roomiq.room.domain.repository.FacilityTypeRepository;
import rw.rra.roomiq.room.domain.repository.MaintenancePeriodRepository;
import rw.rra.roomiq.room.domain.repository.RoomFacilityRepository;
import rw.rra.roomiq.room.domain.repository.RoomPhotoRepository;
import rw.rra.roomiq.room.domain.repository.RoomRepository;
import rw.rra.roomiq.room.domain.repository.RoomRuleAllowedDepartmentRepository;
import rw.rra.roomiq.room.domain.repository.RoomRuleRepository;
import rw.rra.roomiq.room.domain.repository.RoomStatusHistoryRepository;
import rw.rra.roomiq.room.domain.repository.RoomTypeRepository;
import rw.rra.roomiq.room.integration.OrganizationDirectoryClient;
import rw.rra.roomiq.room.integration.CloudinaryPhotoStorage;
import rw.rra.roomiq.room.integration.RoomPhotoAuthorizationClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.dao.DataIntegrityViolationException;
import rw.rra.roomiq.common.web.DomainException;
import org.springframework.http.HttpStatus;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RoomServiceApplicationTests {
    private static final UUID AUTHORIZED_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000654");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("room.photos.max-file-size-bytes", () -> 1024);
        registry.add("room.photos.max-count", () -> 2);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private FacilityTypeRepository facilityTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomFacilityRepository roomFacilityRepository;

    @Autowired
    private RoomPhotoRepository roomPhotoRepository;

    @Autowired
    private RoomRuleRepository roomRuleRepository;

    @Autowired
    private RoomRuleAllowedDepartmentRepository roomRuleAllowedDepartmentRepository;

    @Autowired
    private RoomStatusHistoryRepository roomStatusHistoryRepository;

    @Autowired
    private MaintenancePeriodRepository maintenancePeriodRepository;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrganizationDirectoryClient organizationDirectoryClient;

    @MockitoBean
    private CloudinaryPhotoStorage cloudinaryPhotoStorage;

    @MockitoBean
    private RoomPhotoAuthorizationClient roomPhotoAuthorizationClient;

    @BeforeEach
    void allowOrganizationReferencesInIsolatedApiTests() {
        doNothing().when(organizationDirectoryClient)
                .validateBuildingAndFloor(any(UUID.class), any(UUID.class));
        when(roomPhotoAuthorizationClient.authorizeRoomManagement(any(UUID.class)))
            .thenReturn(AUTHORIZED_ACTOR_ID);
        when(roomPhotoAuthorizationClient.authorizeRoomManagement(org.mockito.ArgumentMatchers.isNull()))
            .thenReturn(AUTHORIZED_ACTOR_ID);
        when(cloudinaryPhotoStorage.upload(any(UUID.class), any(byte[].class), anyString(), anyString()))
            .thenReturn(new CloudinaryPhotoStorage.StoredPhoto(
                "roomiq/rooms/test/photo-1", "https://res.cloudinary.com/example/photo-1.png"));
    }

    @Test
    void contextLoads() {
    }

    @Test
    void roomOpenApiDocumentsAllOperationsAndSharedSecurityContract() throws Exception {
        String response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode document = objectMapper.readTree(response);

        assertThat(document.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http");
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(document.at("/components/schemas/ApiError").isObject()).isTrue();
        JsonNode paths = document.path("paths");
        Set<String> actualOperations = new HashSet<>();
        for (var pathEntry : paths.properties()) {
            String path = pathEntry.getKey();
            if (!path.startsWith("/api/v1/")) {
                continue;
            }
            for (var operationEntry : pathEntry.getValue().properties()) {
                JsonNode operation = operationEntry.getValue();
                if (!operation.has("operationId")) {
                    continue;
                }
                actualOperations.add(operationEntry.getKey().toUpperCase() + " " + path);
                assertThat(operation.path("security").toString()).contains("bearerAuth");
                assertThat(operation.path("parameters").toString()).contains("X-Correlation-ID");
                JsonNode responses = operation.path("responses");
                String successStatus = responses.has("201") ? "201" : "200";
                assertThat(responses.path(successStatus).path("content").path("application/json")
                        .path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/ApiResponse");
                for (String errorStatus : Set.of("400", "401", "403", "404", "409", "500", "502", "503")) {
                    assertThat(responses.path(errorStatus).path("content").path("application/json")
                            .path("schema").path("$ref").asText())
                            .as("error schema for %s %s %s", errorStatus, operationEntry.getKey(), path)
                            .isEqualTo("#/components/schemas/ApiError");
                }
                        assertThat(responses.path("409").path("description").asText())
                            .contains("ROOM_NAME_DUPLICATE", "ROOM_STATUS_TRANSITION_INVALID");
                        assertThat(responses.path("404").path("description").asText()).contains("ROOM_NOT_FOUND");
                        assertThat(responses.path("503").path("description").asText())
                            .contains("IDENTITY_SERVICE_UNAVAILABLE", "ORGANIZATION_SERVICE_UNAVAILABLE");
                assertThat(responses.path(successStatus).path("headers").has("X-Correlation-ID")).isTrue();
            }
        }
        assertThat(actualOperations).containsExactlyInAnyOrderElementsOf(roomApiOperations());
        assertThat(actualOperations).hasSize(31);
    }

    @Test
    void validationAndDomainErrorsUseSharedApiErrorAndCorrelationId() throws Exception {
        mockMvc.perform(post("/api/v1/rooms")
                        .header("X-Correlation-ID", "s4-10-validation")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("s4-10-validation"))
                .andExpect(header().string("X-Correlation-ID", "s4-10-validation"));
    }

    private Set<String> roomApiOperations() {
        return Set.of(
                "GET /api/v1/rooms", "POST /api/v1/rooms", "GET /api/v1/rooms/{id}",
                "PUT /api/v1/rooms/{id}", "PATCH /api/v1/rooms/{id}/status",
                "GET /api/v1/rooms/{id}/status-history", "DELETE /api/v1/rooms/{id}",
                "GET /api/v1/room-types", "POST /api/v1/room-types", "GET /api/v1/room-types/{id}",
                "PUT /api/v1/room-types/{id}", "GET /api/v1/facility-types", "POST /api/v1/facility-types",
                "GET /api/v1/facility-types/{id}", "PUT /api/v1/facility-types/{id}",
                "GET /api/v1/rooms/{roomId}/facilities", "POST /api/v1/rooms/{roomId}/facilities",
                "PUT /api/v1/rooms/{roomId}/facilities/{assignmentId}",
                "GET /api/v1/rooms/{roomId}/rules", "POST /api/v1/rooms/{roomId}/rules",
                "GET /api/v1/office-buildings/{officeBuildingId}/room-rules",
                "POST /api/v1/office-buildings/{officeBuildingId}/room-rules",
                "GET /api/v1/room-rules/{ruleId}", "PUT /api/v1/room-rules/{ruleId}",
                "GET /api/v1/rooms/{roomId}/photos", "POST /api/v1/rooms/{roomId}/photos",
                "PUT /api/v1/rooms/{roomId}/photos/{photoId}",
                "DELETE /api/v1/rooms/{roomId}/photos/{photoId}",
                "GET /api/v1/rooms/{roomId}/maintenance-periods",
                "POST /api/v1/rooms/{roomId}/maintenance-periods",
                "DELETE /api/v1/rooms/{roomId}/maintenance-periods/{maintenancePeriodId}");
    }

    @Test
    void roomApisSupportDynamicCatalogsAndRoomCrudWithFilteringAndPaging() throws Exception {
        String roomTypeJson = objectMapper.writeValueAsString(Map.of(
            "code", "RT-API",
            "name", "API Boardroom",
            "active", true));

        mockMvc.perform(post("/api/v1/room-types")
                .contentType(APPLICATION_JSON)
                .content(roomTypeJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.code").value("RT-API"));

        String facilityTypeJson = objectMapper.writeValueAsString(Map.of(
            "code", "FT-API",
            "name", "Projector",
            "category", "AV",
            "active", true));

        mockMvc.perform(post("/api/v1/facility-types")
                .contentType(APPLICATION_JSON)
                .content(facilityTypeJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.category").value("AV"));

        String roomJson = objectMapper.writeValueAsString(Map.of(
            "floorId", UUID.randomUUID(),
            "officeBuildingId", UUID.randomUUID(),
            "roomTypeCode", "RT-API",
            "name", "Boardroom 1",
            "code", "BR-001",
            "description", "Meeting room",
            "capacity", 12,
            "roomClass", "NORMAL",
            "status", "AVAILABLE"));

        mockMvc.perform(post("/api/v1/rooms")
            .header("Authorization", "Bearer test-token")
                .contentType(APPLICATION_JSON)
                .content(roomJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.code").value("BR-001"));

        mockMvc.perform(get("/api/v1/rooms")
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "code")
                .param("sortDirection", "ASC")
                .param("search", "BR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/room-types")
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "name")
                .param("sortDirection", "ASC"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].code").value("RT-API"));

        mockMvc.perform(put("/api/v1/rooms/{id}", "00000000-0000-0000-0000-000000000001")
            .header("Authorization", "Bearer test-token")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "floorId", UUID.randomUUID(),
                    "officeBuildingId", UUID.randomUUID(),
                    "roomTypeCode", "RT-API",
                    "name", "Updated room",
                    "code", "BR-002",
                    "description", "Updated description",
                    "capacity", 20,
                    "roomClass", "VIP",
                    "status", "MAINTENANCE"))))
            .andExpect(status().isNotFound());
    }

            @Test
            void deniedRoomAndCatalogMutationsDoNotPersistOrCallOrganization() throws Exception {
            UUID buildingId = UUID.randomUUID();
            UUID floorId = UUID.randomUUID();
            doThrow(new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Forbidden"))
                .when(roomPhotoAuthorizationClient).authorizeRoomManagement(buildingId);

            mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer caller-token")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "floorId", floorId,
                    "officeBuildingId", buildingId,
                    "roomTypeCode", "AUTHZ-TYPE",
                    "name", "Denied Room",
                    "code", "DENIED-01",
                    "capacity", 8,
                    "roomClass", "NORMAL",
                    "status", "AVAILABLE"))))
                .andExpect(status().isForbidden());

            assertThat(roomRepository.existsByOfficeBuildingIdAndCodeIgnoreCase(buildingId, "DENIED-01")).isFalse();
            verify(organizationDirectoryClient, org.mockito.Mockito.never())
                .validateBuildingAndFloor(any(UUID.class), any(UUID.class));

            doThrow(new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Global admin is required"))
                .when(roomPhotoAuthorizationClient).authorizeRoomManagement(org.mockito.ArgumentMatchers.isNull());
            mockMvc.perform(post("/api/v1/room-types")
                .header("Authorization", "Bearer caller-token")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", "DENIED-TYPE",
                    "name", "Denied type",
                    "active", true))))
                .andExpect(status().isForbidden());
            assertThat(roomTypeRepository.findByCodeIgnoreCase("DENIED-TYPE")).isEmpty();
            }

            @Test
            void movingRoomRequiresAuthorizationForSourceAndDestinationBuildings() throws Exception {
            UUID sourceBuildingId = UUID.randomUUID();
            UUID destinationBuildingId = UUID.randomUUID();
            UUID roomTypeId = createRoomType();
            UUID roomId = createRoom(sourceBuildingId, roomTypeId, "Scoped Room", "SCOPE-01");
            doThrow(new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Destination scope denied"))
                .when(roomPhotoAuthorizationClient).authorizeRoomManagement(destinationBuildingId);

            mockMvc.perform(put("/api/v1/rooms/{id}", roomId)
                .header("Authorization", "Bearer caller-token")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "floorId", UUID.randomUUID(),
                    "officeBuildingId", destinationBuildingId,
                    "roomTypeId", roomTypeId,
                    "name", "Moved Room",
                    "code", "SCOPE-02",
                    "capacity", 8,
                    "roomClass", "NORMAL",
                    "status", "AVAILABLE"))))
                .andExpect(status().isForbidden());

            assertThat(roomRepository.findById(roomId).orElseThrow().getOfficeBuildingId())
                .isEqualTo(sourceBuildingId);
            verify(roomPhotoAuthorizationClient).authorizeRoomManagement(sourceBuildingId);
            verify(roomPhotoAuthorizationClient).authorizeRoomManagement(destinationBuildingId);
            verify(organizationDirectoryClient, org.mockito.Mockito.never())
                .validateBuildingAndFloor(any(UUID.class), any(UUID.class));
            }

    @Test
    void flywayCreatesRoomResourceTablesAndPostgresIntegrityObjects() {
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2', '3') AND success = TRUE", Integer.class))
            .isEqualTo(3);
        assertThat(tableNames()).contains("room", "room_type", "facility_type", "room_facility", "room_photo",
                "room_rule", "room_rule_allowed_department", "room_status_history", "maintenance_period");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conname IN ('uq_room_building_name', 'uq_room_building_code',
                    'ck_room_capacity_positive', 'ex_maintenance_period_room_overlap')
            """, Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM pg_indexes
            WHERE indexname IN ('uq_room_building_name_ci', 'uq_room_building_code_ci')
            """, Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM pg_constraint WHERE conname = 'ck_room_rule_exactly_one_scope'
            """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT udt_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'maintenance_period' AND column_name = 'period'
                """, String.class)).isEqualTo("tstzrange");
    }

    @Test
    void onlyRoomOwnedReferencesHaveDatabaseForeignKeys() throws SQLException {
        assertThat(importedForeignKeyColumns("room")).containsExactly("room_type_id");
        assertThat(importedForeignKeyColumns("room_facility")).containsExactlyInAnyOrder("room_id", "facility_type_id");
        assertThat(importedForeignKeyColumns("room_rule")).containsExactly("room_id");
        assertThat(importedForeignKeyColumns("room_rule_allowed_department")).containsExactly("room_rule_id");
        assertThat(importedForeignKeyColumns("room_status_history")).containsExactly("room_id");
        assertThat(importedForeignKeyColumns("maintenance_period")).containsExactly("room_id");
    }

    @Test
    void roomCapacityAndBuildingScopedUniquenessAreEnforced() {
        UUID typeId = createRoomType();
        UUID buildingId = UUID.randomUUID();
        insertRoom(UUID.randomUUID(), buildingId, typeId, "Boardroom", "B-01", 12);

        assertThatThrownBy(() -> insertRoom(UUID.randomUUID(), buildingId, typeId, "Another Room", "B-01", 8))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRoom(UUID.randomUUID(), buildingId, typeId, "boardroom", "B-02", 8))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRoom(UUID.randomUUID(), buildingId, typeId, "Other Room", "b-01", 8))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRoom(UUID.randomUUID(), UUID.randomUUID(), typeId, "Other Room", "B-02", 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertRoom(UUID.randomUUID(), UUID.randomUUID(), typeId, "Boardroom", "B-01", 6);
    }

    @Test
    void roomApiRejectsMissingFieldsNonpositiveCapacityAndUnsupportedEnums() throws Exception {
        String validBase = "\"floorId\":\"" + UUID.randomUUID() + "\",\"officeBuildingId\":\""
            + UUID.randomUUID() + "\",\"roomTypeCode\":\"RT-API\",\"code\":\"R-01\",";

        mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer test-token")
                .contentType(APPLICATION_JSON)
            .content("{" + validBase + "\"capacity\":1,\"roomClass\":\"NORMAL\",\"status\":\"AVAILABLE\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer test-token")
                .contentType(APPLICATION_JSON)
            .content("{" + validBase + "\"name\":\"Room\",\"capacity\":0,\"roomClass\":\"NORMAL\",\"status\":\"AVAILABLE\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer test-token")
                .contentType(APPLICATION_JSON)
            .content("{" + validBase + "\"name\":\"Room\",\"capacity\":1,\"roomClass\":\"CONFERENCE\",\"status\":\"AVAILABLE\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/rooms")
                .header("Authorization", "Bearer test-token")
                .contentType(APPLICATION_JSON)
                .content("{" + validBase + "\"name\":\"Room\",\"capacity\":1,\"roomClass\":\"NORMAL\",\"status\":\"OPEN\"}"))
            .andExpect(status().isBadRequest());
    }

        @Test
        void roomApiEnforcesCaseInsensitiveBuildingScopedNameAndCodeUniqueness() throws Exception {
        RoomType roomType = roomTypeRepository.findById(createRoomType()).orElseThrow();
        UUID buildingId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        roomRepository.saveAndFlush(new Room(roomType, floorId, buildingId, "Boardroom", "BR-01",
            null, 12, RoomClass.NORMAL, RoomStatus.AVAILABLE));

        mockMvc.perform(post("/api/v1/rooms")
            .header("Authorization", "Bearer test-token")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "floorId", floorId, "officeBuildingId", buildingId, "roomTypeId", roomType.getId(),
                "name", "boardroom", "code", "BR-02", "capacity", 8,
                "roomClass", "NORMAL", "status", "AVAILABLE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NAME_DUPLICATE"));

        mockMvc.perform(post("/api/v1/rooms")
            .header("Authorization", "Bearer test-token")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "floorId", floorId, "officeBuildingId", buildingId, "roomTypeId", roomType.getId(),
                "name", "Other room", "code", "br-01", "capacity", 8,
                "roomClass", "NORMAL", "status", "AVAILABLE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_CODE_DUPLICATE"));

        mockMvc.perform(post("/api/v1/rooms")
            .header("Authorization", "Bearer test-token")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "floorId", UUID.randomUUID(), "officeBuildingId", UUID.randomUUID(),
                "roomTypeId", roomType.getId(), "name", "Boardroom", "code", "BR-01",
                "capacity", 8, "roomClass", "NORMAL", "status", "AVAILABLE"))))
            .andExpect(status().isCreated());

        UUID sourceBuildingId = UUID.randomUUID();
        UUID targetBuildingId = UUID.randomUUID();
        UUID sourceFloorId = UUID.randomUUID();
        UUID targetFloorId = UUID.randomUUID();
        Room source = roomRepository.saveAndFlush(new Room(roomType, sourceFloorId, sourceBuildingId,
            "Move collision", "MOVE-01", null, 4, RoomClass.NORMAL, RoomStatus.AVAILABLE));
        roomRepository.saveAndFlush(new Room(roomType, targetFloorId, targetBuildingId,
            "Move collision", "MOVE-01", null, 4, RoomClass.NORMAL, RoomStatus.AVAILABLE));

        mockMvc.perform(put("/api/v1/rooms/{id}", source.getId())
            .header("Authorization", "Bearer test-token")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "floorId", targetFloorId, "officeBuildingId", targetBuildingId,
                "roomTypeId", roomType.getId(), "name", "Move collision", "code", "MOVE-01",
                "capacity", 4, "roomClass", "NORMAL", "status", "AVAILABLE"))))
            .andExpect(status().isConflict());
        }

        @Test
        void roomFacilityApisManageAssignmentsAndRejectInvalidOrDuplicateValues() throws Exception {
        RoomType roomType = roomTypeRepository.findById(createRoomType()).orElseThrow();
        Room room = roomRepository.saveAndFlush(new Room(roomType, UUID.randomUUID(), UUID.randomUUID(),
            "Facility API room", "FAC-ROOM", null, 8, RoomClass.NORMAL, RoomStatus.AVAILABLE));
        FacilityType projector = facilityTypeRepository.saveAndFlush(
            new FacilityType("PROJ-" + UUID.randomUUID(), "Projector", "AV", true));
        FacilityType screen = facilityTypeRepository.saveAndFlush(
            new FacilityType("SCREEN-" + UUID.randomUUID(), "Screen", "AV", true));
        FacilityType inactive = facilityTypeRepository.saveAndFlush(
            new FacilityType("INACTIVE-" + UUID.randomUUID(), "Inactive", "AV", false));
        String servicedAt = "2026-10-06T09:30:00Z";

        String assignmentJson = objectMapper.writeValueAsString(Map.of(
            "facilityTypeId", projector.getId(),
            "quantity", 2,
            "state", "WORKING",
            "lastServicedAt", servicedAt));
        String response = mockMvc.perform(post("/api/v1/rooms/{roomId}/facilities", room.getId())
            .contentType(APPLICATION_JSON)
            .content(assignmentJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.quantity").value(2))
            .andExpect(jsonPath("$.data.state").value("WORKING"))
            .andExpect(jsonPath("$.data.lastServicedAt").value(servicedAt))
            .andReturn().getResponse().getContentAsString();
        UUID assignmentId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());

        mockMvc.perform(get("/api/v1/rooms/{roomId}/facilities", room.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].facilityTypeId").value(projector.getId().toString()));

        mockMvc.perform(put("/api/v1/rooms/{roomId}/facilities/{assignmentId}", room.getId(), assignmentId)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "facilityTypeId", projector.getId(),
                "quantity", 3,
                "state", "FAULTY",
                "lastServicedAt", "2026-10-07T10:00:00Z"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.quantity").value(3))
            .andExpect(jsonPath("$.data.state").value("FAULTY"));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/facilities", room.getId())
            .contentType(APPLICATION_JSON)
            .content(assignmentJson))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROOM_FACILITY_DUPLICATE"));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/facilities", room.getId())
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "facilityTypeId", inactive.getId(), "quantity", 1, "state", "WORKING"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FACILITY_TYPE_INACTIVE"));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/facilities", room.getId())
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "facilityTypeId", projector.getId(), "quantity", 0, "state", "WORKING"))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/facilities", room.getId())
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "facilityTypeId", projector.getId(), "quantity", 1, "state", "BROKEN"))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/facilities", UUID.randomUUID())
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "facilityTypeId", projector.getId(), "quantity", 1, "state", "WORKING"))))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/facilities", room.getId())
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "facilityTypeId", UUID.randomUUID(), "quantity", 1, "state", "WORKING"))))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/facilities", room.getId())
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "facilityTypeId", screen.getId(), "quantity", 1, "state", "WORKING"))))
            .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/rooms/{roomId}/facilities/{assignmentId}", room.getId(), assignmentId)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "facilityTypeId", screen.getId(), "quantity", 1, "state", "WORKING"))))
            .andExpect(status().isConflict());
        }

            @Test
            void roomRuleApisManageRoomSpecificAndBuildingDefaultPolicies() throws Exception {
            RoomType roomType = roomTypeRepository.findById(createRoomType()).orElseThrow();
            UUID buildingId = UUID.randomUUID();
            Room room = roomRepository.saveAndFlush(new Room(roomType, UUID.randomUUID(), buildingId,
                "Rule API room", "RULE-01", null, 10, RoomClass.NORMAL, RoomStatus.AVAILABLE));
            UUID departmentA = UUID.randomUUID();
            UUID departmentB = UUID.randomUUID();
            String effectiveFrom = "2026-10-07T09:00:00Z";

            String roomRuleJson = objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("minDurationMinutes", 30),
                Map.entry("maxDurationMinutes", 240),
                Map.entry("minAdvanceMinutes", 60),
                Map.entry("maxAdvanceDays", 30),
                Map.entry("cancellationDeadlineMinutes", 15),
                Map.entry("recurringAllowed", true),
                Map.entry("externalGuestsAllowed", false),
                Map.entry("approvalRequired", true),
                Map.entry("outsideHoursAllowed", false),
                Map.entry("active", true),
                Map.entry("effectiveFrom", effectiveFrom),
                Map.entry("allowedDepartmentIds", List.of(departmentA))));
            String roomRuleResponse = mockMvc.perform(post("/api/v1/rooms/{roomId}/rules", room.getId())
                .contentType(APPLICATION_JSON)
                .content(roomRuleJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.data.officeBuildingId").value(nullValue()))
                .andExpect(jsonPath("$.data.releaseBufferMinutes").value(5))
                .andExpect(jsonPath("$.data.allowedDepartmentIds[0]").value(departmentA.toString()))
                .andExpect(jsonPath("$.data.effectiveFrom").value(effectiveFrom))
                .andReturn().getResponse().getContentAsString();
            UUID roomRuleId = UUID.fromString(objectMapper.readTree(roomRuleResponse).path("data").path("id").asText());

            mockMvc.perform(get("/api/v1/rooms/{roomId}/rules", room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

            mockMvc.perform(put("/api/v1/room-rules/{ruleId}", roomRuleId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "maxDurationMinutes", 180,
                    "minAdvanceMinutes", 30,
                    "maxAdvanceDays", 14,
                    "recurringAllowed", false,
                    "externalGuestsAllowed", true,
                    "approvalRequired", false,
                    "outsideHoursAllowed", true,
                    "releaseBufferMinutes", 8,
                    "active", false,
                    "allowedDepartmentIds", List.of(departmentB)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxDurationMinutes").value(180))
                .andExpect(jsonPath("$.data.releaseBufferMinutes").value(8))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.allowedDepartmentIds[0]").value(departmentB.toString()));

            String buildingRuleJson = objectMapper.writeValueAsString(Map.ofEntries(
                    Map.entry("minDurationMinutes", 15),
                    Map.entry("maxDurationMinutes", 120),
                    Map.entry("minAdvanceMinutes", 0),
                    Map.entry("maxAdvanceDays", 90),
                    Map.entry("cancellationDeadlineMinutes", 0),
                    Map.entry("recurringAllowed", false),
                    Map.entry("externalGuestsAllowed", false),
                    Map.entry("approvalRequired", false),
                    Map.entry("outsideHoursAllowed", false),
                    Map.entry("active", true),
                    Map.entry("allowedDepartmentIds", List.of())));
            mockMvc.perform(post("/api/v1/office-buildings/{officeBuildingId}/room-rules", buildingId)
                .contentType(APPLICATION_JSON)
                .content(buildingRuleJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomId").value(nullValue()))
                .andExpect(jsonPath("$.data.officeBuildingId").value(buildingId.toString()));

            mockMvc.perform(get("/api/v1/office-buildings/{officeBuildingId}/room-rules", buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
            }

            @Test
            void roomRuleApiRejectsInvalidPolicyBoundsAndDuplicateDepartments() throws Exception {
            UUID roomId = createRoom(UUID.randomUUID(), createRoomType(), "Invalid rules room", "RULE-INVALID");
            Map<String, Object> policy = Map.of(
                "minDurationMinutes", 60,
                "maxDurationMinutes", 30,
                "minAdvanceMinutes", 0,
                "maxAdvanceDays", 1,
                "recurringAllowed", false,
                "externalGuestsAllowed", false,
                "approvalRequired", false,
                "outsideHoursAllowed", false,
                "active", true,
                "allowedDepartmentIds", List.of());

            mockMvc.perform(post("/api/v1/rooms/{roomId}/rules", roomId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(policy)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROOM_RULE_DURATION_BOUNDS"));

            Map<String, Object> invalidAdvance = Map.of(
                "minDurationMinutes", 15,
                "maxDurationMinutes", 30,
                "minAdvanceMinutes", 1441,
                "maxAdvanceDays", 1,
                "recurringAllowed", false,
                "externalGuestsAllowed", false,
                "approvalRequired", false,
                "outsideHoursAllowed", false,
                "active", true,
                "allowedDepartmentIds", List.of());
            mockMvc.perform(post("/api/v1/rooms/{roomId}/rules", roomId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidAdvance)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROOM_RULE_ADVANCE_BOUNDS"));

            UUID departmentId = UUID.randomUUID();
            Map<String, Object> duplicateDepartments = new java.util.HashMap<>(invalidAdvance);
            duplicateDepartments.put("minAdvanceMinutes", 0);
            duplicateDepartments.put("allowedDepartmentIds", List.of(departmentId, departmentId));
            mockMvc.perform(post("/api/v1/rooms/{roomId}/rules", roomId)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateDepartments)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROOM_RULE_DUPLICATE_DEPARTMENT"));

            mockMvc.perform(post("/api/v1/rooms/{roomId}/rules", UUID.randomUUID())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidAdvance)))
                .andExpect(status().isNotFound());
            }

            @Test
            void roomRuleScopeConstraintRejectsRulesWithoutExactlyOneScope() {
            assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO room_rule (id, recurring_allowed, external_guests_allowed, approval_required,
                    outside_hours_allowed, release_buffer_minutes, is_active)
                VALUES (?, FALSE, FALSE, FALSE, FALSE, 5, TRUE)
                """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
            }

                @Test
                void roomPhotoApisUploadUpdateListAndDeleteCloudinaryAssets() throws Exception {
                RoomType roomType = roomTypeRepository.findById(createRoomType()).orElseThrow();
                UUID buildingId = UUID.randomUUID();
                Room room = roomRepository.saveAndFlush(new Room(roomType, UUID.randomUUID(), buildingId,
                    "Photo room", "PHOTO-01", null, 8, RoomClass.NORMAL, RoomStatus.AVAILABLE));
                byte[] pngSignature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
                MockMultipartFile file = new MockMultipartFile("file", "room.png", "image/png", pngSignature);

                String response = mockMvc.perform(multipart("/api/v1/rooms/{roomId}/photos", room.getId())
                    .file(file)
                    .param("sortOrder", "0")
                    .param("primary", "true")
                    .param("approvedForPublic", "true")
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.cloudinaryPublicId").value("roomiq/rooms/test/photo-1"))
                    .andExpect(jsonPath("$.data.secureUrl").value("https://res.cloudinary.com/example/photo-1.png"))
                    .andExpect(jsonPath("$.data.sortOrder").value(0))
                    .andExpect(jsonPath("$.data.primary").value(true))
                    .andExpect(jsonPath("$.data.approvedForPublic").value(true))
                    .andExpect(jsonPath("$.data.createdAt").exists())
                    .andReturn().getResponse().getContentAsString();
                UUID photoId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());

                verify(cloudinaryPhotoStorage).upload(eq(room.getId()), eq(pngSignature), eq("room.png"), eq("image/png"));
                verify(roomPhotoAuthorizationClient).authorizeRoomManagement(buildingId);

                mockMvc.perform(get("/api/v1/rooms/{roomId}/photos", room.getId())
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));

                mockMvc.perform(put("/api/v1/rooms/{roomId}/photos/{photoId}", room.getId(), photoId)
                    .header("Authorization", "Bearer test-token")
                    .contentType(APPLICATION_JSON)
                    .content("{\"sortOrder\":1,\"primary\":false,\"approvedForPublic\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sortOrder").value(1))
                    .andExpect(jsonPath("$.data.primary").value(false))
                    .andExpect(jsonPath("$.data.approvedForPublic").value(false));

                mockMvc.perform(delete("/api/v1/rooms/{roomId}/photos/{photoId}", room.getId(), photoId)
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
                assertThat(roomPhotoRepository.existsById(photoId)).isFalse();
                verify(cloudinaryPhotoStorage).delete("roomiq/rooms/test/photo-1");
                verify(roomPhotoAuthorizationClient, times(4)).authorizeRoomManagement(buildingId);
                }

                @Test
                void roomPhotoUploadRejectsUnsupportedSpoofedOversizedAndExcessPhotos() throws Exception {
                RoomType roomType = roomTypeRepository.findById(createRoomType()).orElseThrow();
                Room room = roomRepository.saveAndFlush(new Room(roomType, UUID.randomUUID(), UUID.randomUUID(),
                    "Photo validation room", "PHOTO-VALIDATION", null, 8, RoomClass.NORMAL, RoomStatus.AVAILABLE));

                mockMvc.perform(multipart("/api/v1/rooms/{roomId}/photos", room.getId())
                    .file(new MockMultipartFile("file", "photo.gif", "image/gif", new byte[]{1, 2, 3}))
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ROOM_PHOTO_TYPE_UNSUPPORTED"));

                mockMvc.perform(multipart("/api/v1/rooms/{roomId}/photos", room.getId())
                    .file(new MockMultipartFile("file", "spoof.png", "image/png", new byte[]{1, 2, 3}))
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ROOM_PHOTO_TYPE_UNSUPPORTED"));

                byte[] oversized = new byte[1025];
                oversized[0] = (byte) 0x89;
                oversized[1] = 0x50;
                oversized[2] = 0x4e;
                oversized[3] = 0x47;
                oversized[4] = 0x0d;
                oversized[5] = 0x0a;
                oversized[6] = 0x1a;
                oversized[7] = 0x0a;
                mockMvc.perform(multipart("/api/v1/rooms/{roomId}/photos", room.getId())
                    .file(new MockMultipartFile("file", "large.png", "image/png", oversized))
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ROOM_PHOTO_TOO_LARGE"));

                roomPhotoRepository.saveAllAndFlush(List.of(
                    new RoomPhoto(room, "existing/photo-a", "https://res.cloudinary.com/test/photo-a", (short) 0, false, false),
                    new RoomPhoto(room, "existing/photo-b", "https://res.cloudinary.com/test/photo-b", (short) 1, false, false)));
                mockMvc.perform(multipart("/api/v1/rooms/{roomId}/photos", room.getId())
                    .file(new MockMultipartFile("file", "valid.png", "image/png",
                        new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}))
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ROOM_PHOTO_LIMIT_EXCEEDED"));
                }

                @Test
                void roomPhotoUploadStopsBeforeProviderWhenIdentityDeniesAccess() throws Exception {
                RoomType roomType = roomTypeRepository.findById(createRoomType()).orElseThrow();
                Room room = roomRepository.saveAndFlush(new Room(roomType, UUID.randomUUID(), UUID.randomUUID(),
                    "Denied photo room", "PHOTO-DENIED", null, 8, RoomClass.NORMAL, RoomStatus.AVAILABLE));
                doThrow(new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Forbidden"))
                    .when(roomPhotoAuthorizationClient).authorizeRoomManagement(any(UUID.class));

                mockMvc.perform(multipart("/api/v1/rooms/{roomId}/photos", room.getId())
                    .file(new MockMultipartFile("file", "room.png", "image/png",
                        new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}))
                    .header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden());
                verify(cloudinaryPhotoStorage, org.mockito.Mockito.never())
                    .upload(any(UUID.class), any(byte[].class), anyString(), anyString());
                }

        @Test
        void photoProviderFailuresUseTheSharedErrorAndCorrelationContract() throws Exception {
        UUID roomId = createRoom(UUID.randomUUID(), createRoomType(), "Unavailable photo room", "PHOTO-UNAVAILABLE");
        doThrow(new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "CLOUDINARY_UNAVAILABLE",
                "Photo storage is unavailable"))
            .when(cloudinaryPhotoStorage).upload(any(UUID.class), any(byte[].class), anyString(), anyString());

        mockMvc.perform(multipart("/api/v1/rooms/{roomId}/photos", roomId)
            .file(new MockMultipartFile("file", "room.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}))
            .header("X-Correlation-ID", "room-photo-provider-outage"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("CLOUDINARY_UNAVAILABLE"))
            .andExpect(jsonPath("$.correlationId").value("room-photo-provider-outage"))
            .andExpect(header().string("X-Correlation-ID", "room-photo-provider-outage"));
        }

        @Test
        void invalidProviderMetadataReturnsBadGatewayAndCleansUpUploadedAsset() throws Exception {
        UUID roomId = createRoom(UUID.randomUUID(), createRoomType(), "Invalid photo metadata room", "PHOTO-METADATA");
        String publicId = "roomiq/rooms/invalid-metadata";
        when(cloudinaryPhotoStorage.upload(any(UUID.class), any(byte[].class), anyString(), anyString()))
            .thenReturn(new CloudinaryPhotoStorage.StoredPhoto(publicId, "http://example.test/photo.png"));

        mockMvc.perform(multipart("/api/v1/rooms/{roomId}/photos", roomId)
            .file(new MockMultipartFile("file", "room.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}))
            .header("X-Correlation-ID", "room-photo-invalid-metadata"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("CLOUDINARY_RESPONSE_INVALID"))
            .andExpect(jsonPath("$.correlationId").value("room-photo-invalid-metadata"))
            .andExpect(header().string("X-Correlation-ID", "room-photo-invalid-metadata"));

        verify(cloudinaryPhotoStorage).delete(publicId);
        assertThat(roomPhotoRepository.count()).isZero();
        }

        @Test
        void photoUploadCompensatesCloudinaryAssetWhenPersistenceFails() throws Exception {
        Room room = roomRepository.saveAndFlush(new Room(
            roomTypeRepository.findById(createRoomType()).orElseThrow(), UUID.randomUUID(), UUID.randomUUID(),
            "Photo persistence failure room", "PHOTO-PERSISTENCE", null, 8, RoomClass.NORMAL, RoomStatus.AVAILABLE));
        String publicId = "roomiq/rooms/duplicate-asset-id";
        roomPhotoRepository.saveAndFlush(new RoomPhoto(room, publicId,
            "https://res.cloudinary.com/test/existing.png", (short) 0, false, false));
        when(cloudinaryPhotoStorage.upload(any(UUID.class), any(byte[].class), anyString(), anyString()))
            .thenReturn(new CloudinaryPhotoStorage.StoredPhoto(publicId,
                "https://res.cloudinary.com/test/uploaded.png"));

        mockMvc.perform(multipart("/api/v1/rooms/{roomId}/photos", room.getId())
            .file(new MockMultipartFile("file", "room.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}))
            .header("X-Correlation-ID", "room-photo-persistence-failure"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.correlationId").value("room-photo-persistence-failure"))
            .andExpect(header().string("X-Correlation-ID", "room-photo-persistence-failure"));

        verify(cloudinaryPhotoStorage).delete(publicId);
        assertThat(roomPhotoRepository.countByRoom_Id(room.getId())).isEqualTo(1);
        }

        @Test
        void deniedRoomChildAndLifecycleMutationsDoNotPersist() throws Exception {
        UUID buildingId = UUID.randomUUID();
        UUID roomTypeId = createRoomType();
        UUID facilityTypeId = facilityTypeRepository.saveAndFlush(new FacilityType(
            "AUTHZ-FAC-" + UUID.randomUUID(), "Authorization test facility", "TEST", true)).getId();
        Room room = roomRepository.saveAndFlush(new Room(roomTypeRepository.findById(roomTypeId).orElseThrow(),
            UUID.randomUUID(), buildingId, "Denied mutations", "AUTHZ-DENIED", null, 8,
            RoomClass.NORMAL, RoomStatus.AVAILABLE));
        UUID maintenanceId = UUID.randomUUID();
        insertMaintenancePeriod(room.getId(), UUID.randomUUID(), "2026-06-01T09:00:00Z", "2026-06-01T10:00:00Z");
        doThrow(new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Forbidden"))
            .when(roomPhotoAuthorizationClient).authorizeRoomManagement(buildingId);

        mockMvc.perform(post("/api/v1/rooms/{roomId}/facilities", room.getId())
            .header("Authorization", "Bearer caller-token")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "facilityTypeId", facilityTypeId,
                "quantity", 1,
                "state", "WORKING"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/rooms/{roomId}/rules", room.getId())
            .header("Authorization", "Bearer caller-token")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "minDurationMinutes", 30,
                "recurringAllowed", false,
                "externalGuestsAllowed", false,
                "approvalRequired", true,
                "outsideHoursAllowed", false,
                "active", true,
                "allowedDepartmentIds", List.of()))))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/rooms/{roomId}/status", room.getId())
            .header("Authorization", "Bearer caller-token")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("status", "MAINTENANCE", "reason", "Denied"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/rooms/{id}", room.getId())
            .header("Authorization", "Bearer caller-token"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/rooms/{roomId}/maintenance-periods", room.getId())
            .header("Authorization", "Bearer caller-token")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "startsAt", "2026-06-01T11:00:00Z",
                "endsAt", "2026-06-01T12:00:00Z"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/rooms/{roomId}/maintenance-periods/{maintenancePeriodId}",
                room.getId(), maintenanceId)
            .header("Authorization", "Bearer caller-token"))
            .andExpect(status().isForbidden());

        assertThat(roomFacilityRepository.findAllByRoom_IdOrderByFacilityType_NameAsc(room.getId())).isEmpty();
        assertThat(roomRuleRepository.findAllByRoom_IdOrderByEffectiveFromDesc(room.getId())).isEmpty();
        assertThat(roomStatusHistoryRepository.findAllByRoom_IdOrderByChangedAtAsc(room.getId())).isEmpty();
        assertThat(maintenancePeriodRepository.findAllByRoomIdOrderByStart(room.getId())).hasSize(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.AVAILABLE);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getDeletedAt()).isNull();
        }

        @Test
        void maintenancePeriodsMayBeAdjacentButCannotOverlapForTheSameRoom() {
        UUID roomId = createRoom(UUID.randomUUID(), createRoomType(), "Maintenance Room", "M-01");
        UUID actorId = UUID.randomUUID();
        insertMaintenancePeriod(roomId, actorId, "2026-01-01T09:00:00Z", "2026-01-01T10:00:00Z");
        insertMaintenancePeriod(roomId, actorId, "2026-01-01T10:00:00Z", "2026-01-01T11:00:00Z");

        assertThatThrownBy(() -> insertMaintenancePeriod(roomId, actorId,
                "2026-01-01T09:30:00Z", "2026-01-01T10:30:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID otherRoomId = createRoom(UUID.randomUUID(), createRoomType(), "Other Maintenance Room", "M-02");
        insertMaintenancePeriod(otherRoomId, actorId,
            "2026-01-01T09:30:00Z", "2026-01-01T10:30:00Z");
    }

        @Test
        void roomStatusChangesRecordHistoryAndEnforceTerminalDecommissioning() throws Exception {
        UUID roomId = createRoom(UUID.randomUUID(), createRoomType(), "Lifecycle Room", "L-01");
        UUID actorId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/rooms/{roomId}/status", roomId)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "status", "MAINTENANCE",
                "changedByUserId", actorId,
                "reason", "Annual service"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.oldStatus").value("AVAILABLE"))
            .andExpect(jsonPath("$.data.newStatus").value("MAINTENANCE"))
            .andExpect(jsonPath("$.data.changedByUserId").value(AUTHORIZED_ACTOR_ID.toString()))
            .andExpect(jsonPath("$.data.reason").value("Annual service"))
            .andExpect(jsonPath("$.data.changedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/rooms/{roomId}/status-history", roomId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(patch("/api/v1/rooms/{roomId}/status", roomId)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "status", "DECOMMISSIONED",
                "changedByUserId", actorId,
                "reason", "Room retired"))))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/rooms/{roomId}/status", roomId)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "status", "AVAILABLE",
                "changedByUserId", actorId,
                "reason", "Invalid reactivation"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROOM_STATUS_TRANSITION_INVALID"));
        }

        @Test
        void roomPutCannotBypassStatusHistoryAndSoftDeletePreservesRoomHistory() throws Exception {
        UUID roomTypeId = createRoomType();
        UUID roomId = createRoom(UUID.randomUUID(), roomTypeId, "Soft Delete Room", "SD-01");
        UUID actorId = UUID.randomUUID();
        roomStatusHistoryRepository.saveAndFlush(new RoomStatusHistory(
            roomRepository.findById(roomId).orElseThrow(), null, RoomStatus.AVAILABLE, actorId, "Created"));

        mockMvc.perform(put("/api/v1/rooms/{id}", roomId)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "floorId", UUID.randomUUID(),
                "officeBuildingId", UUID.randomUUID(),
                "roomTypeId", roomTypeId,
                "name", "Changed status by put",
                "code", "SD-02",
                "capacity", 8,
                "roomClass", "NORMAL",
                "status", "MAINTENANCE"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROOM_STATUS_CHANGE_REQUIRED"));

        mockMvc.perform(delete("/api/v1/rooms/{id}", roomId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        assertThat(roomRepository.findById(roomId).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM room_status_history WHERE room_id = ?", Integer.class, roomId)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/rooms/{id}", roomId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/rooms/{id}/status-history", roomId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/rooms")
            .param("search", "Soft Delete Room"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0));
        }

        @Test
        void maintenancePeriodApiScopesRecordsAndMapsPostgresOverlapConflicts() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID roomId = createRoom(buildingId, createRoomType(), "Maintained Room", "MP-01");
        String firstPeriod = objectMapper.writeValueAsString(Map.of(
            "startsAt", "2026-05-01T09:00:00Z",
            "endsAt", "2026-05-01T10:00:00Z",
            "reason", "HVAC service",
            "createdByUserId", actorId));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/maintenance-periods", roomId)
            .contentType(APPLICATION_JSON).content(firstPeriod))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.roomId").value(roomId.toString()))
            .andExpect(jsonPath("$.data.createdByUserId").value(AUTHORIZED_ACTOR_ID.toString()));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/maintenance-periods", roomId)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "startsAt", "2026-05-01T09:30:00Z",
                "endsAt", "2026-05-01T10:30:00Z",
                "createdByUserId", actorId))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROOM_MAINTENANCE_PERIOD_OVERLAP"));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/maintenance-periods", roomId)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "startsAt", "2026-05-01T10:00:00Z",
                "endsAt", "2026-05-01T11:00:00Z",
                "createdByUserId", actorId))))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/rooms/{roomId}/maintenance-periods", roomId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/maintenance-periods", roomId)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "startsAt", "2026-05-01T12:00:00Z",
                "endsAt", "2026-05-01T11:00:00Z",
                "createdByUserId", actorId))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ROOM_MAINTENANCE_PERIOD_INVALID"));

        UUID otherRoomId = createRoom(buildingId, createRoomType(), "Separate Maintained Room", "MP-02");
        mockMvc.perform(post("/api/v1/rooms/{roomId}/maintenance-periods", otherRoomId)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "startsAt", "2026-05-01T09:30:00Z",
                "endsAt", "2026-05-01T10:30:00Z",
                "createdByUserId", actorId))))
            .andExpect(status().isCreated());
        }

        @Test
        @Transactional
        void jpaModelRepositoriesAndDtosMatchTheMigratedRoomSchema() throws Exception {
        assertThat(entityManagerFactory.getMetamodel().getEntities())
            .extracting(entityType -> entityType.getJavaType().getSimpleName())
            .containsExactlyInAnyOrder("RoomType", "FacilityType", "Room", "RoomFacility", "RoomPhoto",
                "RoomRule", "RoomRuleAllowedDepartment", "RoomStatusHistory", "MaintenancePeriod");

        UUID buildingId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RoomType roomType = roomTypeRepository.saveAndFlush(new RoomType("TYPE-" + UUID.randomUUID(), "Boardroom", true));
        FacilityType facilityType = facilityTypeRepository.saveAndFlush(
            new FacilityType("FAC-" + UUID.randomUUID(), "Projector", "AV", true));
        Room room = roomRepository.saveAndFlush(new Room(roomType, floorId, buildingId, "Boardroom", "BR-01",
            "Main meeting room", 12, RoomClass.NORMAL, RoomStatus.AVAILABLE));
        RoomFacility facility = roomFacilityRepository.saveAndFlush(new RoomFacility(room, facilityType,
            (short) 1, FacilityState.WORKING, null));
        RoomPhoto photo = roomPhotoRepository.saveAndFlush(new RoomPhoto(room, "roomiq/" + UUID.randomUUID(),
            "https://images.example.test/room.jpg", (short) 0, true, true));
        RoomRule rule = roomRuleRepository.saveAndFlush(new RoomRule(room, null, 30, 240, 60, 30, 15,
            true, false, true, false, 5, true, null));
        RoomRuleAllowedDepartment allowedDepartment = roomRuleAllowedDepartmentRepository.saveAndFlush(
            new RoomRuleAllowedDepartment(rule, departmentId));
        RoomStatusHistory history = roomStatusHistoryRepository.saveAndFlush(
            new RoomStatusHistory(room, null, RoomStatus.AVAILABLE, actorId, "Created"));
        PGobject range = new PGobject();
        range.setType("tstzrange");
        range.setValue("[2026-01-01 09:00:00+00,2026-01-01 10:00:00+00)");
        MaintenancePeriod maintenancePeriod = maintenancePeriodRepository.saveAndFlush(
            new MaintenancePeriod(room, range, "Scheduled service", actorId));

        entityManager.clear();

        Room reloadedRoom = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(reloadedRoom.getFloorId()).isEqualTo(floorId);
        assertThat(reloadedRoom.getOfficeBuildingId()).isEqualTo(buildingId);
        assertThat(roomFacilityRepository.findById(facility.getId()).orElseThrow().getFacilityType().getId())
            .isEqualTo(facilityType.getId());
        assertThat(roomPhotoRepository.findById(photo.getId()).orElseThrow().getCloudinaryPublicId())
            .isEqualTo(photo.getCloudinaryPublicId());
        assertThat(roomRuleAllowedDepartmentRepository.findById(allowedDepartment.getId()).orElseThrow()
            .getDepartmentId()).isEqualTo(departmentId);
        assertThat(roomStatusHistoryRepository.findById(history.getId()).orElseThrow().getChangedByUserId())
            .isEqualTo(actorId);
        assertThat(maintenancePeriodRepository.findById(maintenancePeriod.getId()).orElseThrow()
            .getPeriod().getValue()).contains("2026-01-01").contains(",");

        assertThat(roomMapper.toDto(roomType).name()).isEqualTo("Boardroom");
        FacilityTypeDto facilityTypeDto = roomMapper.toDto(facilityTypeRepository.findById(facilityType.getId())
            .orElseThrow());
        RoomDto roomDto = roomMapper.toDto(reloadedRoom);
        assertThat(roomMapper.toDto(roomFacilityRepository.findById(facility.getId()).orElseThrow()).state())
            .isEqualTo(FacilityState.WORKING);
        assertThat(roomMapper.toDto(roomPhotoRepository.findById(photo.getId()).orElseThrow()).approvedForPublic())
            .isTrue();
        assertThat(roomMapper.toDto(roomRuleRepository.findById(rule.getId()).orElseThrow()).maxDurationMinutes())
            .isEqualTo(240);
        assertThat(roomMapper.toDto(roomRuleAllowedDepartmentRepository.findById(allowedDepartment.getId())
            .orElseThrow()).departmentId()).isEqualTo(departmentId);
        assertThat(roomMapper.toDto(roomStatusHistoryRepository.findById(history.getId()).orElseThrow()).newStatus())
            .isEqualTo(RoomStatus.AVAILABLE);
        MaintenancePeriodDto maintenancePeriodDto = roomMapper.toDto(
            maintenancePeriodRepository.findById(maintenancePeriod.getId()).orElseThrow());
        assertThat(roomDto.roomTypeId()).isEqualTo(roomType.getId());
        assertThat(facilityTypeDto.category()).isEqualTo("AV");
        assertThat(maintenancePeriodDto.roomId()).isEqualTo(room.getId());
        }

    private Set<String> tableNames() {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                (resultSet, rowNumber) -> resultSet.getString(1)));
    }

    private Set<String> importedForeignKeyColumns(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet keys = connection.getMetaData().getImportedKeys(connection.getCatalog(), "public", tableName)) {
            while (keys.next()) {
                columns.add(keys.getString("FKCOLUMN_NAME"));
            }
        }
        return columns;
    }

    private UUID createRoomType() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO room_type (id, code, name) VALUES (?, ?, ?)", id,
                "TYPE-" + id, "Meeting room");
        return id;
    }

    private UUID createRoom(UUID buildingId, UUID typeId, String name, String code) {
        UUID id = UUID.randomUUID();
        insertRoom(id, buildingId, typeId, name, code, 4);
        return id;
    }

    private void insertRoom(UUID id, UUID buildingId, UUID typeId, String name, String code, int capacity) {
        jdbcTemplate.update("""
                INSERT INTO room (id, floor_id, office_building_id, room_type_id, name, code, capacity, class)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'NORMAL')
                """, id, UUID.randomUUID(), buildingId, typeId, name, code, capacity);
    }

    private void insertMaintenancePeriod(UUID roomId, UUID actorId, String start, String end) {
        jdbcTemplate.update("""
                INSERT INTO maintenance_period (id, room_id, period, created_by_user_id)
                VALUES (?, ?, tstzrange(?::timestamptz, ?::timestamptz, '[)'), ?)
                """, UUID.randomUUID(), roomId, start, end, actorId);
    }
}
