package no.novari.flyt.conexus.gateway.metadata

import no.novari.flyt.gateway.metadata.IntegrationMetadataMapper
import no.novari.flyt.gateway.metadata.IntegrationMetadataValidator
import no.novari.flyt.gateway.metadata.model.InstanceMetadataCategory
import no.novari.flyt.gateway.metadata.model.InstanceMetadataContent
import no.novari.flyt.gateway.metadata.model.InstanceValueMetadata
import no.novari.flyt.gateway.metadata.model.IntegrationMetadata
import org.springframework.stereotype.Service

private val MAIN_DOCUMENT_KEYS =
    setOf(
        "hovedDokumentTittel",
        "hovedDokumentDato",
        "hovedDokumentFilnavn",
        "hovedDokumentMediatype",
        "hovedDokumentFil",
    )

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
                        if (mainDocument.isEmpty()) {
                            emptyList()
                        } else {
                            listOf(
                                InstanceMetadataCategory(
                                    displayName = "Hoveddokument",
                                    content = InstanceMetadataContent(instanceValueMetadata = mainDocument),
                                ),
                            )
                        },
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
                    if (element == null) {
                        add("elements[$index] må være en feltdefinisjon")
                        return@forEachIndexed
                    }
                    val id = element.id
                    if (id != null && !seen.add(id)) add("elements[$index].id dupliserer $id")
                    if (element.type != "String" && element.type != "File") {
                        add("elements[$index].type må være String eller File")
                    }
                }
            }
        return errors.takeIf { it.isNotEmpty() }
    }
}
