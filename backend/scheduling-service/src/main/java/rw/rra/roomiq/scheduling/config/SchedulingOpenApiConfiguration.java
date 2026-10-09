package rw.rra.roomiq.scheduling.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rw.rra.roomiq.common.web.ApiError;

import java.util.Map;

@Configuration
public class SchedulingOpenApiConfiguration {
    private static final String BEARER_AUTH = "bearerAuth";
    private static final String CORRELATION_ID = "X-Correlation-ID";
    private static final String API_ERROR_SCHEMA = "#/components/schemas/ApiError";
    private static final Map<String, String> ERROR_RESPONSES = Map.of(
            "400", "Malformed request or unsupported search bounds, including INVALID_TIMEZONE, INVALID_RRULE, AVAILABILITY_RANGE_TOO_LARGE, AVAILABILITY_RESULT_LIMIT_EXCEEDED, and recurrence-bound errors.",
            "401", "Authentication is required or invalid: AUTHENTICATION_REQUIRED.",
            "403", "The authenticated user is not authorized: ACCESS_DENIED.",
            "404", "A working calendar, window, or recurrence rule was not found.",
            "409", "A calendar or recurrence-rule integrity conflict occurred.",
            "500", "An unexpected error occurred: INTERNAL_ERROR.",
            "503", "Authoritative inputs are unavailable or stale: IDENTITY_AUTHORIZATION_UNAVAILABLE, "
                    + "ROOM_AVAILABILITY_UNAVAILABLE, BOOKING_OCCUPANCY_UNAVAILABLE, BOOKING_OCCUPANCY_STALE, "
                    + "and SCHEDULING_DATA_UNAVAILABLE.");

    @Bean
    OpenApiCustomizer schedulingApiContractCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                    .description("Identity Service bearer access token."));
            ModelConverters.getInstance().read(ApiError.class).forEach(openApi.getComponents()::addSchemas);
            openApi.getComponents().addSchemas("ApiResponse", apiResponseSchema());
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (path.startsWith("/api/v1/working-calendars")
                    || path.startsWith("/api/v1/holidays")
                    || path.startsWith("/api/v1/closure-periods")
                    || path.startsWith("/api/v1/recurrence-rules")
                    || path.startsWith("/api/v1/scheduling-constraints")
                    || path.startsWith("/api/v1/availability")) {
                    pathItem.readOperations().forEach(this::documentOperationContract);
                }
            });
        };
    }

    private void documentOperationContract(Operation operation) {
        operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
        boolean hasCorrelationId = operation.getParameters() != null && operation.getParameters().stream()
                .anyMatch(parameter -> CORRELATION_ID.equalsIgnoreCase(parameter.getName()));
        if (!hasCorrelationId) {
            operation.addParametersItem(new Parameter().in("header").name(CORRELATION_ID)
                    .description("Optional caller correlation ID (1-128 ASCII letters, digits, '.', '_' or '-').")
                    .required(false).schema(new Schema<String>().type("string").pattern("[A-Za-z0-9._-]{1,128}")));
        }
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        String successStatus = operation.getResponses().containsKey("201") ? "201" : "200";
        ApiResponse success = operation.getResponses().computeIfAbsent(successStatus,
                ignored -> new ApiResponse().description("Successful operation"));
        success.setContent(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiResponse"))));
        ERROR_RESPONSES.forEach((status, description) -> operation.getResponses().putIfAbsent(status,
                new ApiResponse().description(description).content(new Content().addMediaType("application/json",
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
        schema.addProperty("metadata", new Schema<Object>().type("object").additionalProperties(new Schema<>()));
        schema.addProperty("timestamp", new Schema<String>().type("string").format("date-time"));
        return schema;
    }
}