package com.clau.payment_service.web.validation

import com.clau.payment_service.model.enums.MeioDePagamento

interface Parcelavel {

    val meio: MeioDePagamento?

    val parcelas: Int
}
