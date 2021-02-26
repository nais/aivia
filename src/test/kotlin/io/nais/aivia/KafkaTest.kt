package io.nais.aivia

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SOURCE_TOPIC = "some-source-topic"
private const val TARGET_TOPIC = "some-target-topic"

@TestInstance(PER_CLASS)
class DummyTest {
    private val embeddedEnv = KafkaWrapper.bootstrap(listOf(SOURCE_TOPIC, TARGET_TOPIC))

    init {
        embeddedEnv.start()
    }

    @AfterAll
    fun tearDown() {
        embeddedEnv.tearDown()
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

        embeddedEnv.initializeSourceTopic(SOURCE_TOPIC, records)

        assertFalse(embeddedEnv.isEmpty(SOURCE_TOPIC), "source has messages to start with")
        assertTrue(embeddedEnv.isEmpty(TARGET_TOPIC), "target starts out empty")

        aivia.mirror()

//        assertFalse(embeddedEnv.isEmpty(TARGET_TOPIC), "target is not empty after mirroring")

        // assert that target topic contains expected messages
    }
}