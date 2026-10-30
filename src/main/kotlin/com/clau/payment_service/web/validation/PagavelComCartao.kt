package com.clau.payment_service.web.validation

import com.clau.payment_service.model.enums.MeioDePagamento
import com.clau.payment_service.web.dto.fragmento.CartaoDto

interface PagavelComCartao {

    val meio: MeioDePagamento?

    val cartao: CartaoDto?
}
