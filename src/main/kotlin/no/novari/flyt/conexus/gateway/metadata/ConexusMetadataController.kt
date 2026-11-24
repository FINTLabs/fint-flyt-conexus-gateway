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
import no.novari.flyt.gateway.metadata.IntegrationMetadataProcessor
import no.novari.flyt.webresourceserver.UrlPaths.EXTERNAL_API
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

@SecurityRequirement(name = BEARER_AUTH)
@RestController
@RequestMapping("$EXTERNAL_API/conexus/metadata")
class ConexusMetadataController(
    private val integrationMetadataProcessor: IntegrationMetadataProcessor,
    private val mapper: ConexusMetadataMapper,
    private val validator: ConexusMetadataValidator,
) {
    @Operation(
        summary = "Registrer en dokumenttype som integrasjon i Flyt",
        description =
            "Bruk Flyt-nøklene fra Conexus-utkastet. Øk metadata.version når feltdefinisjonene endres. " +
                "required beskriver hvilke verdier instanskontrakten krever.",
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
                                value = METADATA_EXAMPLE,
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
            ApiResponse(responseCode = "422", description = "Feltene samsvarer ikke med instanskontrakten."),
        ],
    )
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun postMetadata(
        @Valid @RequestBody metadata: ConexusMetadata,
        @Parameter(hidden = true) authentication: Authentication,
    ): ResponseEntity<Void> =
        integrationMetadataProcessor.processIntegrationMetadata(
            authentication = authentication,
            incomingMetadata = metadata,
            integrationMetadataMapper = mapper,
            integrationMetadataValidator = validator,
        )

    private companion object {
        private const val METADATA_EXAMPLE = """
            {
              "metadata": {
                "dokumenttype": "kartlegging",
                "integrationDisplayName": "Arkivering av kartlegging",
                "version": 1
              },
              "elements": [
                {"id":"dokumentId","displayName":"Dokument-ID","type":"String","required":true},
                {"id":"dokumenttype","displayName":"Dokumenttype","type":"String","required":true},
                {"id":"elevFnr","displayName":"Elevens identifikator","type":"String","required":true},
                {"id":"elevNavn","displayName":"Elevens navn","type":"String","required":true},
                {"id":"elevId","displayName":"Elev-ID i Conexus","type":"String","required":true},
                {"id":"skoleOrgnr","displayName":"Skolens organisasjonsnummer","type":"String","required":true},
                {"id":"skoleNavn","displayName":"Skolens navn","type":"String","required":true},
                {"id":"sakNokkel","displayName":"Saksnøkkel","type":"String","required":true},
                {"id":"hovedDokumentTittel","displayName":"Dokumenttittel","type":"String","required":true},
                {"id":"hovedDokumentDato","displayName":"Dokumentdato","type":"String","required":true},
                {"id":"hovedDokumentFilnavn","displayName":"Filnavn","type":"String","required":true},
                {"id":"hovedDokumentMediatype","displayName":"Mediatype","type":"String","required":true},
                {"id":"hovedDokumentFil","displayName":"Hoveddokument","type":"File","required":true},
                {"id":"forslagSakstittel","displayName":"Forslag til sakstittel","type":"String","required":false},
                {"id":"forslagOffentligSakstittel","displayName":"Forslag til offentlig sakstittel","type":"String","required":false},
                {"id":"forslagJournalposttittel","displayName":"Forslag til journalposttittel","type":"String","required":false},
                {"id":"forslagOffentligJournalposttittel","displayName":"Forslag til offentlig journalposttittel","type":"String","required":false}
              ]
            }
        """
    }
}
