package no.novari.flyt.conexus.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["no.novari"])
class ConexusGatewayApplication

fun main(args: Array<String>) {
    runApplication<ConexusGatewayApplication>(*args)
}
