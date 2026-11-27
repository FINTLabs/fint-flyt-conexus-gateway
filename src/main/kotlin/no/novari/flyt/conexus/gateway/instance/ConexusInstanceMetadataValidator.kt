package no.novari.flyt.conexus.gateway.instance

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import no.novari.flyt.gateway.metadata.model.InstanceMetadataContent
import no.novari.flyt.gateway.metadata.model.InstanceValueMetadata
import no.novari.flyt.gateway.metadata.model.InstanceValueMetadata.Type
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException

@Service
class ConexusInstanceMetadataValidator(
    @param:Qualifier("intgrConfRestClient")
    private val catalog: RestClient,
) {
    fun validate(
        sourceApplicationId: Long,
        instance: ConexusInstance,
    ): ValidatedConexusInstance {
        val dokumenttype = requireNotNull(instance.dokumenttype)
        val metadata = getLatestMetadata(sourceApplicationId, dokumenttype)
        if (instance.metadataVersion != metadata.version) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "metadataVersion må være ${metadata.version} for denne dokumenttypen",
            )
        }

        val typesByKey =
            metadata.instanceMetadata
                ?.valueFields()
                .orEmpty()
                .associate { it.key to it.type }
        val fileKey =
            typesByKey.entries
                .singleOrNull { it.value == Type.FILE }
                ?.key ?: invalidFields("Metadatasettet må ha nøyaktig ett File-felt for hoveddokumentet")
        val document = requireNotNull(instance.hoveddokument)
        val envelopeValues =
            mapOf(
                "dokumentId" to requireNotNull(instance.dokumentId),
                "dokumenttype" to dokumenttype,
                "hovedDokumentTittel" to requireNotNull(document.tittel),
                "hovedDokumentDato" to requireNotNull(document.dato),
                "hovedDokumentFilnavn" to requireNotNull(document.filnavn),
                "hovedDokumentMediatype" to requireNotNull(document.mediatype),
            )
        val values =
            buildMap {
                requireNotNull(instance.felter).forEach { (key, value) ->
                    put(key, validateValue(key, value, typesByKey[key]))
                }
                envelopeValues.forEach { (key, value) ->
                    val type = typesByKey[key] ?: return@forEach
                    if (type != Type.STRING) invalidFields("Metadatafeltet $key må ha typen String")
                    val submitted = put(key, value)
                    if (submitted != null && submitted != value) {
                        invalidFields("felter.$key må samsvare med verdien i instansen")
                    }
                }
            }

        return ValidatedConexusInstance(instance, values, fileKey)
    }

    private fun getLatestMetadata(
        sourceApplicationId: Long,
        dokumenttype: String,
    ): RegisteredMetadata =
        try {
            catalog
                .get()
                .uri(
                    "/metadata?sourceApplicationId={id}&sourceApplicationIntegrationId={dokumenttype}",
                    sourceApplicationId,
                    dokumenttype,
                ).retrieve()
                .body(RegisteredMetadata::class.java)
                ?.takeIf { it.version > 0 }
                ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ugyldig metadataversjon i svaret fra Flyt")
        } catch (_: HttpClientErrorException.NotFound) {
            invalidFields("Det finnes ingen registrerte metadata for dokumenttypen")
        } catch (_: RestClientException) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kunne ikke hente siste metadataversjon")
        }

    private fun InstanceMetadataContent.valueFields(): List<InstanceValueMetadata> =
        instanceValueMetadata + categories.flatMap { it.content.valueFields() }

    private fun validateValue(
        key: String,
        value: JsonNode?,
        type: Type?,
    ): String =
        when (type) {
            Type.STRING -> {
                if (value?.isTextual != true) invalidFields("felter.$key må være en tekstverdi")
                value.textValue()
            }

            Type.BOOLEAN -> {
                if (value?.isBoolean != true) invalidFields("felter.$key må være true eller false")
                value.asText()
            }

            Type.FILE -> invalidFields("felter.$key må sendes i hoveddokument")
            null -> invalidFields("felter.$key finnes ikke i siste metadataversjon")
        }

    private fun invalidFields(message: String): Nothing =
        throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class RegisteredMetadata(
        val version: Long,
        val instanceMetadata: InstanceMetadataContent? = null,
    )
}

data class ValidatedConexusInstance(
    val request: ConexusInstance,
    val values: Map<String, String>,
    val fileKey: String,
)
