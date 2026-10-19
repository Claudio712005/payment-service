package com.clau.payment_service.model.jpa

import com.clau.payment_service.model.enums.SituacaoDaNotaFiscal
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "NOTAS_FISCAIS", schema = "PAY")
class NotaFiscal(

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ID", nullable = false, length = 36)
    var id: UUID,

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "PAGAMENTO_ID", nullable = false, length = 36)
    var pagamentoId: UUID,

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ORDEM_SERVICO_ID", nullable = false, length = 36)
    var ordemServicoId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO", nullable = false, length = 20)
    var situacao: SituacaoDaNotaFiscal,

    @Column(name = "SERIE", nullable = false, length = 5)
    var serie: String,

    @Column(name = "NUMERO")
    var numero: Long? = null,

    @Column(name = "CHAVE_DE_ACESSO", length = 60)
    var chaveDeAcesso: String? = null,

    @Column(name = "CODIGO_VERIFICACAO", length = 40)
    var codigoVerificacao: String? = null,

    @Column(name = "CNPJ_PRESTADOR", nullable = false, length = 14)
    var cnpjPrestador: String,

    @Column(name = "DOCUMENTO_TOMADOR", nullable = false, length = 14)
    var documentoTomador: String,

    @Column(name = "MUNICIPIO_INCIDENCIA", nullable = false, length = 7)
    var municipioIncidencia: String,

    @Column(name = "COMPETENCIA", nullable = false)
    var competencia: LocalDate,

    @Column(name = "VALOR_SERVICOS", nullable = false, precision = 15, scale = 2)
    var valorServicos: BigDecimal,

    @Column(name = "VALOR_INSUMOS", nullable = false, precision = 15, scale = 2)
    var valorInsumos: BigDecimal = BigDecimal.ZERO,

    @Column(name = "VALOR_DEDUCOES", nullable = false, precision = 15, scale = 2)
    var valorDeducoes: BigDecimal = BigDecimal.ZERO,

    @Column(name = "BASE_DE_CALCULO", nullable = false, precision = 15, scale = 2)
    var baseDeCalculo: BigDecimal,

    @Column(name = "ALIQUOTA_ISS", nullable = false, precision = 7, scale = 4)
    var aliquotaIss: BigDecimal,

    @Column(name = "VALOR_ISS", nullable = false, precision = 15, scale = 2)
    var valorIss: BigDecimal,

    @Column(name = "VALOR_TOTAL", nullable = false, precision = 15, scale = 2)
    var valorTotal: BigDecimal,

    @Column(name = "PROTOCOLO", length = 60)
    var protocolo: String? = null,

    @Column(name = "MOTIVO_REJEICAO", length = 500)
    var motivoRejeicao: String? = null,

    @Column(name = "MOTIVO_CANCELAMENTO", length = 500)
    var motivoCancelamento: String? = null,

    @Column(name = "ESPELHO_ID", length = 60)
    var espelhoId: String? = null,

    @Column(name = "EMITIDA_EM")
    var emitidaEm: OffsetDateTime? = null,

    @Column(name = "CANCELADA_EM")
    var canceladaEm: OffsetDateTime? = null,

    @Column(name = "CRIADO_EM", nullable = false)
    var criadoEm: OffsetDateTime,

    @Column(name = "ATUALIZADO_EM", nullable = false)
    var atualizadoEm: OffsetDateTime,

    @OneToMany(mappedBy = "notaFiscal", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("numeroItem ASC")
    var itens: MutableList<ItemDaNotaFiscal> = mutableListOf(),

    @Version
    @Column(name = "VERSAO", nullable = false)
    var versao: Int = 0
)
