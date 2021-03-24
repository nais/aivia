package io.nais.aivia

import io.ktor.application.*
import io.ktor.config.*
import io.ktor.metrics.micrometer.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import org.apache.kafka.common.utils.Exit.addShutdownHook
import java.io.FileInputStream
import java.util.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    Aivia(
        selectConfig(this.environment.config, "source"),
        selectConfig(this.environment.config, "target"),
        mappingConfigFrom(this.environment.config)
    ).also {
        addShutdownHook("Aivia") { it.shutdown() }
        it.run()
        install(Routing) {
            nais(it)
        }
    }

    install(MicrometerMetrics) {
        registry = PrometheusMeterRegistry(
            PrometheusConfig.DEFAULT,
            CollectorRegistry.defaultRegistry,
            Clock.SYSTEM
        )
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics()
        )
    }
}

fun selectConfig(config: ApplicationConfig, role: String): Properties {
    return when (val cluster = config.property("aivia.$role").getString()) {
        "on-prem" -> {
            kafkaOnPremConfigFrom(config, role)
        }
        "aiven" -> {
            kafkaAivenConfigFrom(config, role)
        }
        else -> {
            throw ApplicationConfigurationException("$cluster is invalid value for property $role. Must be set to either `on-prem` or `aiven`.")
        }
    }
}

private fun mappingConfigFrom(config: ApplicationConfig): Properties {
    val topicMappingPath = config.property("aivia.topic_mapping_path").getString()
    val prop = Properties()
    FileInputStream(topicMappingPath).use {
        prop.load(it)
    }
    return prop
}