package no.novari.flyt.conexus.gateway.metadata

import jakarta.validation.constraints.NotBlank
import no.novari.flyt.conexus.gateway.JsonText

data class ConexusMetadata(
    @field:JsonText
    @field:NotBlank(message = "dokumenttype er påkrevd og må ha en verdi")
    val dokumenttype: String? = null,
    @field:JsonText
    @field:NotBlank(message = "integrationDisplayName er påkrevd og må ha en verdi")
    val integrationDisplayName: String? = null,
)
