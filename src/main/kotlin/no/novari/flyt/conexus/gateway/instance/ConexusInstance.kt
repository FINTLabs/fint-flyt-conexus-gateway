package no.novari.flyt.conexus.gateway.instance

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import no.novari.flyt.conexus.gateway.JsonText
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Base64

data class ConexusInstance(
    @field:JsonText
    @field:NotNull(message = "dokumentId er påkrevd")
    @field:Pattern(
        regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        message = "dokumentId må være en GUID",
    )
    @field:Schema(format = "uuid")
    val dokumentId: String? = null,
    @field:JsonText
    @field:NotBlank(message = "dokumenttype er påkrevd og må ha en verdi")
    val dokumenttype: String? = null,
    @field:Valid
    @field:NotNull(message = "elev er påkrevd")
    val elev: ConexusElev? = null,
    @field:Valid
    @field:NotNull(message = "skole er påkrevd")
    val skole: ConexusSkole? = null,
    @field:Valid
    @field:NotNull(message = "sak er påkrevd")
    val sak: ConexusSak? = null,
    @field:Valid
    val journalpost: ConexusTittelforslag? = null,
    @field:Valid
    @field:NotNull(message = "hoveddokument er påkrevd")
    val hoveddokument: ConexusHoveddokument? = null,
)

data class ConexusElev(
    @field:JsonText
    @field:NotNull(message = "elev.fnr er påkrevd")
    @field:Schema(description = "Elevens identifikator slik den er registrert hos Conexus.")
    val fnr: String? = null,
    @field:JsonText
    @field:NotNull(message = "elev.navn er påkrevd")
    val navn: String? = null,
    @field:JsonText
    @field:NotNull(message = "elev.elevId er påkrevd")
    val elevId: String? = null,
)

data class ConexusSkole(
    @field:JsonText
    @field:NotNull(message = "skole.orgnr er påkrevd")
    @field:Pattern(regexp = "[0-9]{9}", message = "skole.orgnr må bestå av ni sifre")
    val orgnr: String? = null,
    @field:JsonText
    @field:NotNull(message = "skole.navn er påkrevd")
    val navn: String? = null,
)

data class ConexusSak(
    @field:JsonText
    @field:NotNull(message = "sak.nokkel er påkrevd")
    val nokkel: String? = null,
    @field:JsonText
    val forslagTittel: String? = null,
    @field:JsonText
    val forslagOffentligTittel: String? = null,
)

data class ConexusTittelforslag(
    @field:JsonText
    val forslagTittel: String? = null,
    @field:JsonText
    val forslagOffentligTittel: String? = null,
)

data class ConexusHoveddokument(
    @field:JsonText
    @field:NotNull(message = "hoveddokument.tittel er påkrevd")
    val tittel: String? = null,
    @field:JsonText
    @field:NotNull(message = "hoveddokument.dato er påkrevd")
    @field:Schema(format = "date")
    val dato: String? = null,
    @field:JsonText
    @field:NotNull(message = "hoveddokument.filnavn er påkrevd")
    val filnavn: String? = null,
    @field:JsonText
    @field:NotNull(message = "hoveddokument.mediatype er påkrevd")
    @field:Pattern(regexp = "application/pdf", message = "hoveddokument.mediatype må være application/pdf")
    val mediatype: String? = null,
    @field:JsonText
    @field:NotNull(message = "hoveddokument.filBase64 er påkrevd")
    @field:Schema(format = "byte", description = "Hoveddokumentet som base64-kodet PDF/A.")
    val filBase64: String? = null,
) {
    @JsonIgnore
    @AssertTrue(message = "hoveddokument.dato må være en gyldig ISO 8601-dato")
    fun isDatoGyldig(): Boolean {
        val verdi = dato ?: return true
        return try {
            LocalDate.parse(verdi)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }

    @JsonIgnore
    @AssertTrue(message = "hoveddokument.filBase64 må inneholde gyldig base64")
    fun isFilBase64Gyldig(): Boolean {
        val verdi = filBase64 ?: return true
        return try {
            Base64.getDecoder().decode(verdi).isNotEmpty()
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
