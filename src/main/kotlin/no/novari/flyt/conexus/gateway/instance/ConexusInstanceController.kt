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
import no.novari.flyt.gateway.instance.InstanceProcessor
import no.novari.flyt.webresourceserver.UrlPaths.EXTERNAL_API
import no.novari.flyt.webresourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
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
@RequestMapping("$EXTERNAL_API/conexus/instances")
class ConexusInstanceController(
    private val instanceProcessor: InstanceProcessor<ValidatedConexusInstance>,
    private val metadataValidator: ConexusInstanceMetadataValidator,
    private val sourceApplicationAuthorizationService: SourceApplicationAuthorizationService,
) {
    @Operation(
        summary = "Send inn et dokument til arkivering",
        description = "Bruk felt-ID-er fra siste metadataversjon. Hoveddokumentet bruker datasettets File-felt.",
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
            ApiResponse(responseCode = "409", description = "metadataVersion samsvarer ikke med siste versjon."),
            ApiResponse(
                responseCode = "422",
                description = "Ugyldige felt, manglende metadata eller inaktiv integrasjon.",
            ),
            ApiResponse(responseCode = "500", description = "Filopplasting eller videresending feilet."),
            ApiResponse(responseCode = "502", description = "Oppslag av siste metadataversjon feilet."),
        ],
    )
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun postInstance(
        @Valid @RequestBody instance: ConexusInstance,
        @Parameter(hidden = true) authentication: Authentication,
    ): ResponseEntity<Void> {
        val sourceApplicationId = sourceApplicationAuthorizationService.getSourceApplicationId(authentication)
        val validatedInstance = metadataValidator.validate(sourceApplicationId, instance)
        return instanceProcessor.processInstance(sourceApplicationId, validatedInstance)
    }

    private companion object {
        private const val INSTANCE_EXAMPLE = """
            {
              "dokumentId": "5a0d2892-9a48-4bef-ae6a-ef8f90bfbe42",
              "dokumenttype": "kartlegging",
              "metadataVersion": 1,
              "felter": {
                "elevFnr": "test-identifikator",
                "elevNavn": "Test Elev",
                "elevId": "134",
                "skoleOrgnr": "901234567",
                "skoleNavn": "Testskolen",
                "sakNokkel": "assessment-901234567-134"
              },
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
