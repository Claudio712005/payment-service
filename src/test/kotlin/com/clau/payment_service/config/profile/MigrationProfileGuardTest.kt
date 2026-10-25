package com.clau.payment_service.config.profile

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

class MigrationProfileGuardTest {

    private fun environment(profiles: List<String>, flywayEnabled: Boolean?): StandardEnvironment =
        StandardEnvironment().apply {
            setDefaultProfiles(MigrationProfileGuard.APPLICATION_PROFILE)
            if (profiles.isNotEmpty()) {
                setActiveProfiles(*profiles.toTypedArray())
            }
            if (flywayEnabled != null) {
                propertySources.addFirst(
                    MapPropertySource(
                        "test",
                        mapOf(MigrationProfileGuard.FLYWAY_ENABLED to flywayEnabled.toString())
                    )
                )
            }
        }

    @Test
    fun `aplicacao sem perfil cai no local e passa com flyway desligado`() {
        MigrationProfileGuard.verify(environment(emptyList(), flywayEnabled = false))
    }

    @Test
    fun `perfil local com flyway desligado passa`() {
        MigrationProfileGuard.verify(environment(listOf("local"), flywayEnabled = false))
    }

    @Test
    fun `perfil migration com flyway ligado passa`() {
        MigrationProfileGuard.verify(environment(listOf("migration"), flywayEnabled = true))
    }

    @Test
    fun `flyway ligado fora do perfil migration e recusado`() {
        val erro = assertFailsWith<IllegalStateException> {
            MigrationProfileGuard.verify(environment(listOf("local"), flywayEnabled = true))
        }

        assertTrue(erro.message!!.contains("responsabilidade do Job"), erro.message!!)
    }

    @Test
    fun `flyway ligado sem perfil nenhum e recusado`() {
        assertFailsWith<IllegalStateException> {
            MigrationProfileGuard.verify(environment(emptyList(), flywayEnabled = true))
        }
    }

    @Test
    fun `perfil migration sem flyway e recusado para nao encerrar em silencio`() {
        val erro = assertFailsWith<IllegalStateException> {
            MigrationProfileGuard.verify(environment(listOf("migration"), flywayEnabled = false))
        }

        assertTrue(erro.message!!.contains("codigo 0 sem migrar"), erro.message!!)
    }

    @Test
    fun `os dois perfis juntos sao recusados`() {
        val erro = assertFailsWith<IllegalStateException> {
            MigrationProfileGuard.verify(environment(listOf("local", "migration"), flywayEnabled = true))
        }

        assertTrue(erro.message!!.contains("ativos juntos"), erro.message!!)
    }
}
