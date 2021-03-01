package io.nais.aivia

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.common.KafkaEnvironment
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SOURCE_TOPIC = "some-source-topic"
private const val TARGET_TOPIC = "some-target-topic"

@TestInstance(PER_CLASS)
class KafkaTest {
    private val embeddedEnv = KafkaWrapper.bootstrap(listOf(SOURCE_TOPIC, TARGET_TOPIC))

    init {
        embeddedEnv.start()
    }

    @AfterAll
    fun tearDown() {
        embeddedEnv.tearDown()
    }

    @Test
    fun `Kafka-instansen i minnet har blitt startet`() {
        assertEquals(embeddedEnv.serverPark.status, KafkaEnvironment.ServerParkStatus.Started)
    }

    @Test
    fun `given a source topic with messages, the target topic contains the same messages`() {
        val sourceKafkaConfig = embeddedEnv.testClientProperties().asProperties()
        val targetKafkaConfig = embeddedEnv.testClientProperties().asProperties()
        val mappingConfig = mapOf(
                SOURCE_TOPIC to TARGET_TOPIC
        ).asProperties()

        val aivia = Aivia(sourceKafkaConfig, targetKafkaConfig, mappingConfig)

        val records = listOf("x", "y", "z")
        embeddedEnv.produceToTopic(SOURCE_TOPIC, records)

        assertIsNotEmpty(SOURCE_TOPIC) // source has messages to start with
        assertIsEmpty(TARGET_TOPIC)

        aivia.mirror()

        // assert that target topic contains expected messages
        assertEquals(records, embeddedEnv.records(TARGET_TOPIC))
    }

    @Test
    fun `target topic contains messages published after subscribing`() {
        val sourceKafkaConfig = embeddedEnv.testClientProperties().asProperties()
        val targetKafkaConfig = embeddedEnv.testClientProperties().asProperties()
        val mappingConfig = mapOf(
            SOURCE_TOPIC to TARGET_TOPIC
        ).asProperties()

        val aivia = Aivia(sourceKafkaConfig, targetKafkaConfig, mappingConfig)

        val records = listOf("x", "y", "z")
        embeddedEnv.produceToTopic(SOURCE_TOPIC, records)

        co { aivia.mirror() }


        val records2 = listOf("æ", "ø", "å")
        embeddedEnv.produceToTopic(SOURCE_TOPIC, records2)

        // assert that target topic contains expected messages
        assertContains(TARGET_TOPIC, records + records2)
    }

    fun co(block: () -> Unit) {
        GlobalScope.launch {
            block.invoke()
        }
    }

    private fun assertContains(topic: String, records: List<String>) {
        await("wait until we get a reply")
            .atMost(20, TimeUnit.SECONDS)
            .until {
                if (embeddedEnv.records(TARGET_TOPIC).containsAll(records)) return@until true
                return@until false
            }
    }

    val assertIsNotEmpty = { topic: String -> assertFalse(embeddedEnv.isEmpty(topic), "topic contains records") }
    val assertIsEmpty = { topic: String -> assertTrue(embeddedEnv.isEmpty(topic), "topic is empty") }

}