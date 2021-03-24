package io.nais.aivia

import io.ktor.config.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ApplicationKtTest {

    lateinit var config: MapApplicationConfig

    @BeforeEach
    fun createConfig() {
        config = MapApplicationConfig().apply {
            put("kafkaOnPrem.brokers", "on-prem-brokers")
            put("kafkaAiven.brokers", "aiven-brokers")
            put("kafkaAiven.truststore_path", "aiven-truststore-path")
            put("kafkaAiven.keystore_path", "aiven-keystore-path")
            put("kafkaAiven.credstore_password", "aiven-credstore-password")
            put("aivia.groupId", "test-group-id")
        }
    }

    @ParameterizedTest
    @CsvSource("source,on-prem", "target,on-prem", "source,aiven", "target,aiven")
    fun `select on-prem config when role is on-prem`(role: String, cluster: String) {
        config.put("aivia.$role", cluster)
        val props = selectConfig(config, role)

        val property =
            if (role == "source") ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG else ProducerConfig.BOOTSTRAP_SERVERS_CONFIG
        assertEquals(props[property], "$cluster-brokers")
    }

    @Test
    fun `selectConfig throws exception on unknown cluster`() {
        config.put("aivia.source", "invalid")
        assertThrows<ApplicationConfigurationException> {
            selectConfig(config, "source")
        }
    }
}
