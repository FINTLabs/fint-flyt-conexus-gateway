package no.novari.flyt.conexus.gateway.metadata

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import no.novari.flyt.conexus.gateway.JsonText

data class ConexusMetadata(
    @field:Valid
    @field:NotNull(message = "metadata er påkrevd")
    val metadata: ConexusMetadataInfo? = null,
    @field:Valid
    @field:NotEmpty(message = "elements må inneholde feltdefinisjoner")
    val elements: List<ConexusMetadataElement?>? = null,
)

data class ConexusMetadataInfo(
    @field:JsonText
    @field:NotBlank(message = "metadata.dokumenttype er påkrevd og må ha en verdi")
    val dokumenttype: String? = null,
    @field:JsonText
    @field:NotBlank(message = "metadata.integrationDisplayName er påkrevd og må ha en verdi")
    val integrationDisplayName: String? = null,
    @field:JsonDeserialize(using = MetadataVersionDeserializer::class)
    @field:NotNull(message = "metadata.version er påkrevd")
    @field:Positive(message = "metadata.version må være et positivt heltall")
    val version: Long? = null,
)

data class ConexusMetadataElement(
    @field:JsonText
    @field:NotBlank(message = "Elementets id er påkrevd og må ha en verdi")
    @field:Schema(description = "Feltnøkkelen i Flyt fra Conexus-utkastet, for eksempel elevFnr.")
    val id: String? = null,
    @field:JsonText
    @field:NotBlank(message = "Elementets displayName er påkrevd og må ha en verdi")
    val displayName: String? = null,
    @field:JsonText
    @field:NotNull(message = "Elementets type er påkrevd")
    @field:Pattern(regexp = "String|File", message = "Elementets type må være String eller File")
    @field:Schema(allowableValues = ["String", "File"])
    val type: String? = null,
    @field:NotNull(message = "Elementets required er påkrevd")
    @field:Schema(description = "Om feltet er påkrevd i instanskontrakten.")
    val required: Boolean? = null,
)

internal class MetadataVersionDeserializer : StdScalarDeserializer<Long>(Long::class.javaObjectType) {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): Long =
        if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            parser.longValue
        } else {
            context.reportInputMismatch(Long::class.javaObjectType, "Expected a JSON integer")
        }
}
