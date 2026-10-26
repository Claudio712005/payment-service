package com.clau.payment_service.web.handler

import com.clau.payment_service.application.exception.ConflitoDeEstadoException
import com.clau.payment_service.application.exception.RecursoNaoEncontradoException
import com.clau.payment_service.application.exception.RegraDeNegocioException
import com.clau.payment_service.config.i18n.MessageSourceConfig
import com.clau.payment_service.web.dto.request.SolicitarCobrancaRequest
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import jakarta.validation.Valid
import kotlin.test.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@WebMvcTest
@Import(
    MessageSourceConfig::class,
    RestExceptionHandler::class,
    RestExceptionHandlerTest.ControladorDeTeste::class
)
class RestExceptionHandlerTest {

    @RestController
    @RequestMapping("/teste")
    class ControladorDeTeste {

        @PostMapping("/cobrancas")
        fun criar(@Valid @RequestBody request: SolicitarCobrancaRequest): String = "ok"

        @GetMapping("/ausente/{id}")
        fun ausente(@PathVariable id: String): Nothing =
            throw RecursoNaoEncontradoException("Pagamento", id)

        @GetMapping("/regra")
        fun regra(): Nothing =
            throw RegraDeNegocioException("erro.regra-de-negocio.titulo")

        @GetMapping("/conflito")
        fun conflito(): Nothing =
            throw ConflitoDeEstadoException("erro.conflito.titulo")

        @GetMapping("/saturado")
        fun saturado(): Nothing =
            throw BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("payment-provider"))

        @GetMapping("/indisponivel")
        fun indisponivel(): Nothing {
            val circuito = CircuitBreaker.ofDefaults("charge-query")
            circuito.transitionToOpenState()
            throw CallNotPermittedException.createCallNotPermittedException(circuito)
        }

        @GetMapping("/explode")
        fun explode(): Nothing = throw IllegalStateException("detalhe interno que nao deve vazar")
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val corpoInvalido = """
        {
          "ordemServicoId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
          "meio": "PIX",
          "valor": { "quantia": 0, "moeda": "brl" },
          "pagador": { "nome": "An", "documento": { "tipo": "CPF", "numero": "529.982.247-26" } },
          "parcelas": 3
        }
    """.trimIndent()

    @Test
    fun `erro de validacao responde problem json em portugues`() {
        mockMvc.perform(
            post("/teste/cobrancas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR")
                .content(corpoInvalido)
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://payment-service/erros/validacao"))
            .andExpect(jsonPath("$.title").value("Requisição inválida"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.instance").value("/teste/cobrancas"))
            .andExpect(jsonPath("$.violacoes[?(@.campo=='pagador.documento.numero')].mensagem")
                .value("Número de documento inválido"))
            .andExpect(jsonPath("$.violacoes[?(@.campo=='parcelas')].mensagem")
                .value("Parcelamento acima de uma parcela é permitido apenas para cartão de crédito"))
    }

    @Test
    fun `erro de validacao responde em ingles`() {
        mockMvc.perform(
            post("/teste/cobrancas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                .content(corpoInvalido)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.violacoes[?(@.campo=='pagador.documento.numero')].mensagem")
                .value("Invalid document number"))
            .andExpect(jsonPath("$.violacoes[?(@.campo=='valor.moeda')].mensagem")
                .value("Currency must follow ISO 4217, with three uppercase letters"))
    }

    @Test
    fun `erro de validacao responde em espanhol`() {
        mockMvc.perform(
            post("/teste/cobrancas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "es")
                .content(corpoInvalido)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Solicitud no válida"))
            .andExpect(jsonPath("$.violacoes[?(@.campo=='pagador.documento.numero')].mensagem")
                .value("Número de documento no válido"))
    }

    @Test
    fun `idioma nao suportado cai no portugues`() {
        mockMvc.perform(
            post("/teste/cobrancas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE")
                .content(corpoInvalido)
        )
            .andExpect(jsonPath("$.title").value("Requisição inválida"))
    }

    @Test
    fun `as violacoes vem ordenadas por campo e com o valor recebido`() {
        mockMvc.perform(
            post("/teste/cobrancas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR")
                .content(corpoInvalido)
        )
            .andExpect(jsonPath("$.violacoes[0].campo").value("pagador.documento.numero"))
            .andExpect(jsonPath("$.violacoes[0].recebido").value("529.982.247-26"))
    }

    @Test
    fun `corpo ilegivel responde 400 com o tipo de problema proprio`() {
        mockMvc.perform(
            post("/teste/cobrancas")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                .content("{ nao e json")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.type").value("https://payment-service/erros/corpo-invalido"))
            .andExpect(jsonPath("$.detail").value("The body sent cannot be parsed"))
    }

    @Test
    fun `recurso ausente responde 404 com recurso e identificador na mensagem`() {
        mockMvc.perform(get("/teste/ausente/abc").header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.type").value("https://payment-service/erros/nao-encontrado"))
            .andExpect(jsonPath("$.detail").value("Pagamento com identificador 'abc' não existe"))
    }

    @Test
    fun `regra de negocio responde 422 e conflito responde 409`() {
        mockMvc.perform(get("/teste/regra").header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR"))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.title").value("Regra de negócio violada"))

        mockMvc.perform(get("/teste/conflito").header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.title").value("Conflicto de estado"))
    }

    @Test
    fun `bulkhead cheio responde 503 com retry-after curto`() {
        mockMvc.perform(get("/teste/saturado").header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
            .andExpect(jsonPath("$.type").value("https://payment-service/erros/dependencia-saturada"))
    }

    @Test
    fun `circuito aberto responde 503 com retry-after longo e nomeia a operacao`() {
        mockMvc.perform(get("/teste/indisponivel").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
            .andExpect(jsonPath("$.detail").value("Calls to charge-query are suspended after consecutive failures"))
    }

    @Test
    fun `falha inesperada responde 500 sem vazar o detalhe interno`() {
        mockMvc.perform(get("/teste/explode").header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.type").value("https://payment-service/erros/interno"))
            .andExpect(jsonPath("$.detail")
                .value("A requisição não pôde ser concluída. Use o identificador de correlação para a investigação"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("detalhe interno"))))
    }
}
