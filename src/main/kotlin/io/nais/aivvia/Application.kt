package io.nais.aivvia

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
   install(Routing){
       get("/"){
           call.respondText("Hi from aivvia")
       }
   }
}