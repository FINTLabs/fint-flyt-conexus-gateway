package no.novari.flyt.conexus.gateway.metadata

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
@RequestMapping("$EXTERNAL_API/conexus/metadata")
class ConexusMetadataController(
    private val handler: ConexusMetadataHandler?,
) {
    @Operation(
        summary = "Registrer en dokumenttype som integrasjon i Flyt",
        description = """Alle dokumenttyper bruker samme Flyt-datasett fra Conexus-utkastet.

| Kategori | Type | Feltnøkler |
| --- | --- | --- |
| | STRING | dokumentId, dokumenttype, elevFnr, elevNavn, elevId, skoleOrgnr, skoleNavn, sakNokkel, forslagSakstittel, forslagOffentligSakstittel, forslagJournalposttittel, forslagOffentligJournalposttittel |
| Hoveddokument | STRING | hovedDokumentTittel, hovedDokumentDato, hovedDokumentFilnavn, hovedDokumentMediatype |
| Hoveddokument | FILE | hovedDokumentFil |""",
        requestBody =
            OpenApiRequestBody(
                required = true,
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = ConexusMetadata::class),
                        examples = [
                            ExampleObject(
                                name = "Kartlegging",
                                value = """{
                                    "dokumenttype": "kartlegging",
                                    "integrationDisplayName": "Arkivering av kartlegging"
                                }""",
                            ),
                        ],
                    ),
                ],
            ),
        responses = [
            ApiResponse(responseCode = "202", description = "Metadataregistreringen er akseptert for behandling."),
            ApiResponse(responseCode = "400", description = "Ugyldig JSON eller feltverdi."),
            ApiResponse(responseCode = "401", description = "Mangler gyldig tilgangstoken."),
            ApiResponse(responseCode = "403", description = "Kildeapplikasjonen har ikke tilgang."),
            ApiResponse(responseCode = "503", description = "Metadatabehandling er utilgjengelig."),
        ],
    )
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun postMetadata(
        @Valid @RequestBody metadata: ConexusMetadata,
        @Parameter(hidden = true) authentication: Authentication,
    ): ResponseEntity<Void> =
        handler?.process(authentication, metadata)
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Metadatabehandling er utilgjengelig")
}

fun interface ConexusMetadataHandler {
    fun process(
        authentication: Authentication,
        metadata: ConexusMetadata,
    ): ResponseEntity<Void>
}
