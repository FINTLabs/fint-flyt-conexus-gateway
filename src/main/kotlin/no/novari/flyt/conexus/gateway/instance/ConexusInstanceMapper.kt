package no.novari.flyt.conexus.gateway.instance

import no.novari.flyt.gateway.instance.InstanceMapper
import no.novari.flyt.gateway.instance.model.File
import no.novari.flyt.gateway.instance.model.instance.InstanceObject
import org.springframework.http.MediaType
import java.util.UUID

class ConexusInstanceMapper : InstanceMapper<ValidatedConexusInstance> {
    override fun map(
        sourceApplicationId: Long,
        incomingInstance: ValidatedConexusInstance,
        persistFile: (File) -> UUID,
    ): InstanceObject {
        val dokumentId = requireNotNull(incomingInstance.request.dokumentId)
        val hoveddokument = requireNotNull(incomingInstance.request.hoveddokument)
        val filnavn = requireNotNull(hoveddokument.filnavn)
        val mediatype = requireNotNull(hoveddokument.mediatype)
        val fileId =
            persistFile(
                File(
                    name = filnavn,
                    sourceApplicationId = sourceApplicationId,
                    sourceApplicationInstanceId = dokumentId,
                    type = MediaType.parseMediaType(mediatype),
                    encoding = "UTF-8",
                    base64Contents = requireNotNull(hoveddokument.filBase64),
                ),
            )

        return InstanceObject(
            valuePerKey = incomingInstance.values + (incomingInstance.fileKey to fileId.toString()),
        )
    }
}
