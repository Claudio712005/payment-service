package com.clau.payment_service.model.jpa

import com.clau.payment_service.model.enums.MeioDePagamento
import com.clau.payment_service.model.enums.SituacaoDoPagamento
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "PAGAMENTOS", schema = "PAY")
class Pagamento(

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ID", nullable = false, length = 36)
    var id: UUID,

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ORDEM_SERVICO_ID", nullable = false, length = 36)
    var ordemServicoId: UUID,

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "CLIENTE_ID", nullable = false, length = 36)
    var clienteId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "MEIO", nullable = false, length = 20)
    var meio: MeioDePagamento,

    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO", nullable = false, length = 20)
    var situacao: SituacaoDoPagamento,

    @Column(name = "MOEDA", nullable = false, length = 3)
    var moeda: String = "BRL",

    @Column(name = "VALOR_TOTAL", nullable = false, precision = 15, scale = 2)
    var valorTotal: BigDecimal,

    @Column(name = "VALOR_PAGO", nullable = false, precision = 15, scale = 2)
    var valorPago: BigDecimal = BigDecimal.ZERO,

    @Column(name = "VALOR_ESTORNADO", nullable = false, precision = 15, scale = 2)
    var valorEstornado: BigDecimal = BigDecimal.ZERO,

    @Column(name = "QUANTIDADE_PARCELAS", nullable = false)
    var quantidadeParcelas: Int = 1,

    @Column(name = "CHAVE_IDEMPOTENCIA", nullable = false, length = 80)
    var chaveIdempotencia: String,

    @Column(name = "PROVEDOR", nullable = false, length = 30)
    var provedor: String,

    @Column(name = "PAGAMENTO_PROVEDOR_ID", length = 60)
    var pagamentoProvedorId: String? = null,

    @Column(name = "EXPIRA_EM")
    var expiraEm: OffsetDateTime? = null,

    @Column(name = "CONFIRMADO_EM")
    var confirmadoEm: OffsetDateTime? = null,

    @Column(name = "MOTIVO_RECUSA", length = 500)
    var motivoRecusa: String? = null,

    @Column(name = "CRIADO_EM", nullable = false)
    var criadoEm: OffsetDateTime,

    @Column(name = "ATUALIZADO_EM", nullable = false)
    var atualizadoEm: OffsetDateTime,

    @OneToMany(mappedBy = "pagamento", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("numero ASC")
    var parcelas: MutableList<Parcela> = mutableListOf(),

    @OneToMany(mappedBy = "pagamento", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("registradoEm ASC")
    var transacoes: MutableList<Transacao> = mutableListOf(),

    @Version
    @Column(name = "VERSAO", nullable = false)
    var versao: Int = 0
)
