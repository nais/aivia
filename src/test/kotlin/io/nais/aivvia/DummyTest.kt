package io.nais.aivvia

import no.nav.common.KafkaEnvironment
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class DummyTest {


    @Test
    fun `vi tester noe`() {
        val kafkaEnv = KafkaEnvironment()

        kafkaEnv.start()


        kafkaEnv.tearDown()
    }
}