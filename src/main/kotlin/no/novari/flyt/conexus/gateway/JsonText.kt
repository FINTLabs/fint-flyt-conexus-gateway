package no.novari.flyt.conexus.gateway

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@JsonDeserialize(using = JsonTextDeserializer::class)
internal annotation class JsonText

internal class JsonTextDeserializer : StdScalarDeserializer<String>(String::class.java) {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): String =
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            parser.text
        } else {
            context.reportInputMismatch(String::class.java, "Expected a JSON string")
        }
}
