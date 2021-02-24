package io.nais.aivia

import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

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
    }
}

internal fun KafkaEnvironment.initializeSourceTopic(name: String) {
    val producer = KafkaProducer(testClientProperties(), StringSerializer(), StringSerializer())
    producer.send(ProducerRecord(name, "some-value"))
    producer.send(ProducerRecord(name, "some-other-value"))
    producer.send(ProducerRecord(name, "another-value"))
    producer.flush()
}

internal fun Map<String, Any?>.asProperties(): Properties = Properties().apply { putAll(this@asProperties) }
