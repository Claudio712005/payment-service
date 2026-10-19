package com.clau.payment_service.model.document

import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "espelhos_de_nota_fiscal")
class EspelhoDaNotaFiscal(

    @Id
    var id: String,

    @Indexed(unique = true)
    @Field("notaFiscalId")
    var notaFiscalId: UUID,

    @Field("serie")
    var serie: String,

    @Field("numero")
    var numero: Long? = null,

    @Field("municipio")
    var municipio: String,

    @Field("xmlAutorizado")
    var xmlAutorizado: String? = null,

    @Field("urlPdf")
    var urlPdf: String? = null,

    @Field("tributos")
    var tributos: Map<String, Any?> = emptyMap(),

    @Field("itens")
    var itens: List<Map<String, Any?>> = emptyList(),

    @Field("rejeicoes")
    var rejeicoes: List<Map<String, Any?>> = emptyList(),

    @Field("retornoDoProvedor")
    var retornoDoProvedor: Map<String, Any?> = emptyMap(),

    @Indexed
    @Field("geradoEm")
    var geradoEm: Instant
)
