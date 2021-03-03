package io.nais.aivia

import io.ktor.config.ApplicationConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.logging.log4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Properties

class Aivia (
        sourceKafkaConfig: Properties,
        targetKafkaConfig: Properties,
        private val mappingConfig: Properties
) {
    private val consumer = KafkaConsumer(sourceKafkaConfig, ByteArrayDeserializer(), ByteArrayDeserializer())
    private val producer = KafkaProducer(targetKafkaConfig, ByteArraySerializer(), ByteArraySerializer())
    private var isRunning = true
    private val logger = LoggerFactory.getLogger(this::class.java)


    fun mirror() {
        val sourceTopics = mappingConfig.keys.map { it.toString() }.toList()
        consumer.subscribe(sourceTopics)
        while (isRunning) {
            val records = consumer.poll(Duration.of(5, ChronoUnit.SECONDS))
            records.asSequence()
                    .forEach { r ->
                        val sourceTopic: String = r.topic()
                        val targetTopic: String = mappingConfig[sourceTopic] as String
                        producer.send(ProducerRecord(targetTopic, r.key(), r.value()))
                    }
            producer.flush()
            consumer.commitSync(Duration.ofSeconds(2))
        }
        producer.close()
        consumer.close()
    }

    fun shutdown() {
        logger.info("Starting shutdown of Kafka Consumer and Producer")
        isRunning = false
    }
}

fun kafkaConfigFrom(config: ApplicationConfig, serviceUser: ServiceUser? = null): Properties {
    return Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.propertyOrNull("kafka.brokers")?.getString()
                ?: "localhost:29092")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-aivia")
        if (serviceUser != null) {
            putAll(credentials(config, serviceUser))
        }
    }
}

private fun credentials(config: ApplicationConfig, serviceUser: ServiceUser): Properties {
    return Properties().apply {
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        put(SaslConfigs.SASL_JAAS_CONFIG, """org.apache.kafka.common.security.plain.PlainLoginModule required username="${serviceUser.username}" password="${serviceUser.password}"; """)
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getTrustStore(config))
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getTrustStorePassword(config))
    }
}

private fun getTrustStore(config: ApplicationConfig): String {
    val path = config.propertyOrNull("kafka.truststore_path")?.getString() ?: "/etc/ssl/certs/java/cacerts"
    return File(path).absolutePath
}

private fun getTrustStorePassword(config: ApplicationConfig): String {
    return config.propertyOrNull("kafka.truststore_password")?.getString() ?: "changeme"
}

data class ServiceUser(val username: String, val password: String)
