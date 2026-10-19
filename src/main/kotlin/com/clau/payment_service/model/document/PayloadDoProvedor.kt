package com.clau.payment_service.model.document

import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "payloads_do_provedor")
class PayloadDoProvedor(

    @Id
    var id: String,

    @Indexed
    @Field("pagamentoId")
    var pagamentoId: UUID,

    @Field("transacaoId")
    var transacaoId: UUID? = null,

    @Field("provedor")
    var provedor: String,

    @Indexed
    @Field("operacao")
    var operacao: String,

    @Field("metodoHttp")
    var metodoHttp: String,

    @Field("uri")
    var uri: String,

    @Field("statusHttp")
    var statusHttp: Int? = null,

    @Field("requisicao")
    var requisicao: Map<String, Any?> = emptyMap(),

    @Field("resposta")
    var resposta: Map<String, Any?> = emptyMap(),

    @Field("cabecalhos")
    var cabecalhos: Map<String, String> = emptyMap(),

    @Field("traceparent")
    var traceparent: String? = null,

    @Field("duracaoMs")
    var duracaoMs: Long? = null,

    @Indexed
    @Field("registradoEm")
    var registradoEm: Instant,

    @Indexed(expireAfter = "0s")
    @Field("expiraEm")
    var expiraEm: Instant? = null
)
