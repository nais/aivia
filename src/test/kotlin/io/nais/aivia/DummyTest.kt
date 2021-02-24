package io.nais.aivia

import no.nav.common.KafkaEnvironment
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.*

@TestInstance(PER_CLASS)
class DummyTest {
    private val embeddedEnv = KafkaEnvironment()

    init {
        embeddedEnv.start()
    }

    @AfterAll
    fun tearDown() {
        embeddedEnv.tearDown()
    }

    @Test
    fun `vi tester noe`() {
        embeddedEnv.start()
        //TODO
        embeddedEnv.tearDown()
    }
}