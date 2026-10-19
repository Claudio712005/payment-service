package com.clau.payment_service.model.document

import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "historico_do_pagamento")
@CompoundIndex(name = "ix_pagamento_ocorrido", def = "{'pagamentoId': 1, 'ocorridoEm': -1}")
class HistoricoDoPagamento(

    @Id
    var id: String,

    @Field("pagamentoId")
    var pagamentoId: UUID,

    @Indexed
    @Field("ordemServicoId")
    var ordemServicoId: UUID? = null,

    @Field("evento")
    var evento: String,

    @Field("situacaoAnterior")
    var situacaoAnterior: String? = null,

    @Field("situacaoAtual")
    var situacaoAtual: String? = null,

    @Field("ator")
    var ator: String,

    @Field("detalhes")
    var detalhes: Map<String, Any?> = emptyMap(),

    @Field("traceparent")
    var traceparent: String? = null,

    @Field("ocorridoEm")
    var ocorridoEm: Instant
)
