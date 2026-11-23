package no.novari.flyt.conexus.gateway

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.context.annotation.Configuration

internal const val BEARER_AUTH = "bearerAuth"

@OpenAPIDefinition(info = Info(title = "FINT Flyt Conexus Gateway", version = "0.2"))
@SecurityScheme(name = BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer")
@Configuration
class OpenApiConfig
