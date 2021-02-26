package io.nais.aivia

import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.BytesDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.utils.Bytes
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.HashMap

internal const val username = "some-username"
internal const val password = "some-password"

object KafkaWrapper {
    fun bootstrap(topicNames: List<String>): KafkaEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = true,
            withSchemaRegistry = false,
            withSecurity = false, // TODO should get this working
            topicNames = topicNames,
    )
}

internal fun KafkaEnvironment.testClientProperties(): MutableMap<String, Any> {
    return HashMap<String, Any>().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersURL)
        /*
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        put(
            SaslConfigs.SASL_JAAS_CONFIG,
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";"
        )
        */
        put(ConsumerConfig.GROUP_ID_CONFIG, "nais-group")
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }
}

internal fun KafkaEnvironment.isEmpty(topicName: String): Boolean {
    val consumer = KafkaConsumer(this.testClientProperties(), BytesDeserializer(), BytesDeserializer())
    consumer.subscribe(listOf(topicName))
    for(i in 1..30) {
        val records = consumer.poll(Duration.of(30000, ChronoUnit.MILLIS))
        if (!records.isEmpty) {
            return false
        }
    }
    return true
}

internal fun KafkaEnvironment.initializeSourceTopic(name: String, records: List<String>) {
    val clientProperties = testClientProperties()
    val producer = KafkaProducer(clientProperties, StringSerializer(), StringSerializer())
    records.forEach { e ->
        producer.send(ProducerRecord(name, e))
    }
    producer.flush()
}

internal fun Map<String, Any?>.asProperties(): Properties = Properties().apply { putAll(this@asProperties) }
