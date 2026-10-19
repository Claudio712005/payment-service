package com.clau.payment_service.model.jpa

import com.clau.payment_service.model.enums.SituacaoDoEvento
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "EVENTOS_SAIDA", schema = "PAY")
class EventoDeSaida(

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ID", nullable = false, length = 36)
    var id: UUID,

    @Column(name = "TIPO", nullable = false, length = 80)
    var tipo: String,

    @Column(name = "AGREGADO", nullable = false, length = 40)
    var agregado: String,

    @Column(name = "CHAVE_AGREGADO", nullable = false, length = 60)
    var chaveAgregado: String,

    @Column(name = "TOPICO", nullable = false, length = 120)
    var topico: String,

    @Lob
    @JdbcTypeCode(SqlTypes.CLOB)
    @Column(name = "PAYLOAD", nullable = false)
    var payload: String,

    @Lob
    @JdbcTypeCode(SqlTypes.CLOB)
    @Column(name = "CABECALHO")
    var cabecalho: String? = null,

    @Column(name = "TRACEPARENT", length = 60)
    var traceparent: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO", nullable = false, length = 20)
    var situacao: SituacaoDoEvento,

    @Column(name = "TENTATIVAS", nullable = false)
    var tentativas: Int = 0,

    @Column(name = "ULTIMO_ERRO", length = 500)
    var ultimoErro: String? = null,

    @Column(name = "CRIADO_EM", nullable = false)
    var criadoEm: OffsetDateTime,

    @Column(name = "PUBLICADO_EM")
    var publicadoEm: OffsetDateTime? = null
)
