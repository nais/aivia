package io.nais.aivia

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.common.KafkaEnvironment.TopicInfo
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SOURCE_TOPIC = "some-source-topic"
private const val TARGET_TOPIC = "some-target-topic"
private val THIRTY_SECONDS = Duration.ofSeconds(30)

private const val ORDERING_TOPIC_TARGET = "orderingtopic_target"
private const val ORDERING_TOPIC_SOURCE = "orderingtopic_source"

@TestInstance(PER_CLASS)
class KafkaTest {
    private val topicInfos = listOf(TopicInfo(ORDERING_TOPIC_SOURCE, 3), TopicInfo(ORDERING_TOPIC_TARGET, 3))
    private val embeddedEnv = KafkaWrapper.bootstrap(listOf(SOURCE_TOPIC, TARGET_TOPIC), topicInfos)

    init {
        embeddedEnv.start()
    }

    @AfterAll
    fun tearDown() {
        embeddedEnv.tearDown()
    }

    @Test
    fun `messages are continuously mirrored between source and target topic`() {
        val sourceKafkaConfig = embeddedEnv.testClientProperties().asProperties()
        val targetKafkaConfig = embeddedEnv.testClientProperties().asProperties()
        val mappingConfig = mapOf(
                SOURCE_TOPIC to TARGET_TOPIC
        ).asProperties()

        val aivia = Aivia(sourceKafkaConfig, targetKafkaConfig, mappingConfig)

        val records = listOf("x", "y", "z")
        embeddedEnv.produceToTopic(SOURCE_TOPIC, records)

        val job = co { aivia.mirror() }

        await().atMost(THIRTY_SECONDS).untilAsserted {
            assertTrue(embeddedEnv.records(TARGET_TOPIC).values.containsAll(records), "first batch mirrored")
        }

        val records2 = listOf("æ", "ø", "å")
        embeddedEnv.produceToTopic(SOURCE_TOPIC, records2)

        await().atMost(THIRTY_SECONDS).untilAsserted {
            assertTrue(embeddedEnv.records(TARGET_TOPIC).values.containsAll(records + records2), "second batch mirrored")
        }

        job.cancel()
    }

    @Test
    fun `records retains ordering inside partition after mirroring`() {
        embeddedEnv.produceToTopic(ORDERING_TOPIC_SOURCE, (0..68).map { it.toString() })

        val sourceKafkaConfig = embeddedEnv.testClientProperties().asProperties()
        val targetKafkaConfig = embeddedEnv.testClientProperties().asProperties()
        val mappingConfig = mapOf(
                ORDERING_TOPIC_SOURCE to ORDERING_TOPIC_TARGET
        ).asProperties()

        val aivia = Aivia(sourceKafkaConfig, targetKafkaConfig, mappingConfig)
        val job = co { aivia.mirror() }

        embeddedEnv.produceToTopic(ORDERING_TOPIC_SOURCE, (0..68).map { it.toString() })

        await().atMost(THIRTY_SECONDS).untilAsserted {
            assertEquals(69 * 2, embeddedEnv.records(ORDERING_TOPIC_TARGET)?.count())
        }

        assertTrue(embeddedEnv.equalOrdering(ORDERING_TOPIC_SOURCE, ORDERING_TOPIC_TARGET),
            "Partitions should be equal ordered")

        job.cancel()
    }



    private fun co(block: () -> Unit): Job {
        return GlobalScope.launch {
            block.invoke()
        }
    }

    val assertIsNotEmpty = { topic: String -> assertFalse(embeddedEnv.isEmpty(topic), "topic contains records") }
    val assertIsEmpty = { topic: String -> assertTrue(embeddedEnv.isEmpty(topic), "topic is empty") }
}
