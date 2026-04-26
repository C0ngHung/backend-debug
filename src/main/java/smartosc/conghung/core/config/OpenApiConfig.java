package smartosc.conghung.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile({"dev", "test"})
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public GroupedOpenApi publicApi(@Value("${open-api.service.api-docs}") String apiDocs) {
        return GroupedOpenApi.builder()
                .group(apiDocs)
                .packagesToScan("smartosc.conghung.modules")
                .build();
    }

    @Bean
    public OpenAPI openAPI(
            @Value("${open-api.service.title}") String title,
            @Value("${open-api.service.version}") String version,
            @Value("${open-api.service.server}") String serverUrl) {

        return new OpenAPI()
                .servers(List.of(new Server().url(serverUrl)))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .security(List.of(new SecurityRequirement().addList(SECURITY_SCHEME_NAME)))
                .info(new Info()
                        .title(title)
                        .description("API documents for Backend service")
                        .version(version)
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://springdoc.org"))
                        .contact(new Contact()
                                .name("C0ngHungDev")
                                .email("C0ngHungDCH@gmail.com")));
    }
}
