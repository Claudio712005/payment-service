package com.clau.payment_service.model.enums

enum class SituacaoDoPagamento {
    PENDENTE,
    AUTORIZADO,
    PAGO,
    RECUSADO,
    EXPIRADO,
    CANCELADO,
    ESTORNADO_PARCIAL,
    ESTORNADO_TOTAL
}
