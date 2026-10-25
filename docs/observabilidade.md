# Observabilidade — logs

Log estruturado em JSON, rastreável pelos quatro identificadores, com mascaramento declarado
por anotação no campo. Configuração em `src/main/resources/logback-spring.xml` e no pacote
`observability`.

## Envelope JSON

```json
{
  "@timestamp": "2026-10-24T22:44:19.478988Z",
  "level": "INFO",
  "logger": "com.clau.payment_service.Cobranca",
  "thread": "http-nio-8080-exec-1",
  "application": "payment-service",
  "environment": "local",
  "message": "cobranca registrada Cobranca(id=e1f0, cartao=**** **** **** 1234, cvv=***)",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "requestId": "req-1",
  "correlationId": "corr-1",
  "context": { "pagamentoId": "e1f0" },
  "tags": ["negocio"],
  "error": { "type": "...", "message": "...", "stackTrace": "..." }
}
```

Identificador ausente é **omitido**, nunca emitido vazio. O que sobra do MDC e os key-value
pairs do SLF4J vão aninhados em `context` — fora dele só entra o que o envelope declara, então
chave de MDC não colide com campo do envelope.

## Os quatro identificadores

| Campo | Origem |
|---|---|
| `traceId` | MDC, preenchido pelo Micrometer Tracing (`micrometer-tracing-bridge-otel`) |
| `spanId` | idem |
| `requestId` | `CorrelationFilter`: cabeçalho `X-Request-Id`, ou UUID gerado |
| `correlationId` | `CorrelationFilter`: cabeçalho `X-Correlation-Id`, ou o próprio `requestId` |

O filtro roda em `Ordered.HIGHEST_PRECEDENCE`, devolve os dois cabeçalhos na resposta e limpa
o MDC no `finally` — inclusive quando a cadeia estoura. Valor recebido por cabeçalho é saneado
por `IdentifierSanitizer`: só `[A-Za-z0-9-_.:]` sobrevive, cortado em `maxLength`. Isso fecha
injeção de quebra de linha no log.

`traceId` e `spanId` só aparecem com tracing ativo. Sem tracing, `requestId` e `correlationId`
continuam funcionando — correlação por requisição não depende de OpenTelemetry.

## Mascaramento

Declarado no campo, não inferido do texto:

```kotlin
data class CobrancaRequest(
    val ordemServicoId: UUID,
    val valor: BigDecimal,
    @Sensitive(mode = Sensitive.Mode.KEEP_LAST, keep = 4) val cartao: String,
    @Sensitive val cvv: String?,
    @Sensitive(mode = Sensitive.Mode.HASH) val email: String
)
```

```kotlin
log.info("cobranca solicitada {}", request.loggable())
```

| Modo | Resultado |
|---|---|
| `FULL` (default) | `***` |
| `KEEP_LAST` + `keep` | `**** **** **** 1234` — conta só alfanuméricos, separador fica legível |
| `HASH` | `sha256:a1b2c3d4e5f6` — estável, correlaciona sem expor |

Quatro arquivos, em `observability/logging/sensitive`:

| Arquivo | Papel |
|---|---|
| `Sensitive` | a anotação e os três modos |
| `SensitiveMasker` | como substituir o valor |
| `LogRenderer` | percorre as propriedades públicas por reflexão e aplica a anotação |
| `Loggable` | envelope com `toString()` tardio, mais a extensão `loggable()` |

`loggable()` só renderiza quando o SLF4J converte o argumento em texto. Nível desligado, nada
de reflexão: custo zero.

`LogRenderer` desce em objeto aninhado, `Iterable` e `Map`, com teto de profundidade 4 e
ciclo cortado por aí. Tipo de `java.*`, `kotlin.*` e afins usa o `toString()` próprio, sem
reflexão. Campo anotado com valor nulo sai `null`, não `***` — mascarar ausência inventa
informação.

### O que a anotação não alcança

Mascaramento por anotação cobre o que passa pelo objeto. **Não** cobre:

- interpolação direta: `log.info("cpf $cpf")` — a string chega pronta no logger
- mensagem de exceção que carrega valor
- log de biblioteca: SQL com parâmetro do Hibernate, dump de request do cliente HTTP

A escolha é deliberada: o alternativo é varrer toda linha de log com uma bateria de regex, que
custa por linha, erra nos dois sentidos (UUID de pagamento mascarado por parecer dado pessoal,
CNPJ engolido pelo padrão de cartão) e cria dependência de ordem entre regras. Se a rede vier a
ser necessária, o lugar dela é um appender ou um filtro de encoder, não um pacote.

Enquanto isso, a disciplina é: **logar objeto, não string interpolada.**

## Propriedades

Saída e appenders:

| Propriedade | Default | Para quê |
|---|---|---|
| `observability.logging.console-appender` | `CONSOLE_JSON` | `CONSOLE_JSON` ou `CONSOLE_PLAIN` |
| `observability.logging.root-appender` | `ASYNC` | appender da raiz; aponte direto num console para desligar o assíncrono |
| `observability.logging.root-level` | `INFO` | nível da raiz |
| `observability.logging.charset` | `UTF-8` | |
| `observability.logging.plain-pattern` | ver XML | padrão do modo texto |
| `observability.logging.async.queue-size` | `8192` | |
| `observability.logging.async.never-block` | `true` | descarta em vez de travar o request |
| `observability.logging.async.discarding-threshold` | `0` | `0` mantém todos os níveis na fila |

JSON assíncrono é o default. Texto assíncrono exige `console-appender=CONSOLE_PLAIN`. Síncrono
exige `root-appender` apontando para o console escolhido — os nomes de appender são explícitos
porque logback só faz condicional com janino, e não vale uma dependência a mais.

Correlação:

| Propriedade | Default |
|---|---|
| `observability.correlation.request-header` | `X-Request-Id` |
| `observability.correlation.correlation-header` | `X-Correlation-Id` |
| `observability.correlation.echo-response-headers` | `true` |
| `observability.correlation.max-length` | `64` |

## Campos extra, renomear e excluir

`StructuredJsonLogFormatter` estende `JsonWriterStructuredLogFormatter` e repassa o
`StructuredLoggingJsonMembersCustomizer` do Boot, então as propriedades nativas valem:

```yaml
logging:
  structured:
    json:
      add:
        servico: pagamentos
      rename:
        logger: logger_name
      exclude:
        - thread
```

## Testes

| Arquivo | Garante |
|---|---|
| `LogRendererTest` | cada modo, objeto aninhado, coleção, nulo anotado, renderização tardia |
| `StructuredJsonLogFormatterTest` | envelope, promoção dos quatro ids, `context` aninhado, erro aninhado |
| `CorrelationFilterTest` | geração e reaproveitamento de id, saneamento, limpeza do MDC com e sem exceção |
| `LogbackConfigurationTest` | sobe o `logback-spring.xml` de verdade nos três modos de saída |

`LogbackConfigurationTest` reconfigura o sistema de log do processo e restaura no `@AfterTest`.
Boot ignora um `initialize` num contexto já inicializado, então o teste chama `cleanUp()` antes
de cada cenário — sem isso ele passaria medindo a configuração do cenário anterior.
