# Perfis

Dois perfis, um arquivo cada, sem documento multi-perfil no mesmo YAML.

| Arquivo | Quem ativa | Para quê |
|---|---|---|
| `application.yaml` | sempre | o que é comum: nome, conexões, resiliência, nível de log |
| `application-local.yaml` | a **aplicação** | servir HTTP: pool, `ddl-auto: validate`, actuator, swagger |
| `application-migration.yaml` | o **Job do Kubernetes** | migrar o schema e encerrar |

`spring.profiles.default: local` está em `application.yaml`: `./gradlew bootRun` e
`java -jar` sobem a aplicação sem nenhuma flag. O perfil `migration` **nunca** é default —
tem que ser pedido.

## Migrations são do Job, não da aplicação

`spring.flyway.enabled` é `false` em `application.yaml`. Só `application-migration.yaml` liga.

A aplicação sobe com `ddl-auto: validate`. Schema não migrado → o pod falha no startup em vez
de atender com schema errado. Ela nunca migra, nem como fallback.

O Job roda a mesma imagem com `SPRING_PROFILES_ACTIVE=migration`. Nesse perfil o Boot sobe sem
servidor web, o Flyway migra durante o refresh do contexto e o processo encerra: saída `0` em
sucesso, diferente de `0` em falha — que é o que o Job lê.

Os dois lados declaram o perfil **explicitamente** nos manifests:

| Recurso | `SPRING_PROFILES_ACTIVE` |
|---|---|
| `deployment.yaml` | `local` |
| `migracao-job.yaml` | `migration` |

## A regra é verificada na subida

`MigrationProfileGuard` é um `BeanFactoryPostProcessor`: roda antes de qualquer bean, Flyway
incluído. Três combinações são recusadas, com a causa no texto do erro.

| Situação | Por que falha |
|---|---|
| `spring.flyway.enabled=true` fora do perfil `migration` | alguém devolveu a migração para a aplicação |
| perfil `migration` com `spring.flyway.enabled=false` | o Job encerraria com código `0` **sem migrar nada**, e o deploy seguiria sobre schema desatualizado |
| perfis `migration` e `local` ativos juntos | o perfil `migration` sobe sem servidor web; junto com `local` o resultado é indefinido |

A segunda é a que justifica a guarda existir: um Job que sai `0` sem fazer nada é pior que um
Job que quebra, porque o `kubectl wait --for=condition=complete` passa e ninguém olha.

## O que cada perfil muda

| Propriedade | `local` | `migration` |
|---|---|---|
| `spring.flyway.enabled` | `false` | `true` |
| `spring.jpa.hibernate.ddl-auto` | `validate` | `none` |
| `spring.main.web-application-type` | padrão (servlet) | `none` |
| `spring.datasource.hikari.pool-name` | `payment-pool` | `payment-migracao` |
| `spring.datasource.hikari.maximum-pool-size` | `10` | `1` |
| `spring.data.mongodb.auto-index-creation` | `true` | `false` |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | ausente |
| `server.shutdown` | `graceful` | ausente |

Conexão com Oracle, MongoDB e Redis, nome da aplicação e políticas de resiliência vivem em
`application.yaml` e valem nos dois — o Job usa o mesmo banco que a aplicação valida.

`ProfileConfigurationTest` carrega os arquivos com o mesmo mecanismo do Boot
(`ConfigDataApplicationContextInitializer`) e fixa cada linha dessa tabela, de forma que mover
uma propriedade para o arquivo errado quebra o teste.
