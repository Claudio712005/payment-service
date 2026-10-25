package com.clau.payment_service.config.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.ConfigurableEnvironment

class ProfileConfigurationTest {

    private fun ambiente(vararg perfis: String): ConfigurableEnvironment {
        val context = GenericApplicationContext()
        if (perfis.isNotEmpty()) {
            context.environment.setActiveProfiles(*perfis)
        }
        ConfigDataApplicationContextInitializer().initialize(context)
        return context.environment
    }

    @Test
    fun `sem perfil declarado a aplicacao cai no local`() {
        val ambiente = ambiente()

        assertEquals(listOf("local"), ambiente.defaultProfiles.toList())
        assertEquals("false", ambiente.getProperty("spring.flyway.enabled"))
        assertEquals("validate", ambiente.getProperty("spring.jpa.hibernate.ddl-auto"))
    }

    @Test
    fun `perfil local nao executa migrations`() {
        val ambiente = ambiente("local")

        assertEquals("false", ambiente.getProperty("spring.flyway.enabled"))
        assertNull(ambiente.getProperty("spring.flyway.locations"))
        assertEquals("validate", ambiente.getProperty("spring.jpa.hibernate.ddl-auto"))
        assertEquals("payment-pool", ambiente.getProperty("spring.datasource.hikari.pool-name"))
        assertEquals("true", ambiente.getProperty("spring.data.mongodb.auto-index-creation"))
    }

    @Test
    fun `perfil local sobe servidor web e expoe os endpoints da aplicacao`() {
        val ambiente = ambiente("local")

        assertNull(ambiente.getProperty("spring.main.web-application-type"))
        assertEquals("/swagger-ui.html", ambiente.getProperty("springdoc.swagger-ui.path"))
        assertEquals("graceful", ambiente.getProperty("server.shutdown"))
    }

    @Test
    fun `perfil migration executa migrations sem servidor web`() {
        val ambiente = ambiente("migration")

        assertEquals("true", ambiente.getProperty("spring.flyway.enabled"))
        assertEquals("classpath:db/migration", ambiente.getProperty("spring.flyway.locations"))
        assertEquals("none", ambiente.getProperty("spring.main.web-application-type"))
        assertEquals("none", ambiente.getProperty("spring.jpa.hibernate.ddl-auto"))
        assertEquals("payment-migracao", ambiente.getProperty("spring.datasource.hikari.pool-name"))
        assertEquals("false", ambiente.getProperty("spring.data.mongodb.auto-index-creation"))
    }

    @Test
    fun `perfil migration nao expoe swagger nem shutdown gracioso`() {
        val ambiente = ambiente("migration")

        assertNull(ambiente.getProperty("springdoc.swagger-ui.path"))
        assertNull(ambiente.getProperty("server.shutdown"))
    }

    @Test
    fun `conexao e politicas de resiliencia sao comuns aos dois perfis`() {
        listOf("local", "migration").forEach { perfil ->
            val ambiente = ambiente(perfil)

            assertTrue(
                ambiente.getProperty("spring.datasource.url")!!.contains("oracle"),
                "perfil $perfil"
            )
            assertEquals("payment-service", ambiente.getProperty("spring.application.name"), "perfil $perfil")
            assertEquals(
                "16",
                ambiente.getProperty("resilience.bulkheads.payment-provider.max-concurrent-calls"),
                "perfil $perfil"
            )
        }
    }

    @Test
    fun `a guarda aceita cada perfil isolado`() {
        MigrationProfileGuard.verify(ambiente("local"))
        MigrationProfileGuard.verify(ambiente("migration"))
        MigrationProfileGuard.verify(ambiente())
    }
}
