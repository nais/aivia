package io.nais.aivvia

import io.ktor.application.*
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
}