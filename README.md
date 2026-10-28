# payment-service

Serviço de pagamento da ServiceTrack: cobrança de uma ordem de serviço, conversa com o provedor
de pagamento e emissão da nota fiscal.

Kotlin 2.3 sobre Spring Boot 4.1, JDK 25. Oracle para o estado transacional, MongoDB para
documento de provedor e histórico, Redis para cache.

## Rodar

```bash
./gradlew bootRun
```

Sem perfil e sem variável: os defaults do `application.yaml` apontam para `localhost`. Oracle,
MongoDB e Redis são externos — precisam estar no ar, exceto o Redis, que é opcional.

```bash
./gradlew test
```

## Documentação

| Documento | Assunto |
|---|---|
| [docs/api.md](docs/api.md) | contratos, i18n, validação e formato de erro |
| [docs/cache.md](docs/cache.md) | cache em Redis por Jedis, e por que ele não derruba a aplicação |
| [docs/resiliencia.md](docs/resiliencia.md) | bulkhead, circuit breaker e retry por operação |
| [docs/observabilidade.md](docs/observabilidade.md) | log estruturado, correlação e mascaramento |
| [docs/perfis.md](docs/perfis.md) | `local` serve HTTP, `migration` migra o schema e encerra |
| [k8s/README.md](k8s/README.md) | manifests, ConfigMap e Secret do cluster local |

## Pontos fixos

**Migrations são do Job, não da aplicação.** `spring.flyway.enabled` é `false` fora do perfil
`migration`, e `MigrationProfileGuard` recusa a subida se alguém reverter isso.

**Toda propriedade é variável de ambiente.** `k8s/payment-service.env` é a configuração efetiva;
`ConfigMapCoverageTest` falha se uma variável nova não tiver origem declarada.

**Redis é otimização.** Indisponível, cada operação de cache degrada para o caminho sem cache —
a aplicação sobe, responde e não perde requisição.
