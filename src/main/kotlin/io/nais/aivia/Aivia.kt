package io.nais.aivia

import io.prometheus.client.Counter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
        @OptIn(DelicateCoroutinesApi::class)
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
            mutableMapOf<String, Int>().withDefault { 0 }.apply {
                records.asSequence()
                    .forEach { r ->
                        val sourceTopic: String = r.topic()
                        val targetTopic: String = mappingConfig[sourceTopic] as String
                        producer.send(ProducerRecord(targetTopic, r.key(), r.value()))
                        mirroredRecords.labels(sourceTopic, targetTopic).inc()
                        put(sourceTopic, getValue(sourceTopic) + 1)
                    }
                producer.flush()
                consumer.commitSync(Duration.ofSeconds(5))
            }.forEach { (topic, count) ->
                logger.info("-> Mirrored $count records from source topic $topic")
            }
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

