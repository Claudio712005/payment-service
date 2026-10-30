# Documentação da API

Quatro decisões sustentam esta API: o documento e as mensagens são internacionalizados, os
contratos são compostos de fragmentos, a validação de formato vive em restrições declarativas, e
todo erro sai no mesmo formato.

OpenAPI em `/v3/api-docs`, Swagger UI em `/swagger-ui.html`.

**A fonte de verdade da API é `apis/payment-service.openapi.yaml`**, não estas anotações — ver
[contratos.md](contratos.md). O que vive no código é o que o gerador não sabe produzir: descrição
como chave de tradução e restrição que cruza campos.

## 1. Internacionalização

Três idiomas: `pt-BR` (padrão), `en`, `es`. O idioma vem do cabeçalho `Accept-Language`;
idioma não suportado cai no padrão em vez de falhar.

| Componente | Papel |
|---|---|
| `messages/messages_{pt_BR,en,es}.properties` | os três bundles, com as **mesmas chaves** |
| `MessageSourceConfig` | `MessageSource`, `AcceptHeaderLocaleResolver` e o `Validator` |
| `LocaleContextMessageInterpolator` | faz a mensagem de Bean Validation sair no idioma da requisição |
| `DocumentoTraduzidoCustomizer` | traduz o **próprio documento OpenAPI** |

O que é internacionalizado:

- título, descrição e tags do documento OpenAPI
- resumo e descrição de cada operação e parâmetro
- descrição de cada schema e de cada propriedade, inclusive dentro de `allOf`
- mensagem de cada violação de validação
- `title` e `detail` de toda resposta de erro

`springdoc.cache.disabled` é `true` para que o documento seja re-renderizado por requisição —
sem isso o primeiro idioma pedido ficaria em cache para todos.

Os `@Schema(description = ...)` não guardam texto: guardam **chave**, no formato `{api.algo}`.
`ComposicaoDoContratoTest` falha se alguma descrição for texto literal, e
`BundlesDeMensagemTest` falha se as chaves divergirem entre idiomas, se uma mensagem ficar
vazia, se uma tradução repetir o português, ou se os marcadores de argumento (`{0}`) não
casarem entre os três arquivos.

## 2. Contratos por composição

Fragmentos reutilizáveis, em `web/dto/fragmento`:

| Fragmento | Reusado por |
|---|---|
| `Documento` | `Pagador` |
| `Contato` | `Pagador` |
| `Valor` | `SolicitarCobranca`, `Pagamento`, `Parcela` |
| `Pagador` | `SolicitarCobranca`, `Pagamento` |

No OpenAPI isso vira `$ref` — o schema de `Valor` existe uma vez e é apontado de três lugares.
Mudar a casa decimal de `Valor` muda os três sem edição em cascata.

### Composição polimórfica

A instrução de pagamento depende do meio, e cada meio tem campos próprios. Em vez de um objeto
com tudo nulável, `InstrucaoDePagamento` é uma `sealed interface` com discriminador:

```
InstrucaoDePagamento          oneOf + discriminator: meio
├── InstrucaoPix              allOf [$ref pai, { copiaECola, qrCodeBase64, expiraEm }]
├── InstrucaoBoleto           allOf [$ref pai, { linhaDigitavel, urlDoBoleto, vencimento }]
└── InstrucaoCartao           allOf [$ref pai, { codigoDeAutorizacao, bandeira, parcelas }]
```

O cliente lê `meio` e sabe qual variante esperar. Gerador de cliente produz tipos separados em
vez de um objeto com nove campos opcionais. O discriminador vive **só** no pai; `@JsonTypeInfo`
com `EXISTING_PROPERTY` reaproveita o campo `meio` em vez de inventar um `@type`.

## 3. Validadores

Restrições de Bean Validation, aplicadas antes de qualquer regra de negócio. As do próprio
jakarta cobrem obrigatoriedade, tamanho, faixa, e-mail e formato. Dois casos exigiram validador
próprio.

### `@Documento` — CPF ou CNPJ

Valida **dígito verificador**, não formato. Aceita com ou sem pontuação, recusa documento com
todos os dígitos iguais, e recusa quando a quantidade de dígitos não é 11 nem 14.

```kotlin
@field:Documento val numero: String?
@field:Documento(tipos = [TipoDeDocumento.CNPJ]) val cnpjPrestador: String?
```

Nulo é aceito de propósito: obrigatoriedade é `@NotNull`, não é tarefa deste validador. Misturar
as duas coisas tira do cliente a informação de *qual* das duas falhou.

Um detalhe que ficou fixado em teste: `11.111.111/1111-80` é **aceito**. O radical é repetido,
mas o dígito verificador é válido — a regra é recusar quando todos os dígitos são iguais, não
quando o radical é.

### `@ParcelamentoCoerente` — restrição entre campos

Parcelamento acima de uma parcela só vale para cartão de crédito. É restrição de **classe**,
porque depende de dois campos, mas a violação é reportada no campo `parcelas`, não na raiz do
objeto — o cliente precisa saber onde corrigir.

O contrato implementa a interface `Parcelavel`, então o validador não conhece
`SolicitarCobrancaRequest`: qualquer contrato futuro com meio e parcelas reaproveita a restrição.

## 4. Erros

Toda resposta de erro é `application/problem+json`, no formato RFC 9457, com dois acréscimos:
`ocorridoEm` e `correlationId` — o mesmo identificador que aparece no log, para ligar o erro que
o cliente viu à linha de log que o explica.

```json
{
  "type": "https://payment-service/erros/validacao",
  "title": "Requisição inválida",
  "status": 400,
  "detail": "A requisição tem 4 campo(s) inválido(s)",
  "instance": "/pagamentos",
  "ocorridoEm": "2026-10-26T00:51:50.602184Z",
  "correlationId": "corr-1",
  "violacoes": [
    { "campo": "pagador.documento.numero", "mensagem": "Número de documento inválido", "recebido": "529.982.247-26" },
    { "campo": "parcelas", "mensagem": "Parcelamento acima de uma parcela é permitido apenas para cartão de crédito" }
  ]
}
```

| Exceção | Status | `type` |
|---|---|---|
| `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException` | 400 | `validacao` |
| `HttpMessageNotReadableException` | 400 | `corpo-invalido` |
| `MethodArgumentTypeMismatchException` | 400 | `parametro-invalido` |
| `HttpRequestMethodNotSupportedException` | 405 | `metodo-nao-suportado` |
| `RecursoNaoEncontradoException` | 404 | `nao-encontrado` |
| `ConflitoDeEstadoException` | 409 | `conflito` |
| `RegraDeNegocioException` | 422 | `regra-de-negocio` |
| `BulkheadFullException` | 503 | `dependencia-saturada`, com `Retry-After: 1` |
| `CallNotPermittedException` | 503 | `dependencia-indisponivel`, com `Retry-After: 30` |
| qualquer outra | 500 | `interno` |

Três decisões por trás da tabela:

**422, não 400, para regra de negócio.** O corpo estava sintaticamente correto e passou na
validação; o que falhou foi uma regra de estado. Misturar os dois impede o cliente de saber se
vale corrigir o payload ou não.

**503 com `Retry-After` para falha de resiliência.** Bulkhead cheio e circuito aberto são
condições temporárias e a janela é conhecida — ver [resiliencia.md](resiliencia.md). Devolver 500
aqui mandaria o cliente desistir de algo que vai funcionar em segundos.

**500 nunca vaza detalhe interno.** O `detail` é uma mensagem fixa traduzida e aponta o
`correlationId`; a exceção real vai para o log. Há teste que falha se a mensagem da exceção
aparecer no corpo.

As violações saem ordenadas por campo — resposta estável facilita teste de cliente e diff de
contrato.

## Como estender

| Precisa de | Faça |
|---|---|
| campo novo num contrato | se o conceito repete, crie fragmento em `web/dto/fragmento` e aponte por `$ref` |
| validação de formato nova | restrição do jakarta; validador próprio só quando há dígito verificador ou dependência entre campos |
| erro de negócio novo | lance `RegraDeNegocioException` com a chave de mensagem; adicione a chave nos três bundles |
| meio de pagamento novo | acrescente a variante de `InstrucaoDePagamento` com `@JsonSubTypes` e `oneOf` |
| idioma novo | crie `messages_<idioma>.properties` e acrescente o `Locale` em `SupportedLocales.ALL` |

Em todos os casos, `BundlesDeMensagemTest` cobra paridade entre idiomas e
`ComposicaoDoContratoTest` cobra que nenhuma descrição fique como texto literal.

## Limite conhecido

O documento OpenAPI ainda não tem `paths`: não existe controller em produção, porque os casos de
uso não existem. `components.schemas` é populado por `contratosDeclaradosCustomizer`, que declara
os contratos explicitamente — então o contrato é publicável e versionável **antes** do endpoint.
Quando o controller chegar, o springdoc reaproveita os mesmos `$ref`.

A camada está provada de ponta a ponta: `RestExceptionHandlerTest` sobe uma fatia web real, com
um controller de teste, e verifica validação, tradução e mapeamento de erro nos três idiomas.
