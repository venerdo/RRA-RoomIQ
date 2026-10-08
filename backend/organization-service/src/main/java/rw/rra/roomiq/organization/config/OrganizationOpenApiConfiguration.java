package rw.rra.roomiq.organization.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;
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
public class OrganizationOpenApiConfiguration {
    private static final String BEARER_AUTH = "bearerAuth";
    private static final String CORRELATION_ID = "X-Correlation-ID";
    private static final String API_ERROR_SCHEMA = "#/components/schemas/ApiError";
    private static final Map<String, String> ERROR_RESPONSES = Map.of(
            "400", "Validation or malformed-request error: VALIDATION_ERROR, CONSTRAINT_VIOLATION, INVALID_REQUEST, INVALID_SORT_FIELD, INVALID_TIMEZONE.",
            "401", "Authentication is required or invalid: AUTHENTICATION_REQUIRED.",
            "403", "The authenticated user is not authorized: ACCESS_DENIED.",
            "404", "The requested resource was not found: COUNTRY_NOT_FOUND, PROVINCE_NOT_FOUND, DISTRICT_NOT_FOUND, OFFICE_BUILDING_NOT_FOUND, FLOOR_NOT_FOUND, DEPARTMENT_NOT_FOUND, DEPARTMENT_PARENT_NOT_FOUND.",
            "409", "A business or persistence conflict occurred: ORGANIZATION_RESOURCE_CONFLICT, ORGANIZATION_PARENT_INACTIVE, ORGANIZATION_ACTIVE_CHILDREN, INVALID_DEPARTMENT_PARENT, DEPARTMENT_SCOPE_HAS_CHILDREN, INVALID_DEPARTMENT_HIERARCHY.",
            "500", "An unexpected error occurred: INTERNAL_ERROR.",
            "503", "Identity authorization is unavailable: IDENTITY_AUTHORIZATION_UNAVAILABLE.");

    @Bean
    OpenApiCustomizer organizationApiContractCustomizer() {
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