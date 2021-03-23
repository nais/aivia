package io.nais.aivia

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.*
import io.ktor.routing.Route
import io.ktor.routing.get
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat

fun Route.nais(aivia: Aivia) {
    get("/internal/isalive") {
        if (aivia.isAlive()) {
            call.respondText("UP")
        } else {
            call.respond(HttpStatusCode.InternalServerError, "AiviA is dead!")
        }
    }
    get("/internal/isready") {
        if (aivia.isAlive()) {
            call.respondText("UP")
        } else {
            call.respond(HttpStatusCode.InternalServerError, "AiviA is dead!")
        }
    }
    get("/internal/prometheus") {
        val names = call.request.queryParameters.getAll("name")?.toSet() ?: emptySet()
        call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004), HttpStatusCode.OK) {
            TextFormat.write004(this, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(names))
        }
    }
}