package no.novari.flyt.example.gateway.instance.advanced

import no.novari.flyt.example.gateway.instance.model.AdvancedExample
import no.novari.flyt.example.gateway.instance.model.CaseWorker
import no.novari.flyt.example.gateway.instance.model.Document
import no.novari.flyt.example.gateway.instance.model.FileContent
import no.novari.flyt.gateway.instance.model.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class AdvancedMappingServiceTest {
    private val service = AdvancedMappingService()

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `maps advanced example with documents and case workers`() {
        val fileBytes = byteArrayOf(1, 2, 3)
        val document =
            Document(
                title = "Original title",
                fileContent =
                    FileContent(
                        fileName = "attachment.pdf",
                        mimeType = "application/pdf",
                        base64 = fileBytes,
                    ),
            )
        val caseWorker = CaseWorker(email = "caseworker@example.com", name = "Case Worker")
        val input =
            AdvancedExample(
                sysId = "sys-adv-1",
                string1 = "hello",
                int1 = 7,
                documents = listOf(document),
                caseWorkers = listOf(caseWorker),
            )

        val capturedFiles = mutableListOf<File>()
        val expectedFileId = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6")

        val result =
            service.map(
                sourceApplicationId = 99L,
                incomingInstance = input,
                persistFile = { file ->
                    capturedFiles += file
                    expectedFileId
                },
            )

        assertEquals(1, capturedFiles.size)
        val capturedFile = capturedFiles.single()
        assertEquals("attachment.pdf", capturedFile.name)
        assertEquals(99L, capturedFile.sourceApplicationId)
        assertEquals("sys-adv-1", capturedFile.sourceApplicationInstanceId)
        assertEquals(MediaType.APPLICATION_PDF, capturedFile.type)
        assertEquals("UTF-8", capturedFile.encoding)
        assertEquals(Base64.encode(fileBytes), capturedFile.base64Contents)

        assertEquals(
            mapOf(
                "sysId" to "sys-adv-1",
                "string1" to "hello",
                "int1" to "7",
            ),
            result.valuePerKey,
        )

        val documentObjects = result.objectCollectionPerKey.getValue("documents")
        val caseWorkerObjects = result.objectCollectionPerKey.getValue("caseWorkers")

        assertEquals(1, documentObjects.size)
        assertEquals(1, caseWorkerObjects.size)

        val documentObject = documentObjects.single()
        assertEquals(
            mapOf(
                "title" to "attachment.pdf",
                "fileName" to "attachment.pdf",
                "mediaType" to "application/pdf",
                "file" to expectedFileId.toString(),
            ),
            documentObject.valuePerKey,
        )

        val caseWorkerObject = caseWorkerObjects.single()
        assertEquals(
            mapOf(
                "email" to "caseworker@example.com",
                "name" to "Case Worker",
            ),
            caseWorkerObject.valuePerKey,
        )

        assertTrue(documentObject.objectCollectionPerKey.isEmpty())
        assertTrue(caseWorkerObject.objectCollectionPerKey.isEmpty())
    }
}
