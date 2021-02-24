package io.nais.aivia

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.*

private const val SOURCE_TOPIC = "some-source-topic"
private const val TARGET_TOPIC = "some-target-topic"

@TestInstance(PER_CLASS)
class DummyTest {
    private val embeddedEnv = KafkaWrapper.bootstrap(listOf(SOURCE_TOPIC, TARGET_TOPIC))

    init {
        embeddedEnv.start()
        embeddedEnv.initializeSourceTopic(SOURCE_TOPIC)
    }

    @AfterAll
    fun tearDown() {
        embeddedEnv.tearDown()
    }

    @Test
    fun `given a source topic with messages, the target topic contains the same messages`() {
        // TODO
        val sourceKafkaConfig = embeddedEnv.testClientProperties().asProperties()
        val targetKafkaConfig = embeddedEnv.testClientProperties().asProperties()
        val mappingConfig = mapOf(
            SOURCE_TOPIC to TARGET_TOPIC
        ).asProperties()

        val aivia = Aivia(sourceKafkaConfig, targetKafkaConfig, mappingConfig)

        // assert that target topic is empty
        // assert that source topic contains expected messages
        aivia.mirror()

        // assert that target topic contains expected messages
    }
}