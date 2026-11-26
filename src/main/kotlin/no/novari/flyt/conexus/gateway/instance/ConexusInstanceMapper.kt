package no.novari.flyt.conexus.gateway.instance

import no.novari.flyt.gateway.instance.InstanceMapper
import no.novari.flyt.gateway.instance.model.File
import no.novari.flyt.gateway.instance.model.instance.InstanceObject
import org.springframework.http.MediaType
import java.util.UUID

class ConexusInstanceMapper : InstanceMapper<ConexusInstance> {
    override fun map(
        sourceApplicationId: Long,
        incomingInstance: ConexusInstance,
        persistFile: (File) -> UUID,
    ): InstanceObject {
        val dokumentId = requireNotNull(incomingInstance.dokumentId)
        val elev = requireNotNull(incomingInstance.elev)
        val skole = requireNotNull(incomingInstance.skole)
        val sak = requireNotNull(incomingInstance.sak)
        val journalpost = incomingInstance.journalpost
        val hoveddokument = requireNotNull(incomingInstance.hoveddokument)
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
            valuePerKey =
                buildMap {
                    put("dokumentId", dokumentId)
                    put("dokumenttype", requireNotNull(incomingInstance.dokumenttype))
                    put("elevFnr", requireNotNull(elev.fnr))
                    put("elevNavn", requireNotNull(elev.navn))
                    put("elevId", requireNotNull(elev.elevId))
                    put("skoleOrgnr", requireNotNull(skole.orgnr))
                    put("skoleNavn", requireNotNull(skole.navn))
                    put("sakNokkel", requireNotNull(sak.nokkel))
                    sak.forslagTittel?.let { put("forslagSakstittel", it) }
                    sak.forslagOffentligTittel?.let { put("forslagOffentligSakstittel", it) }
                    journalpost?.forslagTittel?.let { put("forslagJournalposttittel", it) }
                    journalpost?.forslagOffentligTittel?.let { put("forslagOffentligJournalposttittel", it) }
                    put("hovedDokumentTittel", requireNotNull(hoveddokument.tittel))
                    put("hovedDokumentDato", requireNotNull(hoveddokument.dato))
                    put("hovedDokumentFilnavn", filnavn)
                    put("hovedDokumentMediatype", mediatype)
                    put("hovedDokumentFil", fileId.toString())
                },
        )
    }
}
