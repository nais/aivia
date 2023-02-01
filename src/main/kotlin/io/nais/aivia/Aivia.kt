package io.nais.aivia

import io.prometheus.client.Counter
import kotlinx.coroutines.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*

private val mirroredRecords = Counter.build()
    .name("aivia_mirrored_records")
    .help("number of records mirrored")
    .labelNames("source", "target")
    .register()

class Aivia(
    private val sourceKafkaConfig: Properties,
    private val targetKafkaConfig: Properties,
    private val mappingConfig: Properties
) {
    private var currentJob: Job? = null
    private var coroutineScope: CoroutineScope? = null
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun run() {
        @OptIn(DelicateCoroutinesApi::class)
        currentJob = GlobalScope.launch {
            coroutineScope = this
            mirror()
        }
    }

    fun isAlive(): Boolean {
        return currentJob?.isActive ?: false
    }

    fun mirror() {
        val sourceTopics = mappingConfig.keys.map { it.toString() }.toList()
        logger.info("Consuming from topics: $sourceTopics")

        KafkaConsumer(sourceKafkaConfig, ByteArrayDeserializer(), ByteArrayDeserializer()).use { consumer ->
            KafkaProducer(targetKafkaConfig, ByteArraySerializer(), ByteArraySerializer()).use { producer ->
                consumer.subscribe(sourceTopics)
                var failed = false
                while (coroutineScope?.isActive == true && !failed) {
                    val records = consumer.poll(Duration.of(5, ChronoUnit.SECONDS))
                    if (records.count() > 0) {
                        logger.info("Found ${records.count()} records to mirror")
                    } else {
                        logger.debug("Found no messages to mirror")
                    }
                    mutableMapOf<String, Int>().withDefault { 0 }.apply {
                        records.asSequence()
                            .forEach { r ->
                                val sourceTopic: String = r.topic()
                                val targetTopic: String = mappingConfig[sourceTopic] as String
                                producer.send(ProducerRecord(targetTopic, r.key(), r.value())) { _, e ->
                                    if (null != e) {
                                        failed = true
                                        logger.error("Failed to produce record: %s", e)
                                        throw IllegalStateException("Failed to produce record", e)
                                    }
                                }
                                mirroredRecords.labels(sourceTopic, targetTopic).inc()
                                put(sourceTopic, getValue(sourceTopic) + 1)
                            }
                        producer.flush()
                        if (!failed) {
                            consumer.commitSync(Duration.ofSeconds(5))
                        }
                    }.forEach { (topic, count) ->
                        logger.info("-> Mirrored $count records from source topic $topic")
                    }
                }
                logger.warn("Completed never-ending loop")
            }
        }
    }

    fun shutdown() {
        logger.info("Starting shutdown of Kafka Consumer and Producer")
        coroutineScope?.cancel()
    }
}

