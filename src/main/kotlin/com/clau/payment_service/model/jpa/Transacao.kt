package com.clau.payment_service.model.jpa

import com.clau.payment_service.model.enums.SituacaoDaTransacao
import com.clau.payment_service.model.enums.TipoDeTransacao
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "TRANSACOES", schema = "PAY")
class Transacao(

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ID", nullable = false, length = 36)
    var id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PAGAMENTO_ID", nullable = false)
    var pagamento: Pagamento,

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, length = 20)
    var tipo: TipoDeTransacao,

    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO", nullable = false, length = 20)
    var situacao: SituacaoDaTransacao,

    @Column(name = "VALOR", nullable = false, precision = 15, scale = 2)
    var valor: BigDecimal,

    @Column(name = "TRANSACAO_PROVEDOR_ID", length = 60)
    var transacaoProvedorId: String? = null,

    @Column(name = "CODIGO_RETORNO", length = 40)
    var codigoRetorno: String? = null,

    @Column(name = "MENSAGEM_RETORNO", length = 500)
    var mensagemRetorno: String? = null,

    @Column(name = "MOTIVO", length = 500)
    var motivo: String? = null,

    @Column(name = "PAYLOAD_ID", length = 60)
    var payloadId: String? = null,

    @Column(name = "TRACEPARENT", length = 60)
    var traceparent: String? = null,

    @Column(name = "OCORRIDO_EM")
    var ocorridoEm: OffsetDateTime? = null,

    @Column(name = "REGISTRADO_EM", nullable = false)
    var registradoEm: OffsetDateTime
)
