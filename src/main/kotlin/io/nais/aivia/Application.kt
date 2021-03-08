package io.nais.aivia

import io.ktor.application.*
import io.ktor.metrics.micrometer.*
import io.ktor.routing.*
import io.ktor.server.engine.*
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
import java.util.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {

    install(Routing) {
        nais()
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
    val source = this.environment.config.property("aivia.source_topic_name").getString()
    val target = this.environment.config.property("aivia.target_topic_name").getString()
    Aivia(kafkaOnPremConfigFrom(this.environment.config),
        kafkaAivenConfigFrom(this.environment.config), mapOf(source to target).asProperties())
        .also {
            addShutdownHook("Aivia") { it.shutdown() }
            it.mirror()
    }
}

internal fun Map<String, Any?>.asProperties(): Properties = Properties().apply { putAll(this@asProperties) }
