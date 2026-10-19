package com.clau.payment_service.model.document

import java.time.Instant
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "notificacoes_do_provedor")
class NotificacaoDoProvedor(

    @Id
    var id: String,

    @Field("provedor")
    var provedor: String,

    @Field("tipo")
    var tipo: String,

    @Field("acao")
    var acao: String? = null,

    @Indexed
    @Field("recursoId")
    var recursoId: String,

    @Field("corpo")
    var corpo: Map<String, Any?> = emptyMap(),

    @Field("cabecalhos")
    var cabecalhos: Map<String, String> = emptyMap(),

    @Field("assinaturaValida")
    var assinaturaValida: Boolean,

    @Indexed
    @Field("recebidoEm")
    var recebidoEm: Instant,

    @Field("processadoEm")
    var processadoEm: Instant? = null,

    @Field("resultado")
    var resultado: String? = null
)
