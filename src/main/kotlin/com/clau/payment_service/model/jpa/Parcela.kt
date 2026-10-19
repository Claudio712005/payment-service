package com.clau.payment_service.model.jpa

import com.clau.payment_service.model.enums.SituacaoDaParcela
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "PARCELAS", schema = "PAY")
class Parcela(

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ID", nullable = false, length = 36)
    var id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PAGAMENTO_ID", nullable = false)
    var pagamento: Pagamento,

    @Column(name = "NUMERO", nullable = false)
    var numero: Int,

    @Column(name = "VALOR", nullable = false, precision = 15, scale = 2)
    var valor: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "SITUACAO", nullable = false, length = 20)
    var situacao: SituacaoDaParcela,

    @Column(name = "VENCIMENTO")
    var vencimento: LocalDate? = null,

    @Column(name = "LIQUIDADO_EM")
    var liquidadoEm: OffsetDateTime? = null
)
