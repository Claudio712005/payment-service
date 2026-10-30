# Contratos

Dois arquivos em [`apis/`](../apis) descrevem toda a fronteira do serviço, e **são a fonte de
verdade**: o código é gerado a partir deles, não o contrário.

| Arquivo | Descreve | Padrão |
|---|---|---|
| [`payment-service.openapi.yaml`](../apis/payment-service.openapi.yaml) | a API REST | OpenAPI 3.0.3 |
| [`payment-service.asyncapi.yaml`](../apis/payment-service.asyncapi.yaml) | eventos publicados e o comando consumido | AsyncAPI 3.0.0 |

## Por que contract first

A alternativa — anotar o código e exportar o documento — faz o contrato ser consequência da
implementação. Quem consome descobre a mudança depois que ela já foi publicada, e nada impede que
um rename de campo vire quebra de contrato sem ninguém decidir isso.

Aqui a ordem é a inversa: o YAML muda primeiro, a interface é regerada, e **o código para de
compilar** se divergir. Não é disciplina, é o compilador.

## Como o REST amarra no código

```bash
./gradlew openApiValidate   # o contrato é válido?
./gradlew openApiGenerate   # gera as interfaces de controller
./gradlew build             # generate roda antes de compilar, sempre
```

O gerador produz **só interfaces**, em `build/generated/openapi`, uma por tag:
`PagamentosApi` com `solicitarCobranca`, `listarPagamentos` e `consultarPagamento`. O controller
implementa a interface e herda caminho, verbo, parâmetros, cabeçalhos e tipo de retorno — nenhum
`@PostMapping` escrito à mão, nenhum caminho duplicado entre contrato e código.

Os **modelos não são gerados**. Cada schema do contrato é mapeado para um DTO escrito à mão, pela
tabela `esquemasEscritosAMao` em `build.gradle.kts`:

```kotlin
"SolicitarCobranca" to "com.clau.payment_service.web.dto.request.SolicitarCobrancaRequest",
"Pagamento"         to "com.clau.payment_service.web.dto.response.PagamentoResponse",
```

Isso é deliberado. Modelo gerado perderia duas coisas que este serviço tem e que o gerador não
sabe produzir:

- as **descrições como chave de mensagem** (`{api.request.meio}`), que fazem o documento servido
  responder ao `Accept-Language` em três idiomas;
- as **restrições que cruzam campos** — `@ParcelamentoCoerente` e `@CartaoCoerente` —, que recusam
  a requisição antes de qualquer chamada ao provedor.

O preço é um mapeamento a manter, e ele é verificado: `ContratoRestTest` falha se um schema do
contrato não tiver classe mapeada, se a classe mapeada não existir, ou se sobrar mapeamento sem
schema correspondente.

## Onde cada status aparece

| Status | Quando | `type` |
|---|---|---|
| `400` | campo recusado, corpo ilegível, parâmetro inválido, regra que cruza campos | `validacao`, `corpo-invalido`, `parametro-invalido` |
| `401` / `403` | token ausente ou inválido / sem o escopo da operação | `nao-autenticado`, `sem-permissao` |
| `404` | pagamento inexistente | `nao-encontrado` |
| `409` | chave de idempotência reutilizada com outro corpo | `conflito` |
| `422` | regra que depende de estado, como ordem já cobrada | `regra-de-negocio` |
| `503` | provedor saturado ou com circuito aberto | `dependencia-saturada`, `dependencia-indisponivel` |
| `500` | falha não prevista | `interno` |

A fronteira entre `400` e `422` é a que mais engana. Parcelamento sem cartão de crédito é `400`:
a incoerência está no corpo, e quem recusa é uma restrição declarativa, a mesma que recusa um CPF
inválido. `422` é para quando o corpo está impecável e o **momento** é que não serve.

## Idempotência

`POST /v1/pagamentos` exige `Idempotency-Key`. A chave identifica a intenção de cobrar, não a
tentativa de enviar — quem chama gera uma por pedido e repete a mesma em toda retentativa.

| Situação | Resposta |
|---|---|
| chave nova | `201` com `Location` |
| chave repetida, mesmo corpo | `200` com o mesmo pagamento, sem nova cobrança |
| chave repetida, corpo diferente | `409` |

A unicidade é do banco (`UK_PAGAMENTOS_IDEMPOTENCIA`), não de cache: cair entre a cobrança e a
resposta não permite cobrar duas vezes.

## Cartão em claro, e o que isso custa

A API recebe número, validade e código de segurança. **Isso põe o serviço no escopo PCI-DSS
integral** — cofre, mascaramento, auditoria e certificação deixam de ser opcionais. Foi uma
escolha declarada, não um acidente, e o contrato carrega as consequências:

- todo campo de `Cartao` é `writeOnly`, e `ContratoRestTest` falha se um deles deixar de ser;
- `numero` é mascarado no log com `@Sensitive(KEEP_LAST, keep = 4)`, `codigoDeSeguranca` por
  inteiro;
- nenhuma resposta devolve dado de cartão — nem a de erro: `Violacao.recebido` é omitido para
  campo sensível.

Tokenizar no SDK do provedor elimina o escopo inteiro. Quando houver essa opção, o caminho é
trocar `Cartao` por um token — mudança aditiva no contrato, com o campo antigo marcado como
`deprecated` antes de sair.

## Mensageria

`EnvelopeDoEvento` é o que todo evento de pagamento carrega. Evolução é **aditiva**: campo novo
nasce opcional, campo existente nunca muda de tipo nem de significado. Quebrar um evento não dá
erro HTTP — dá silêncio e dado errado, descoberto dias depois.

A propriedade do contrato é assimétrica:

| Mensagem | Dono |
|---|---|
| `pagamento.criado`, `.autorizado`, `.pago`, `.recusado`, `.expirado` | este serviço |
| comando `cobrar-pagamento` | o orquestrador que pede |

O comando aparece no arquivo como **cópia**. Mudá-lo aqui não muda nada do outro lado: o que
impede a cópia de divergir é teste de contrato, não ferramenta.

Três decisões que o arquivo registra e que valem relembrar:

1. **Publicação é pela outbox.** A transição e a linha em `EVENTOS_SAIDA` são gravadas na mesma
   transação; uma rotina publica depois. Broker fora do ar atrasa o evento, não desfaz o pagamento.
   O preço é o evento sair fora do span de origem, e é por isso que o `traceparent` é guardado na
   outbox e vai no cabeçalho.
2. **O cabeçalho é o trace canônico**, não um campo de corpo: `traceparent` em cabeçalho é o que
   o consumidor usa para entrar no mesmo trace.
3. **`expiraEm` do comando é o relógio do orquestrador.** Comando vencido é descartado com
   registro, porque cobrar depois que quem pediu já desistiu cria dinheiro órfão.

## O que os testes fixam

| Teste | O que trava |
|---|---|
| `openApiValidate` | o contrato REST é um OpenAPI válido |
| `ContratoRestTest` | referência quebrada, schema sem classe, mapeamento órfão, erro fora de `problem+json`, campo de cartão sem `writeOnly`, `Idempotency-Key` obrigatório |
| `ContratoDeMensageriaTest` | referência quebrada, canal sem mensagem, operação sem canal, canal sem versão no endereço, evento sem envelope ou sem cabeçalho padrão, comando sem `expiraEm` |
| `ComposicaoDoContratoTest` | os DTOs compõem fragmentos em vez de repeti-los, e toda descrição é chave de tradução |
| `CartaoCoerenteValidatorTest` | cartão exigido em crédito e débito, recusado em Pix e boleto |
| `BundlesDeMensagemTest` | os três idiomas têm as mesmas chaves, nenhuma sem tradução |

## Mudar o contrato

1. editar o YAML em `apis/`;
2. `./gradlew openApiValidate`;
3. ajustar o DTO mapeado, se o schema mudou de forma;
4. `./gradlew test` — os testes de contrato apontam o que ficou solto;
5. commit separado para o contrato e para a implementação, nessa ordem.

Mudança que **quebra** consumidor não entra assim: entra como campo novo opcional, ou como
caminho novo. O contrato é publicado; retirar algo dele é decisão com prazo, não refatoração.
