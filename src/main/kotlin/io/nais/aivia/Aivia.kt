package io.nais.aivia

import io.ktor.config.*
import io.prometheus.client.Counter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*

private val mirroredRecords = Counter.build()
    .name("aivia_mirrored_records")
    .help("number of records mirrored")
    .labelNames("source", "target")
    .register()

class Aivia(
    sourceKafkaConfig: Properties,
    targetKafkaConfig: Properties,
    private val mappingConfig: Properties
) {
    private val consumer = KafkaConsumer(sourceKafkaConfig, ByteArrayDeserializer(), ByteArrayDeserializer())
    private val producer = KafkaProducer(targetKafkaConfig, ByteArraySerializer(), ByteArraySerializer())

    private var isRunning = true
    private var currentJob: Job? = null
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun run() {
        currentJob = GlobalScope.launch {
            isRunning = true
            mirror()
        }
    }

    fun isAlive(): Boolean {
        return currentJob?.isActive ?: false
    }

    fun mirror() {
        val sourceTopics = mappingConfig.keys.map { it.toString() }.toList()
        logger.info("Consuming from topics: $sourceTopics")
        consumer.subscribe(sourceTopics)
        while (isRunning) {
            val records = consumer.poll(Duration.of(5, ChronoUnit.SECONDS))
            if (records.count() > 0) {
                logger.info("Found ${records.count()} records to mirror")
            } else {
                logger.debug("Found no messages to mirror")
            }
            records.asSequence()
                .forEach { r ->
                    val sourceTopic: String = r.topic()
                    val targetTopic: String = mappingConfig[sourceTopic] as String
                    producer.send(ProducerRecord(targetTopic, r.key(), r.value()))
                    mirroredRecords.labels(sourceTopic, targetTopic).inc()
                }
            producer.flush()
            consumer.commitSync(Duration.ofSeconds(5))
        }
        logger.info("Completed never-ending loop")
        producer.close()
        consumer.close()
    }

    fun shutdown() {
        logger.info("Starting shutdown of Kafka Consumer and Producer")
        isRunning = false
    }
}

fun mappingConfigFrom(config: ApplicationConfig): Properties {
    val topicMappingPath = config.property("aivia.topic_mapping_path").getString()
    val prop = Properties()
    FileInputStream(topicMappingPath).use {
        prop.load(it)
    }
    return prop
}

fun kafkaAivenConfigFrom(config: ApplicationConfig, role: String): Properties {
    return Properties().apply {
        if (role == "target") {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.property("kafkaAiven.brokers").getString())
            commonProducerConfig()
        } else {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.property("kafkaAiven.brokers").getString())
            commonConsumerConfig(config)
        }
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, config.property("kafkaAiven.truststore_path").getString())
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, config.property("kafkaAiven.credstore_password").getString())
        put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, config.property("kafkaAiven.keystore_path").getString())
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, config.property("kafkaAiven.credstore_password").getString())
    }
}

fun kafkaOnPremConfigFrom(config: ApplicationConfig, role: String): Properties {
    return Properties().apply {
        if (role == "target") {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.property("kafkaOnPrem.brokers").getString())
            commonProducerConfig()
        } else {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.property("kafkaOnPrem.brokers").getString())
            commonConsumerConfig(config)
        }
        putAll(credentials(config))
    }
}

private fun Properties.commonProducerConfig() {
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
    put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true") // Acks=ALL, Retries=maxint, Max inflight request=1
}

private fun Properties.commonConsumerConfig(config: ApplicationConfig) {
    put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java)
    put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java)
    put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    put(ConsumerConfig.GROUP_ID_CONFIG, config.property("aivia.groupId").getString())
}

private fun credentials(config: ApplicationConfig): Properties {
    return Properties().apply {
        serviceUser(config.config("kafkaOnPrem.serviceuser"))?.also { serviceUser ->
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(
                SaslConfigs.SASL_JAAS_CONFIG,
                """org.apache.kafka.common.security.plain.PlainLoginModule required username="${serviceUser.username}" password="${serviceUser.password}"; """
            )
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getTrustStore(config))
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getTrustStorePassword(config))
        }
    }
}

private fun getTrustStore(config: ApplicationConfig): String {
    val path = config.propertyOrNull("kafkaOnPrem.truststore_path")?.getString() ?: "/etc/ssl/certs/java/cacerts"
    return File(path).absolutePath
}

private fun getTrustStorePassword(config: ApplicationConfig): String {
    return config.propertyOrNull("kafkaOnPrem.truststore_password")?.getString() ?: "changeme"
}

fun serviceUser(appConfig: ApplicationConfig): ServiceUser? {
    if (appConfig.propertyOrNull("username") != null) {
        return ServiceUser(
            username = appConfig.property("username").getString(),
            password = appConfig.propertyOrNull("password")?.getString() ?: ""
        )
    }
    return null
}

data class ServiceUser(val username: String, val password: String)
