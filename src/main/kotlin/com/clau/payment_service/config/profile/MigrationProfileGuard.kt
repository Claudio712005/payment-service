package com.clau.payment_service.config.profile

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

@Component
class MigrationProfileGuard : BeanFactoryPostProcessor {

    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        verify(beanFactory.getBean(Environment::class.java))
    }

    companion object {

        const val MIGRATION_PROFILE = "migration"
        const val APPLICATION_PROFILE = "local"
        const val FLYWAY_ENABLED = "spring.flyway.enabled"

        fun verify(environment: Environment) {
            val migrationActive = environment.acceptsProfiles(Profiles.of(MIGRATION_PROFILE))
            val applicationActive = environment.acceptsProfiles(Profiles.of(APPLICATION_PROFILE))
            val flywayEnabled = environment.getProperty(FLYWAY_ENABLED, Boolean::class.java, false)

            if (migrationActive && applicationActive) {
                throw IllegalStateException(
                    "perfis '$MIGRATION_PROFILE' e '$APPLICATION_PROFILE' ativos juntos: " +
                        "o perfil '$MIGRATION_PROFILE' pertence ao Job do Kubernetes e sobe sem servidor web. " +
                        "Use SPRING_PROFILES_ACTIVE=$MIGRATION_PROFILE no Job e " +
                        "SPRING_PROFILES_ACTIVE=$APPLICATION_PROFILE na aplicacao."
                )
            }

            if (flywayEnabled && !migrationActive) {
                throw IllegalStateException(
                    "$FLYWAY_ENABLED=true fora do perfil '$MIGRATION_PROFILE': " +
                        "migrations sao responsabilidade do Job do Kubernetes, nao da aplicacao. " +
                        "A aplicacao sobe com ddl-auto=validate e falha se o schema nao estiver migrado."
                )
            }

            if (migrationActive && !flywayEnabled) {
                throw IllegalStateException(
                    "perfil '$MIGRATION_PROFILE' ativo com $FLYWAY_ENABLED=false: " +
                        "o Job encerraria com codigo 0 sem migrar nada e o deploy seguiria sobre " +
                        "schema desatualizado."
                )
            }
        }
    }
}
