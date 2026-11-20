package no.novari.flyt.conexus.gateway.instance

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.novari.flyt.conexus.gateway.BEARER_AUTH
import no.novari.flyt.webresourceserver.UrlPaths.EXTERNAL_API
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

@SecurityRequirement(name = BEARER_AUTH)
@RestController
@RequestMapping("$EXTERNAL_API/conexus/instances")
class ConexusInstanceController(
    private val handler: ConexusInstanceHandler?,
) {
    @Operation(
        summary = "Send inn et dokument til arkivering",
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ConexusInstance::class),
                        examples = [
                            ExampleObject(
                                name = "Kartlegging",
                                description = "Syntetiske testdata med forkortet filinnhold.",
                                value = INSTANCE_EXAMPLE,
                            ),
                        ],
                    ),
                ],
            ),
        responses = [
            ApiResponse(responseCode = "202", description = "Instansen er akseptert for behandling."),
            ApiResponse(responseCode = "400", description = "Ugyldig JSON eller feltverdi."),
            ApiResponse(responseCode = "401", description = "Mangler gyldig tilgangstoken."),
            ApiResponse(responseCode = "403", description = "Kildeapplikasjonen har ikke tilgang."),
            ApiResponse(responseCode = "422", description = "Integrasjonen mangler eller er deaktivert."),
            ApiResponse(responseCode = "503", description = "Instansbehandling er utilgjengelig."),
        ],
    )
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun postInstance(
        @Valid @RequestBody instance: ConexusInstance,
        @Parameter(hidden = true) authentication: Authentication,
    ): ResponseEntity<Void> =
        handler?.process(authentication, instance)
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Instansbehandling er utilgjengelig")

    private companion object {
        private const val INSTANCE_EXAMPLE = """
            {
              "dokumentId": "5a0d2892-9a48-4bef-ae6a-ef8f90bfbe42",
              "dokumenttype": "kartlegging",
              "elev": { "fnr": "test-identifikator", "navn": "Test Elev", "elevId": "134" },
              "skole": { "orgnr": "901234567", "navn": "Testskolen" },
              "sak": { "nokkel": "assessment-901234567-134" },
              "hoveddokument": {
                "tittel": "Kartlegging",
                "dato": "2026-06-14",
                "filnavn": "kartlegging.pdf",
                "mediatype": "application/pdf",
                "filBase64": "JVBERi0="
              }
            }
        """
    }
}

fun interface ConexusInstanceHandler {
    fun process(
        authentication: Authentication,
        instance: ConexusInstance,
    ): ResponseEntity<Void>
}
