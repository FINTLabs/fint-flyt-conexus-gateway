package no.novari.flyt.conexus.gateway

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.novari.flyt.conexus.gateway.instance.ConexusInstance
import no.novari.flyt.conexus.gateway.instance.ConexusInstanceController
import no.novari.flyt.conexus.gateway.instance.ConexusInstanceHandler
import no.novari.flyt.conexus.gateway.metadata.ConexusMetadata
import no.novari.flyt.conexus.gateway.metadata.ConexusMetadataController
import no.novari.flyt.conexus.gateway.metadata.ConexusMetadataHandler
import no.novari.flyt.gateway.instance.error.GlobalExceptionHandler
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup
import org.springframework.web.server.ResponseStatusException

class ConexusApiTest {
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            "conexus-test-client",
            null,
            emptyList(),
        )

    private var processedAuthentication: Authentication? = null
    private var processedInstance: ConexusInstance? = null
    private var processedMetadata: ConexusMetadata? = null
    private val instanceHandler =
        ConexusInstanceHandler { auth, instance ->
            processedAuthentication = auth
            processedInstance = instance
            ResponseEntity.accepted().build()
        }
    private val metadataHandler =
        ConexusMetadataHandler { auth, metadata ->
            processedAuthentication = auth
            processedMetadata = metadata
            ResponseEntity.accepted().build()
        }
    private val mvc = mvc(instanceHandler, metadataHandler)

    @Test
    fun `accepts an instance without title suggestions and preserves the identifier as supplied`() {
        mvc
            .perform(jsonPost(INSTANCES, INSTANCE))
            .andExpect(status().isAccepted)

        assertSame(authentication, processedAuthentication)
        val instance = requireNotNull(processedInstance)
        assertEquals("kartlegging", instance.dokumenttype)
        assertEquals("CONEXUS-TEST-ID", instance.elev?.fnr)
        assertEquals("012345678", instance.skole?.orgnr)
        assertEquals("JVBERi0=", instance.hoveddokument?.filBase64)
        assertNull(instance.sak?.forslagTittel)
        assertNull(instance.journalpost)
    }

    @Test
    fun `accepts title suggestions when supplied`() {
        val body = readInstance()
        (body["sak"] as ObjectNode).put("forslagTittel", "Eksempel på sakstittel")
        body.putObject("journalpost").put("forslagOffentligTittel", "Kartlegging")

        mvc
            .perform(jsonPost(INSTANCES, body.toString()))
            .andExpect(status().isAccepted)

        assertEquals("Eksempel på sakstittel", processedInstance?.sak?.forslagTittel)
        assertEquals("Kartlegging", processedInstance?.journalpost?.forslagOffentligTittel)
    }

    @Test
    fun `accepts metadata registration for a document type`() {
        mvc
            .perform(jsonPost(METADATA, METADATA_BODY))
            .andExpect(status().isAccepted)

        assertSame(authentication, processedAuthentication)
        assertEquals(ConexusMetadata("kartlegging", "Arkivering av kartlegging"), processedMetadata)
    }

    @ParameterizedTest
    @ValueSource(strings = [INSTANCES, METADATA])
    fun `rejects malformed JSON without echoing request content`(path: String) {
        mvc
            .perform(jsonPost(path, """{"synthetic-private-value":"""))
            .andExpect(status().isBadRequest)
            .andExpect(content().string(not(containsString("synthetic-private-value"))))

        assertNull(processedAuthentication)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/dokumentId", "/dokumenttype", "/elev", "/elev/fnr", "/elev/navn", "/elev/elevId",
            "/skole", "/skole/orgnr", "/skole/navn", "/sak", "/sak/nokkel", "/hoveddokument",
            "/hoveddokument/tittel", "/hoveddokument/dato", "/hoveddokument/filnavn",
            "/hoveddokument/mediatype", "/hoveddokument/filBase64",
        ],
    )
    fun `rejects missing required instance fields`(pointer: String) {
        val instance = readInstance()
        (instance.at(pointer.substringBeforeLast('/')) as ObjectNode).remove(pointer.substringAfterLast('/'))

        mvc
            .perform(jsonPost(INSTANCES, instance.toString()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString(pointer.drop(1).replace('/', '.'))))

        assertNull(processedAuthentication)
    }

    @ParameterizedTest
    @CsvSource(
        "/dokumentId, private-not-a-uuid",
        "/dokumenttype, ' '",
        "/skole/orgnr, 123",
        "/hoveddokument/dato, 2026-02-30",
        "/hoveddokument/dato, 2026-06-14T12:00:00",
        "/hoveddokument/mediatype, text/plain",
        "/hoveddokument/filBase64, private-not-base64",
        "/hoveddokument/filBase64, ''",
    )
    fun `rejects invalid instance formats with a field path and without echoing the value`(
        pointer: String,
        value: String,
    ) {
        val instance = readInstance()
        (instance.at(pointer.substringBeforeLast('/')) as ObjectNode).put(pointer.substringAfterLast('/'), value)

        mvc
            .perform(jsonPost(INSTANCES, instance.toString()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString(pointer.drop(1).replace('/', '.'))))
            .andExpect(content().string(not(containsString("private-not"))))

        assertNull(processedAuthentication)
    }

    @Test
    fun `rejects a number where the JSON contract expects a string`() {
        val instance = readInstance()
        (instance["skole"] as ObjectNode).put("orgnr", 123456789)

        mvc
            .perform(jsonPost(INSTANCES, instance.toString()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("orgnr")))
            .andExpect(content().string(not(containsString("123456789"))))

        assertNull(processedAuthentication)
    }

    @Test
    fun `rejects a null student identifier`() {
        val instance = readInstance()
        (instance["elev"] as ObjectNode).putNull("fnr")

        mvc
            .perform(jsonPost(INSTANCES, instance.toString()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("elev.fnr")))

        assertNull(processedAuthentication)
    }

    @ParameterizedTest
    @CsvSource("{}, dokumenttype", "{}, integrationDisplayName", "{\"dokumenttype\":\" \"}, dokumenttype")
    fun `rejects missing or blank metadata fields`(
        body: String,
        field: String,
    ) {
        mvc
            .perform(jsonPost(METADATA, body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString(field)))

        assertNull(processedAuthentication)
    }

    @Test
    fun `preserves a rejection from instance processing`() {
        val rejectingHandler =
            ConexusInstanceHandler { _, _ ->
                throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Integrasjonen er deaktivert")
            }

        mvc(rejectingHandler, metadataHandler)
            .perform(jsonPost(INSTANCES, INSTANCE))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.message").value("Integrasjonen er deaktivert"))
    }

    @ParameterizedTest
    @CsvSource("$INSTANCES, instance", "$METADATA, metadata")
    fun `returns service unavailable when no handler can confirm receipt`(
        path: String,
        request: String,
    ) {
        val body = if (request == "instance") INSTANCE else METADATA_BODY

        mvc(null, null)
            .perform(jsonPost(path, body))
            .andExpect(status().isServiceUnavailable)
    }

    private fun mvc(
        instanceHandler: ConexusInstanceHandler?,
        metadataHandler: ConexusMetadataHandler?,
    ) = standaloneSetup(ConexusInstanceController(instanceHandler), ConexusMetadataController(metadataHandler))
        .setControllerAdvice(GlobalExceptionHandler())
        .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
        .build()

    private fun readInstance() = objectMapper.readTree(INSTANCE) as ObjectNode

    private fun jsonPost(
        path: String,
        body: String,
    ) = post(path)
        .principal(authentication)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)

    private companion object {
        private const val INSTANCES = "/api/conexus/instances"
        private const val METADATA = "/api/conexus/metadata"
        private const val METADATA_BODY =
            """{"dokumenttype":"kartlegging","integrationDisplayName":"Arkivering av kartlegging"}"""
        private const val INSTANCE = """
            {
              "dokumentId": "5a0d2892-9a48-4bef-ae6a-ef8f90bfbe42",
              "dokumenttype": "kartlegging",
              "elev": {"fnr": "CONEXUS-TEST-ID", "navn": "Test Elev", "elevId": "134"},
              "skole": {"orgnr": "012345678", "navn": "Testskolen"},
              "sak": {"nokkel": "test-012345678-134"},
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
