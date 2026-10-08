package rw.rra.roomiq.common.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
public class RoomIqWebConfiguration {
    @Bean
    public OpenAPI roomIqOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("RRA RoomIQ API")
                .description("RRA RoomIQ service API")
                .version("v1"));
    }
}
