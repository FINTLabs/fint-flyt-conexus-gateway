package no.novari.flyt.conexus.gateway.metadata

import no.novari.flyt.gateway.metadata.IntegrationMetadataMapper
import no.novari.flyt.gateway.metadata.IntegrationMetadataValidator
import no.novari.flyt.gateway.metadata.model.InstanceMetadataCategory
import no.novari.flyt.gateway.metadata.model.InstanceMetadataContent
import no.novari.flyt.gateway.metadata.model.InstanceValueMetadata
import no.novari.flyt.gateway.metadata.model.IntegrationMetadata
import org.springframework.stereotype.Service

private const val FILE_KEY = "hovedDokumentFil"
private val MAIN_DOCUMENT_KEYS =
    setOf("hovedDokumentTittel", "hovedDokumentDato", "hovedDokumentFilnavn", "hovedDokumentMediatype", FILE_KEY)
private val REQUIRED_KEYS =
    setOf("dokumentId", "dokumenttype", "elevFnr", "elevNavn", "elevId", "skoleOrgnr", "skoleNavn", "sakNokkel") +
        MAIN_DOCUMENT_KEYS
private val OPTIONAL_KEYS =
    setOf(
        "forslagSakstittel",
        "forslagOffentligSakstittel",
        "forslagJournalposttittel",
        "forslagOffentligJournalposttittel",
    )
private val SUPPORTED_KEYS = REQUIRED_KEYS + OPTIONAL_KEYS

@Service
class ConexusMetadataMapper : IntegrationMetadataMapper<ConexusMetadata> {
    override fun toIntegrationMetadata(
        sourceApplicationId: Long,
        incomingMetadata: ConexusMetadata,
    ): IntegrationMetadata {
        val metadata = requireNotNull(incomingMetadata.metadata)
        val (mainDocument, other) =
            requireNotNull(incomingMetadata.elements)
                .map { toValueMetadata(requireNotNull(it)) }
                .partition { it.key in MAIN_DOCUMENT_KEYS }

        return IntegrationMetadata(
            sourceApplicationId = sourceApplicationId,
            sourceApplicationIntegrationId = requireNotNull(metadata.dokumenttype),
            sourceApplicationIntegrationUri = null,
            integrationDisplayName = requireNotNull(metadata.integrationDisplayName),
            version = requireNotNull(metadata.version),
            instanceMetadata =
                InstanceMetadataContent(
                    instanceValueMetadata = other,
                    categories =
                        listOf(
                            InstanceMetadataCategory(
                                displayName = "Hoveddokument",
                                content = InstanceMetadataContent(instanceValueMetadata = mainDocument),
                            ),
                        ),
                ),
        )
    }

    private fun toValueMetadata(element: ConexusMetadataElement): InstanceValueMetadata =
        InstanceValueMetadata(
            key = requireNotNull(element.id),
            displayName = requireNotNull(element.displayName),
            type = if (element.type == "File") InstanceValueMetadata.Type.FILE else InstanceValueMetadata.Type.STRING,
        )
}

@Service
class ConexusMetadataValidator : IntegrationMetadataValidator<ConexusMetadata> {
    override fun validate(incomingMetadata: ConexusMetadata): List<String>? {
        val seen = mutableSetOf<String>()
        val errors =
            buildList {
                incomingMetadata.elements.orEmpty().forEachIndexed { index, element ->
                    val id = element?.id
                    if (id == null || id !in SUPPORTED_KEYS) {
                        add("elements[$index].id må være en avtalt Flyt-nøkkel")
                    } else {
                        if (!seen.add(id)) add("elements[$index].id dupliserer $id")
                        if (element.required != (id in REQUIRED_KEYS)) {
                            add("elements[$index].required samsvarer ikke med instanskontrakten for $id")
                        }
                        val expectedType = if (id == FILE_KEY) "File" else "String"
                        if (element.type != expectedType) add("elements[$index].type må være $expectedType for $id")
                    }
                }
                val missing = REQUIRED_KEYS - seen
                if (missing.isNotEmpty()) add("elements mangler påkrevde felter: ${missing.joinToString()}")
            }
        return errors.takeIf { it.isNotEmpty() }
    }
}
