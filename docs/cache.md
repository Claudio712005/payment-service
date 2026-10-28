# Cache

Redis por Jedis, declarado como **otimização**. Redis fora do ar não derruba a aplicação, não
falha requisição e não impede a subida: cada operação de cache degrada para o caminho sem cache.

## Jedis, não Lettuce

`spring-boot-starter-data-redis` traz Lettuce por padrão. O starter entra com
`exclude(io.lettuce:lettuce-core)` e `redis.clients:jedis` ao lado, e `spring.data.redis.client-type`
é `jedis` no `application.yaml`. Com Lettuce no classpath o Boot o escolheria primeiro — a
exclusão é o que torna a escolha determinística em vez de uma questão de ordem de `@Import`.

A `JedisConnectionFactory` é a do Boot, configurada por `spring.data.redis.*`. Não há fábrica
escrita à mão: duplicar `JedisConnectionConfiguration` significaria reimplementar SSL, sentinela
e URL com menos cuidado. O que o serviço acrescenta são os valores:

| Variável | Padrão | Por quê |
|---|---|---|
| `REDIS_CONNECT_TIMEOUT` | `200ms` | cache indisponível precisa falhar rápido; esperar é pior que não cachear |
| `REDIS_TIMEOUT` | `300ms` | idem para leitura |
| `REDIS_POOL_MAX_WAIT` | `100ms` | pool esgotado não enfileira requisição |
| `REDIS_POOL_MIN_IDLE` | `0` | nenhuma conexão é aberta na subida — pod sobe com Redis fora |
| `REDIS_POOL_MAX_ACTIVE` | `16` | teto de conexões por réplica |

`REDIS_USERNAME` e `REDIS_PASSWORD` vêm do Secret, vazias no Redis local e preenchidas onde há
ACL. Nunca no ConfigMap — `ConfigMapCoverageTest` falha se aparecerem lá.

## Três camadas de tolerância

O Spring Cache sozinho **não** garante o que se quer aqui: `SimpleCacheErrorHandler`, o padrão,
relança a exceção, e no caminho `@Cacheable(sync = true)` o `CacheInterceptor` nem consulta o
error handler. Por isso a tolerância está no caminho da chamada, não só na borda.

| Camada | Classe | Papel |
|---|---|---|
| decorador do cache | `ResilientCache` | captura a falha, degrada a operação e alimenta o circuito |
| circuito | `CacheCircuit` | depois de N falhas, para de chamar o Redis por um intervalo |
| borda do aspecto | `TolerantCacheErrorHandler` | rede de segurança para o que escapar do decorador |

### O que cada operação faz quando o Redis falha

| Operação | Degradação |
|---|---|
| `get` | ausência de valor — o método anotado executa |
| `get` com carregador | o carregador roda e o valor volta sem passar pelo Redis |
| `put`, `putIfAbsent` | nada é gravado |
| `evict`, `clear`, `invalidate` | nada é removido |

A degradação de `evict` é a única com consequência de dado: a entrada velha permanece até o TTL
expirar. Com o Redis inacessível não há alternativa — remover exige o mesmo servidor que caiu —
e é por isso que **todo cache tem TTL**, nunca entrada eterna.

Erro do carregador **não** é degradado: sobe como `Cache.ValueRetrievalException`. Falha de
negócio não é falha de cache. E se o Redis cair depois do carregador rodar, o valor já carregado
é devolvido em vez de recarregado: `ResilientCache` conta a invocação para não executar duas
vezes o trabalho caro que o `sync = true` existe para evitar.

### Por que um circuito e não só o error handler

Sem circuito, com o Redis fora, **toda** operação paga o timeout de conexão antes de degradar —
o cache passa a somar latência em vez de economizar. Depois de `PAYMENT_CACHE_CIRCUIT_FAILURES`
falhas o circuito abre por `PAYMENT_CACHE_CIRCUIT_COOLDOWN` e as chamadas desviam do Redis sem
tentar. Vencida a pausa, a próxima chamada sonda; sucesso zera o contador.

O estado vai para o Micrometer como `cache.circuit.open` (`1` aberto, `0` fechado), ao lado das
métricas do Resilience4j.

## Saúde e sondas

`management.health.redis.enabled` é `false` no perfil `local`. O indicador do Boot entra no
grupo `readiness`, e um Redis fora tiraria o pod do balanceador por causa de um componente
**opcional** — exatamente o acoplamento que este desenho recusa. A visibilidade vem da métrica
do circuito e do log de degradação, não de uma sonda que mata o pod.

## Chaves, TTL e serialização

Chave: `<prefixo>:<nome do cache>::<chave>`, com `PAYMENT_CACHE_KEY_PREFIX` na frente para que
dois ambientes possam dividir instância sem colidir.

TTL padrão em `PAYMENT_CACHE_DEFAULT_TTL`. TTL por cache em `cache.ttls.<nome>`, e os caches
declarados ali existem desde a subida, com a configuração própria.

Valor em JSON por `GenericJacksonJsonRedisSerializer` (Jackson 3, o mesmo da API), com módulo
Kotlin e tipagem polimórfica restrita a `com.clau.payment_service`, `java.util`, `java.time` e
`java.math`. JSON em vez da serialização JDK porque o conteúdo do cache precisa ser legível por
`redis-cli` quando algo estiver errado, e a lista de pacotes existe para que um valor plantado
no Redis não vire instanciação de classe arbitrária.

`PAYMENT_CACHE_NULL_VALUES` é `true`: ausência também é resposta, e cachear `null` evita que
chave inexistente vire consulta repetida ao provedor.

## Desligar

`PAYMENT_CACHE_ENABLED=false` troca o `CacheManager` por `NoOpCacheManager`. As anotações
continuam no lugar, nada é guardado, nada falha. É o interruptor para isolar o cache de uma
investigação sem alterar código nem manifest.

## O que os testes fixam

| Teste | O que trava |
|---|---|
| `CacheCircuitTest` | limite de falhas, pausa, reabertura e reset por sucesso |
| `ResilientCacheTest` | degradação de cada operação, carregador executado uma vez, erro de negócio preservado |
| `CacheConfigTest` | contexto sobe com Redis inalcançável, método `@Cacheable` responde, circuito abre, `NoOpCacheManager` quando desligado |
| `ConfigurationVariablesTest` | cliente `jedis`, defaults do cache e sobreposição por variável |
| `ConfigMapCoverageTest` | toda variável nova tem origem no ConfigMap ou no Secret |

O `CacheConfigTest` aponta para uma porta sem Redis de propósito: é a prova de que a ausência do
cache é um caminho exercitado, não uma esperança.
