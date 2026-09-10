package com.paperflow.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(
                        List.of(
                                new Server()
                                        .url("http://localhost:9000")

                        )
                )
                .info(new Info()
                        .title("PaperFlow API")
                        .version("0.0.1")
                        .description("PaperFlow Backend API Documentation"));
    }
}