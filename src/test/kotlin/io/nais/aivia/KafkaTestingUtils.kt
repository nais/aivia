package io.nais.aivia

import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.common.KafkaEnvironment.TopicInfo
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.BytesDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.utils.Bytes
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap

internal const val username = "some-username"
internal const val password = "some-password"

object KafkaWrapper {
    fun bootstrap(topicNames: List<String>, topicInfos: List<TopicInfo> = emptyList()): KafkaEnvironment =
        KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = true,
            withSchemaRegistry = false,
            withSecurity = false, // TODO should get this working
            topicNames = topicNames,
            topicInfos = topicInfos,
        )
}

internal fun KafkaEnvironment.testClientProperties(): MutableMap<String, Any> {
    return HashMap<String, Any>().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersURL)
        put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString())
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }
}

internal fun KafkaEnvironment.produceToTopic(name: String, records: List<String>) {
    val clientProperties = testClientProperties()
    clientProperties[ProducerConfig.BATCH_SIZE_CONFIG] = 0
    clientProperties[ProducerConfig.LINGER_MS_CONFIG] = 0

    val producer = KafkaProducer(clientProperties, StringSerializer(), StringSerializer())
    records.forEach { r ->
        producer.send(ProducerRecord(name, r, r))
        producer.flush()
    }
}

internal fun KafkaEnvironment.records(topicName: String): ConsumerRecords<String, String>? {
    KafkaConsumer(this.testClientProperties(), StringDeserializer(), StringDeserializer())
        .use { consumer ->
            consumer.subscribe(listOf(topicName))
            return consumer.poll(Duration.of(10, ChronoUnit.SECONDS))
        }
}

internal fun KafkaEnvironment.equalOrdering(some: String, other: String): Boolean {
    val somePartitions = records(some)?.groupByTo(LinkedHashMap(), { r -> r.partition() }, { r -> r.value() })
    val otherPartitions = records(other)?.groupByTo(LinkedHashMap(), { r -> r.partition() }, { r -> r.value() })
    somePartitions?.values?.forEach {
        if (!otherPartitions?.containsValue(it)!!) {
            return false
        }
    }

    return true
}

internal val ConsumerRecords<String, String>?.values get() = this?.map { r -> r.value() } ?: emptyList()

