package com.clau.payment_service.model.jpa

import com.clau.payment_service.model.enums.OrigemDaMensagem
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "MENSAGENS_PROCESSADAS", schema = "PAY")
class MensagemProcessada(

    @Id
    @Column(name = "ID_MENSAGEM", nullable = false, length = 160)
    var idMensagem: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "ORIGEM", nullable = false, length = 20)
    var origem: OrigemDaMensagem,

    @Column(name = "CANAL", nullable = false, length = 120)
    var canal: String,

    @Column(name = "ETAPA", length = 40)
    var etapa: String? = null,

    @Column(name = "RESULTADO", nullable = false, length = 20)
    var resultado: String,

    @Column(name = "TRACEPARENT", length = 60)
    var traceparent: String? = null,

    @Column(name = "PROCESSADO_EM", nullable = false)
    var processadoEm: OffsetDateTime
)
