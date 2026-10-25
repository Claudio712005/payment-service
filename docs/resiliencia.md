# Resiliência

Bulkhead, circuit breaker e retry em **instâncias nomeadas**, não numa configuração global.
Quem nomeia são dois enums; o `application.yaml` referencia esses enums por chave.

## Os dois enums

`ResilienceDomain` é a dependência externa. **O bulkhead é por domínio**, porque o recurso que
se esgota é a conexão com aquele provedor — um pool compartilhado deixaria a prefeitura
derrubar a cobrança.

| Domínio | Dependência |
|---|---|
| `PAYMENT_PROVIDER` | provedor de pagamento |
| `INVOICE_AUTHORITY` | autoridade fiscal da NFS-e |

`ResilienceOperation` é a chamada. **Circuito e retry são por operação**, porque consulta e
emissão falham de formas diferentes e não devem se contaminar. Cada constante declara o
domínio e se a operação é **idempotente**:

| Operação | Domínio | Idempotente | Retry |
|---|---|---|---|
| `CHARGE_CREATE` | `PAYMENT_PROVIDER` | não | **não** |
| `CHARGE_QUERY` | `PAYMENT_PROVIDER` | sim | sim |
| `CHARGE_CANCEL` | `PAYMENT_PROVIDER` | sim | sim |
| `CHARGE_REFUND` | `PAYMENT_PROVIDER` | sim | sim |
| `INVOICE_ISSUE` | `INVOICE_AUTHORITY` | não | **não** |
| `INVOICE_QUERY` | `INVOICE_AUTHORITY` | sim | sim |
| `INVOICE_CANCEL` | `INVOICE_AUTHORITY` | sim | sim |

Retry **onde se justifica** quer dizer: onde repetir não duplica efeito. Criar cobrança não
tem retry — um timeout de leitura sobre uma cobrança que o provedor já aceitou, repetido, cobra
o cliente duas vezes. Emitir nota não tem retry — a emissão consome número fiscal sequencial.

Isso não é só default: habilitar retry numa operação não idempotente **falha na subida**, com a
chave do `application.yaml` nomeada no erro. Para permitir de verdade, a operação precisa ser
declarada idempotente em `ResilienceOperation`, que é mudança de código com revisor.

## O que abre o circuito

Só `429` e `5xx`. `400`, `404` e `422` são resposta de negócio: o provedor está vivo e
respondendo, e derrubar o circuito por causa deles transforma erro de dado em indisponibilidade.

Declarado por enum, não por número solto:

```yaml
resilience:
  circuit-breakers:
    charge-create:
      recorded-failures: [TOO_MANY_REQUESTS, SERVER_ERROR]
```

`HttpFailureClass` tem `TOO_MANY_REQUESTS` (429), `SERVER_ERROR` (500–599) e `CLIENT_ERROR`
(400–428), esta última disponível mas fora do default.

O status é extraído de `HttpStatusCodeException` do Spring, ou de qualquer exceção que
implemente `HttpStatusAware`. A cadeia de causas é percorrida até cinco níveis, então status
embrulhado em exceção de aplicação continua sendo encontrado.

### Exceção fora do predicado é ignorada, não contada como sucesso

`recordException` sozinho **não** basta. Com apenas ele, um `422` entra na janela deslizante
como **chamada bem-sucedida** — medido em teste: 20 chamadas com `422` deixaram quatro
registros na janela. O efeito é diluir a taxa de falha, e uma rajada de erro de negócio
mascarar 5xx real.

Por isso a configuração também declara `ignoreException { !recorder.test(it) }`: o que não é
falha registrada não entra na janela de forma alguma. `ResilienceConfigTest` fixa isso
verificando que a janela fica com zero chamadas depois de 20 respostas `422`.

### Desvio declarado: falha de conexão

A instrução era "apenas 429 e 5xx". Timeout e conexão recusada não têm status HTTP, e pela
regra literal um provedor **morto** nunca abriria o circuito — que é justamente o caso em que o
circuito serve para algo.

Então `record-connection-failures` existe e vem `true`: `ResourceAccessException`, `IOException`
e `TimeoutException` contam como falha. Para voltar ao literal:

```yaml
resilience:
  circuit-breakers:
    charge-query:
      record-connection-failures: false
```

## Back-off exponencial

`IntervalFunction.ofExponentialRandomBackoff`, com `jitter` para não sincronizar as réplicas
num mesmo instante de retentativa.

| Operação | Tentativas | Inicial | Multiplicador | Teto | Jitter |
|---|---|---|---|---|---|
| `charge-query` | 4 | 200ms | 2.0 | 2s | 0.3 |
| `charge-refund` | 3 | 500ms | 2.0 | 4s | 0.3 |
| `charge-cancel` | 3 | 500ms | 2.0 | 4s | 0.3 |
| `invoice-query` | 5 | 1s | 2.0 | 20s | 0.5 |

`invoice-query` é mais paciente porque prefeitura processa em lote e demora. `charge-query` é
curto porque está no caminho de uma requisição de usuário.

O retry usa **o mesmo predicado** do circuito: repetir um `422` é desperdício, a resposta não
vai mudar. E `failAfterMaxAttempts(false)` mantém a exceção original na última tentativa, em
vez de trocá-la por `MaxRetriesExceededException` — quem chamou precisa do status.

## Bulkhead por domínio

```yaml
resilience:
  bulkheads:
    payment-provider:
      max-concurrent-calls: 16
      max-wait: 50ms
    invoice-authority:
      max-concurrent-calls: 6
      max-wait: 0ms
```

`max-wait: 0ms` falha rápido com `BulkheadFullException` em vez de enfileirar thread. O
provedor de pagamento tolera uma espera curta porque está no caminho síncrono; a autoridade
fiscal não, porque a emissão é assíncrona e pode ser retentada depois.

Dois testes fixam o isolamento: duas operações do mesmo domínio disputam o mesmo pool, e um
domínio saturado não impede o outro de chamar.

## Ordem de composição

```
Retry( CircuitBreaker( Bulkhead( chamada ) ) )
```

Retry por fora, bulkhead por dentro — a mesma ordem que o Resilience4j aplica por anotação.
Cada tentativa passa pelo circuito e adquire sua própria permissão de bulkhead, então uma
rajada de retentativas não fura o limite de concorrência e tentativa sobre circuito aberto
falha de imediato com `CallNotPermittedException`.

## Uso

```kotlin
class MercadoPagoAdapter(
    private val resilience: ResilienceExecutor,
    private val client: RestClient
) : PagamentoGatewayPort {

    override fun consultar(id: String): PagamentoResponse =
        resilience.execute(ResilienceOperation.CHARGE_QUERY) {
            client.get().uri("/v1/payments/{id}", id).retrieve().body(PagamentoResponse::class.java)!!
        }
}
```

Nenhuma anotação, nenhum proxy, nenhum aspecto: `ResilienceExecutor` é um bean e a operação é
escolhida pelo enum na chamada. Isso mantém a decisão visível no ponto de uso e dispensa o
starter do Resilience4j, que é construído para o Boot 3.

## Métricas e log

Com um `MeterRegistry` no contexto, as três famílias de métrica do Resilience4j são ligadas por
`TaggedBulkheadMetrics`, `TaggedCircuitBreakerMetrics` e `TaggedRetryMetrics` — a instância vai
como tag, então dá para olhar `charge-query` separado de `invoice-issue`.

Transição de estado do circuito gera log em `WARN` com o nome da operação e os dois estados.

## Propriedades

| Prefixo | Chave | Conteúdo |
|---|---|---|
| `resilience.bulkheads` | `ResilienceDomain` em kebab-case | `max-concurrent-calls`, `max-wait` |
| `resilience.circuit-breakers` | `ResilienceOperation` em kebab-case | janela, limiares, espera, `recorded-failures`, `record-connection-failures` |
| `resilience.retries` | `ResilienceOperation` em kebab-case | `enabled`, `max-attempts`, `initial-backoff`, `multiplier`, `max-backoff`, `jitter` |
| `resilience.default-bulkhead` | — | usado por domínio sem entrada |
| `resilience.default-circuit-breaker` | — | usado por operação sem entrada |
| `resilience.default-retry` | — | usado por operação sem entrada |

`sliding-window` aceita `COUNT_BASED` ou `TIME_BASED`, também por enum. Domínio ou operação sem
entrada no yaml cai no default correspondente, então acrescentar uma constante no enum não
exige tocar na configuração.
