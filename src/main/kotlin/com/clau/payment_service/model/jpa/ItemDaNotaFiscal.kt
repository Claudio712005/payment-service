package com.clau.payment_service.model.jpa

import com.clau.payment_service.model.enums.TipoDeItemFiscal
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
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "ITENS_NOTA_FISCAL", schema = "PAY")
class ItemDaNotaFiscal(

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ID", nullable = false, length = 36)
    var id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "NOTA_FISCAL_ID", nullable = false)
    var notaFiscal: NotaFiscal,

    @Column(name = "NUMERO_ITEM", nullable = false)
    var numeroItem: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, length = 20)
    var tipo: TipoDeItemFiscal,

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "REFERENCIA_ID", nullable = false, length = 36)
    var referenciaId: UUID,

    @Column(name = "DESCRICAO", nullable = false, length = 200)
    var descricao: String,

    @Column(name = "CODIGO_TRIBUTACAO_MUNICIPAL", length = 20)
    var codigoTributacaoMunicipal: String? = null,

    @Column(name = "QUANTIDADE", nullable = false, precision = 12, scale = 3)
    var quantidade: BigDecimal,

    @Column(name = "VALOR_UNITARIO", nullable = false, precision = 15, scale = 2)
    var valorUnitario: BigDecimal,

    @Column(name = "VALOR_TOTAL", nullable = false, precision = 15, scale = 2)
    var valorTotal: BigDecimal,

    @Column(name = "ALIQUOTA_ISS", nullable = false, precision = 7, scale = 4)
    var aliquotaIss: BigDecimal,

    @Column(name = "VALOR_ISS", nullable = false, precision = 15, scale = 2)
    var valorIss: BigDecimal
)
