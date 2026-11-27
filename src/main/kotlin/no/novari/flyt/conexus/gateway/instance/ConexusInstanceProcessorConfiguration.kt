package no.novari.flyt.conexus.gateway.instance

import no.novari.flyt.gateway.instance.InstanceProcessor
import no.novari.flyt.gateway.instance.InstanceProcessorFactoryService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ConexusInstanceProcessorConfiguration {
    @Bean
    fun conexusInstanceProcessor(
        factory: InstanceProcessorFactoryService,
    ): InstanceProcessor<ValidatedConexusInstance> =
        factory.createInstanceProcessor(
            sourceApplicationIntegrationIdFunction = { requireNotNull(it.request.dokumenttype) },
            sourceApplicationInstanceIdFunction = { requireNotNull(it.request.dokumentId) },
            instanceMapper = ConexusInstanceMapper(),
        )
}
