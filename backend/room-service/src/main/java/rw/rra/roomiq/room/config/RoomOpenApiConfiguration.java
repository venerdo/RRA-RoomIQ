package rw.rra.roomiq.room.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rw.rra.roomiq.common.web.ApiError;

import java.util.Map;

@Configuration
public class RoomOpenApiConfiguration {
    private static final String BEARER_AUTH = "bearerAuth";
    private static final String CORRELATION_ID = "X-Correlation-ID";
    private static final String API_ERROR_SCHEMA = "#/components/schemas/ApiError";
    private static final Map<String, String> ERROR_RESPONSES = Map.of(
            "400", "Validation or malformed-request error: VALIDATION_ERROR, CONSTRAINT_VIOLATION, INVALID_REQUEST, INVALID_SORT_FIELD, INVALID_CODE, INVALID_NAME, ROOM_CAPACITY_INVALID, ROOM_TYPE_REQUIRED, ROOM_MAINTENANCE_PERIOD_INVALID, ROOM_RULE_DURATION_BOUNDS, ROOM_RULE_ADVANCE_BOUNDS, ROOM_RULE_DUPLICATE_DEPARTMENT, ROOM_PHOTO_REQUIRED, ROOM_PHOTO_TOO_LARGE, ROOM_PHOTO_TYPE_UNSUPPORTED, ROOM_PHOTO_READ_FAILED, ROOM_PHOTO_SORT_ORDER_INVALID, ROOM_PHOTO_LIMIT_EXCEEDED, ORGANIZATION_REFERENCE_INVALID, ROOM_FLOOR_BUILDING_MISMATCH, ROOM_RULE_DEPARTMENT_SCOPE_MISMATCH.",
            "401", "Authentication is required or invalid: AUTHENTICATION_REQUIRED.",
            "403", "Identity denied the caller's current permission or building scope: ACCESS_DENIED, ORGANIZATION_ACCESS_DENIED.",
            "404", "A resource was not found: ROOM_NOT_FOUND, ROOM_TYPE_NOT_FOUND, FACILITY_TYPE_NOT_FOUND, ROOM_FACILITY_NOT_FOUND, ROOM_RULE_NOT_FOUND, ROOM_PHOTO_NOT_FOUND, ROOM_MAINTENANCE_PERIOD_NOT_FOUND.",
            "409", "A business or persistence conflict occurred: ROOM_TYPE_DUPLICATE, FACILITY_TYPE_DUPLICATE, ROOM_NAME_DUPLICATE, ROOM_CODE_DUPLICATE, ROOM_FACILITY_DUPLICATE, FACILITY_TYPE_INACTIVE, ROOM_STATUS_TRANSITION_INVALID, ROOM_STATUS_CHANGE_REQUIRED, ROOM_DECOMMISSIONED, ROOM_MAINTENANCE_PERIOD_OVERLAP.",
            "500", "An unexpected error occurred: INTERNAL_ERROR.",
            "502", "Photo storage returned invalid metadata: CLOUDINARY_RESPONSE_INVALID.",
            "503", "An external service is unavailable: IDENTITY_SERVICE_UNAVAILABLE, ORGANIZATION_SERVICE_UNAVAILABLE, CLOUDINARY_NOT_CONFIGURED, CLOUDINARY_UNAVAILABLE.");

    @Bean
    OpenApiCustomizer roomApiContractCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Identity Service bearer access token."));
            ModelConverters.getInstance().read(ApiError.class)
                    .forEach(openApi.getComponents()::addSchemas);
            openApi.getComponents().addSchemas("ApiResponse", apiResponseSchema());

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (!path.startsWith("/api/v1/")) {
                    return;
                }
                pathItem.readOperations().forEach(this::documentOperationContract);
            });
        };
    }

    private void documentOperationContract(Operation operation) {
        operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
        boolean hasCorrelationId = operation.getParameters() != null && operation.getParameters().stream()
                .anyMatch(parameter -> CORRELATION_ID.equalsIgnoreCase(parameter.getName()));
        if (!hasCorrelationId) {
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name(CORRELATION_ID)
                    .description("Optional caller correlation ID (1-128 ASCII letters, digits, '.', '_' or '-'). Invalid or missing values are replaced by the service.")
                    .required(false)
                    .schema(new Schema<String>().type("string").pattern("[A-Za-z0-9._-]{1,128}")));
        }

        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        String successStatus = operation.getResponses().containsKey("201") ? "201" : "200";
        ApiResponse successResponse = operation.getResponses().computeIfAbsent(successStatus,
                status -> new ApiResponse().description("Successful operation"));
        successResponse.setContent(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiResponse"))));
        ERROR_RESPONSES.forEach((status, description) -> operation.getResponses().putIfAbsent(status,
                new ApiResponse().description(description)
                        .content(new Content().addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref(API_ERROR_SCHEMA))))));
        operation.getResponses().values().forEach(response -> response.addHeaderObject(CORRELATION_ID,
                new Header().description("Correlation ID echoed by the service.")
                        .schema(new Schema<String>().type("string"))));
    }

    private Schema<Object> apiResponseSchema() {
        Schema<Object> schema = new Schema<>();
        schema.type("object");
        schema.addProperty("success", new Schema<Boolean>().type("boolean"));
        schema.addProperty("message", new Schema<String>().type("string"));
        schema.addProperty("data", new Schema<Object>().type("object").nullable(true));
        schema.addProperty("metadata", new Schema<Object>().type("object")
                .additionalProperties(new Schema<>()));
        schema.addProperty("timestamp", new Schema<String>().type("string").format("date-time"));
        return schema;
    }
}