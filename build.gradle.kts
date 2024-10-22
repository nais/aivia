import org.cyclonedx.gradle.CycloneDxTask

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.cyclonedx.bom") version "1.10.0"
    id("org.gradle.test-retry") version "1.6.0"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://packages.confluent.io/maven")
}

val junitJupiterVersion = "5.11.3"
val kafkaVersion = "2.8.1"
val ktorVersion = "3.0.0"
val log4jVersion = "2.24.1"
val micrometerVersion = "1.13.6"
val prometheusVersion = "0.16.0"
val slf4jVersion = "1.7.30"

group = "io.nais"
version = "generatedlater"

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.prometheus:simpleclient:$prometheusVersion")
    implementation("io.micrometer:micrometer-registry-prometheus-simpleclient:$micrometerVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
    implementation("com.vlkan.log4j2:log4j2-logstash-layout-fatjar:1.0.5")

    testImplementation("org.awaitility:awaitility:4.2.2")
    testImplementation("org.amshove.kluent:kluent:1.73")
    testImplementation("no.nav:kafka-embedded-env:$kafkaVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = true
        }
        retry {
            maxFailures.set(1)
            maxRetries.set(10)
        }
    }

    withType<CycloneDxTask> {
        setOutputFormat("json")
        setIncludeLicenseText(false) 
    }

    named<Jar>("jar") {
        archiveFileName.set("app.jar")

        manifest {
            attributes["Main-Class"] = "io.ktor.server.netty.EngineMain"
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = layout.buildDirectory.dir("libs").get().file(it.name).asFile
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }
}
