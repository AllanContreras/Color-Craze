package com.Color_craze.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * Configuración de OpenAPI/Swagger para la documentación de la API de Color Craze.
 * Compatible con MongoDB como base de datos.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Color Craze API")
                        .version("1.0.0")
                        .description("REST API para gestión de usuarios y autenticación usando MongoDB."));
    }
}
