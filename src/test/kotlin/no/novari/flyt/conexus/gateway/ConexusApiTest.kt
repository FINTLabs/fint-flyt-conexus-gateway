package no.novari.flyt.conexus.gateway

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import no.novari.flyt.conexus.gateway.instance.ConexusInstance
import no.novari.flyt.conexus.gateway.instance.ConexusInstanceController
import no.novari.flyt.conexus.gateway.instance.ConexusInstanceHandler
import no.novari.flyt.conexus.gateway.metadata.ConexusMetadataController
import no.novari.flyt.conexus.gateway.metadata.ConexusMetadataMapper
import no.novari.flyt.conexus.gateway.metadata.ConexusMetadataValidator
import no.novari.flyt.gateway.instance.error.GlobalExceptionHandler
import no.novari.flyt.gateway.metadata.model.InstanceValueMetadata
import no.novari.flyt.gateway.metadata.model.IntegrationMetadata
import no.novari.flyt.webresourceserver.security.client.sourceapplication.SourceApplicationAuthorizationService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup
import org.springframework.web.client.RestClient
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
    private var registeredMetadata: IntegrationMetadata? = null
    private val instanceHandler =
        ConexusInstanceHandler { auth, instance ->
            processedAuthentication = auth
            processedInstance = instance
            ResponseEntity.accepted().build()
        }
    private val authorizationService =
        mock(SourceApplicationAuthorizationService::class.java).also {
            `when`(it.getSourceApplicationId(authentication)).thenReturn(117L)
        }
    private val catalogBuilder = RestClient.builder().baseUrl("http://catalog/beta/innlandetfylke-no/api/intern-klient")
    private val catalog = MockRestServiceServer.bindTo(catalogBuilder).build()
    private val metadataController =
        ConexusMetadataController(
            catalogBuilder.build(),
            authorizationService,
            ConexusMetadataMapper(),
            ConexusMetadataValidator(),
        )
    private val mvc = mvc(instanceHandler)

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
    fun `registers the documented metadata example with its version and Flyt keys`() {
        val body = readMetadata()
        (body["metadata"] as ObjectNode).put("version", 2L)
        expectRegistration(2, HttpStatus.CREATED)

        mvc
            .perform(jsonPost(METADATA, body.toString()))
            .andExpect(status().isAccepted)

        catalog.verify()
        verify(authorizationService).getSourceApplicationId(authentication)
        val registered = requireNotNull(registeredMetadata)
        assertEquals(117L, registered.sourceApplicationId)
        assertEquals("kartlegging", registered.sourceApplicationIntegrationId)
        assertEquals("Arkivering av kartlegging", registered.integrationDisplayName)
        assertEquals(2L, registered.version)
        val fields = registered.instanceMetadata.instanceValueMetadata
        assertEquals(InstanceValueMetadata.Type.STRING, fields.single { it.key == "elevFnr" }.type)
        assertEquals("Forslag til sakstittel", fields.single { it.key == "forslagSakstittel" }.displayName)
        val document = registered.instanceMetadata.categories.single()
        assertEquals("Hoveddokument", document.displayName)
        assertEquals(
            InstanceValueMetadata.Type.FILE,
            document.content.instanceValueMetadata
                .single { it.key == "hovedDokumentFil" }
                .type,
        )
        assertEquals(5, document.content.instanceValueMetadata.size)
    }

    @Test
    fun `reports a duplicate version and accepts a new version`() {
        expectRegistration(1, HttpStatus.CREATED)
        expectRegistration(1, HttpStatus.CONFLICT)
        expectRegistration(2, HttpStatus.CREATED)
        val body = readMetadata()

        mvc.perform(jsonPost(METADATA, body.toString())).andExpect(status().isAccepted)
        mvc
            .perform(jsonPost(METADATA, body.toString()))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Metadataversjon 1 finnes allerede"))

        (body["metadata"] as ObjectNode).put("version", 2L)
        mvc.perform(jsonPost(METADATA, body.toString())).andExpect(status().isAccepted)
        catalog.verify()
    }

    @Test
    fun `does not accept metadata without confirmation of registration`() {
        expectRegistration(1, HttpStatus.ACCEPTED)

        mvc
            .perform(jsonPost(METADATA, readMetadata().toString()))
            .andExpect(status().isBadGateway)

        catalog.verify()
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
    @ValueSource(
        strings = [
            "/metadata/dokumenttype",
            "/metadata/integrationDisplayName",
            "/metadata/version",
            "/elements",
            "/elements/0/type",
        ],
    )
    fun `rejects missing metadata properties`(pointer: String) {
        val body = readMetadata()
        (body.at(pointer.substringBeforeLast('/')) as ObjectNode).remove(pointer.substringAfterLast('/'))

        mvc
            .perform(jsonPost(METADATA, body.toString()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString(pointer.substringAfterLast('/'))))

        assertNull(registeredMetadata)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "1.1", "null", "true", "\"2\""])
    fun `rejects values other than positive integer versions`(value: String) {
        val body = readMetadata()
        (body["metadata"] as ObjectNode).replace("version", objectMapper.readTree(value))

        mvc
            .perform(jsonPost(METADATA, body.toString()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("version")))

        assertNull(registeredMetadata)
    }

    @ParameterizedTest
    @CsvSource(
        "id, elevNavn, dupliserer",
        "type, Boolean, må være String eller File",
    )
    fun `rejects duplicate field IDs and unsupported types`(
        name: String,
        value: String,
        error: String,
    ) {
        val body = readMetadata()
        val element = body["elements"].single { it["id"].asText() == "elevFnr" } as ObjectNode
        element.put(name, value)

        mvc
            .perform(jsonPost(METADATA, body.toString()))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.message").value(containsString(error)))

        assertNull(registeredMetadata)
    }

    @Test
    fun `registers a new version with Conexus-defined fields replacing an existing field`() {
        val body = readMetadata()
        (body["metadata"] as ObjectNode).put("version", 2L)
        val elements = body["elements"] as ArrayNode
        elements.remove(elements.indexOfFirst { it["id"].asText() == "elevNavn" })
        elements
            .addObject()
            .put("id", "elevFornavn")
            .put("displayName", "Elevens fornavn")
            .put("type", "String")
            .put("required", true)
        elements
            .addObject()
            .put("id", "elevEtternavn")
            .put("displayName", "Elevens etternavn")
            .put("type", "String")
            .put("required", true)
        val identifier = elements.single { it["id"].asText() == "elevFnr" } as ObjectNode
        identifier.put("required", false)
        expectRegistration(2, HttpStatus.CREATED)

        mvc
            .perform(jsonPost(METADATA, body.toString()))
            .andExpect(status().isAccepted)

        catalog.verify()
        val fields = requireNotNull(registeredMetadata).instanceMetadata.instanceValueMetadata
        assertNull(fields.find { it.key == "elevNavn" })
        assertEquals("Elevens fornavn", fields.single { it.key == "elevFornavn" }.displayName)
        assertEquals("Elevens etternavn", fields.single { it.key == "elevEtternavn" }.displayName)
    }

    @Test
    fun `preserves a rejection from instance processing`() {
        val rejectingHandler =
            ConexusInstanceHandler { _, _ ->
                throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Integrasjonen er deaktivert")
            }

        mvc(rejectingHandler)
            .perform(jsonPost(INSTANCES, INSTANCE))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.message").value("Integrasjonen er deaktivert"))
    }

    @Test
    fun `returns service unavailable when no instance handler can confirm receipt`() {
        mvc(null)
            .perform(jsonPost(INSTANCES, INSTANCE))
            .andExpect(status().isServiceUnavailable)
    }

    private fun mvc(instanceHandler: ConexusInstanceHandler?) =
        standaloneSetup(ConexusInstanceController(instanceHandler), metadataController)
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .build()

    private fun expectRegistration(
        version: Long,
        responseStatus: HttpStatus,
    ) {
        catalog
            .expect(requestTo("http://catalog/beta/innlandetfylke-no/api/intern-klient/metadata"))
            .andExpect(method(HttpMethod.POST))
            .andExpect { request ->
                val metadata =
                    objectMapper.readValue(
                        (request as MockClientHttpRequest).bodyAsString,
                        IntegrationMetadata::class.java,
                    )
                assertEquals(version, metadata.version)
                registeredMetadata = metadata
            }.andRespond(withStatus(responseStatus))
    }

    private fun readMetadata(): ObjectNode {
        val operation =
            ConexusMetadataController::class.java.declaredMethods
                .single { it.name == "postMetadata" }
                .getAnnotation(Operation::class.java)
        return objectMapper.readTree(
            operation.requestBody.content
                .single()
                .examples
                .single()
                .value,
        ) as ObjectNode
    }

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
