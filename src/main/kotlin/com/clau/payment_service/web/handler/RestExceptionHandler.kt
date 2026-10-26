package com.clau.payment_service.web.handler

import com.clau.payment_service.application.exception.ConflitoDeEstadoException
import com.clau.payment_service.application.exception.RecursoNaoEncontradoException
import com.clau.payment_service.application.exception.RegraDeNegocioException
import com.clau.payment_service.observability.logging.CorrelationKeys
import com.clau.payment_service.web.error.ProblemaResponse
import com.clau.payment_service.web.error.TipoDeProblema
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import java.time.OffsetDateTime
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class RestExceptionHandler(private val messageSource: MessageSource) {

    private val log = LoggerFactory.getLogger(RestExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun corpoInvalido(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> {
        val violacoes = exception.bindingResult.allErrors.map { erro ->
            ProblemaResponse.ViolacaoResponse(
                campo = (erro as? FieldError)?.field ?: erro.objectName,
                mensagem = erro.defaultMessage ?: traduzir("erro.validacao.titulo"),
                recebido = (erro as? FieldError)?.rejectedValue?.toString()
            )
        }
        return problema(
            TipoDeProblema.VALIDACAO,
            HttpStatus.BAD_REQUEST,
            traduzir("erro.validacao.detalhe", violacoes.size),
            request,
            violacoes
        )
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun parametroInvalido(
        exception: HandlerMethodValidationException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> {
        val violacoes = exception.parameterValidationResults.flatMap { resultado ->
            resultado.resolvableErrors.map { erro ->
                ProblemaResponse.ViolacaoResponse(
                    campo = resultado.methodParameter.parameterName ?: "",
                    mensagem = erro.defaultMessage ?: traduzir("erro.parametro.titulo")
                )
            }
        }
        return problema(
            TipoDeProblema.VALIDACAO,
            HttpStatus.BAD_REQUEST,
            traduzir("erro.validacao.detalhe", violacoes.size),
            request,
            violacoes
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun restricaoViolada(
        exception: ConstraintViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> {
        val violacoes = exception.constraintViolations.map { violacao ->
            ProblemaResponse.ViolacaoResponse(
                campo = violacao.propertyPath.toString(),
                mensagem = violacao.message,
                recebido = violacao.invalidValue?.toString()
            )
        }
        return problema(
            TipoDeProblema.VALIDACAO,
            HttpStatus.BAD_REQUEST,
            traduzir("erro.validacao.detalhe", violacoes.size),
            request,
            violacoes
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun corpoIlegivel(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> = problema(
        TipoDeProblema.CORPO_INVALIDO,
        HttpStatus.BAD_REQUEST,
        traduzir("erro.corpo.detalhe"),
        request
    )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun tipoDeParametroInvalido(
        exception: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> = problema(
        TipoDeProblema.PARAMETRO_INVALIDO,
        HttpStatus.BAD_REQUEST,
        traduzir("erro.parametro.detalhe", exception.name),
        request
    )

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun metodoNaoSuportado(
        exception: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> = problema(
        TipoDeProblema.METODO_NAO_SUPORTADO,
        HttpStatus.METHOD_NOT_ALLOWED,
        traduzir("erro.metodo.detalhe", exception.method),
        request
    )

    @ExceptionHandler(RecursoNaoEncontradoException::class)
    fun naoEncontrado(
        exception: RecursoNaoEncontradoException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> = problema(
        TipoDeProblema.NAO_ENCONTRADO,
        HttpStatus.NOT_FOUND,
        traduzir("erro.nao-encontrado.detalhe", exception.recurso, exception.identificador),
        request
    )

    @ExceptionHandler(ConflitoDeEstadoException::class)
    fun conflito(
        exception: ConflitoDeEstadoException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> = problema(
        TipoDeProblema.CONFLITO,
        HttpStatus.CONFLICT,
        traduzir(exception.chaveDeMensagem, *exception.argumentos),
        request
    )

    @ExceptionHandler(RegraDeNegocioException::class)
    fun regraDeNegocio(
        exception: RegraDeNegocioException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> = problema(
        TipoDeProblema.REGRA_DE_NEGOCIO,
        HttpStatus.UNPROCESSABLE_ENTITY,
        traduzir(exception.chaveDeMensagem, *exception.argumentos),
        request
    )

    @ExceptionHandler(BulkheadFullException::class)
    fun dependenciaSaturada(
        exception: BulkheadFullException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> = problema(
        TipoDeProblema.DEPENDENCIA_SATURADA,
        HttpStatus.SERVICE_UNAVAILABLE,
        traduzir("erro.dependencia-saturada.detalhe", exception.message ?: ""),
        request,
        cabecalhos = HttpHeaders().apply { set(HttpHeaders.RETRY_AFTER, RETRY_CURTO) }
    )

    @ExceptionHandler(CallNotPermittedException::class)
    fun dependenciaIndisponivel(
        exception: CallNotPermittedException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> = problema(
        TipoDeProblema.DEPENDENCIA_INDISPONIVEL,
        HttpStatus.SERVICE_UNAVAILABLE,
        traduzir("erro.dependencia-indisponivel.detalhe", exception.causingCircuitBreakerName),
        request,
        cabecalhos = HttpHeaders().apply { set(HttpHeaders.RETRY_AFTER, RETRY_LONGO) }
    )

    @ExceptionHandler(Exception::class)
    fun inesperado(
        exception: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ProblemaResponse> {
        log.error("falha nao tratada em {} {}", request.method, request.requestURI, exception)
        return problema(
            TipoDeProblema.INTERNO,
            HttpStatus.INTERNAL_SERVER_ERROR,
            traduzir("erro.interno.detalhe"),
            request
        )
    }

    private fun problema(
        tipo: TipoDeProblema,
        status: HttpStatus,
        detalhe: String,
        request: HttpServletRequest,
        violacoes: List<ProblemaResponse.ViolacaoResponse> = emptyList(),
        cabecalhos: HttpHeaders = HttpHeaders()
    ): ResponseEntity<ProblemaResponse> {
        cabecalhos.contentType = MediaType.APPLICATION_PROBLEM_JSON
        val corpo = ProblemaResponse(
            type = tipo.uriCompleta(),
            title = traduzir(tipo.chaveDoTitulo),
            status = status.value(),
            detail = detalhe,
            instance = request.requestURI,
            ocorridoEm = OffsetDateTime.now(),
            correlationId = MDC.get(CorrelationKeys.CORRELATION_ID),
            violacoes = violacoes.sortedBy { it.campo }
        )
        return ResponseEntity(corpo, cabecalhos, status)
    }

    private fun traduzir(chave: String, vararg argumentos: Any): String =
        messageSource.getMessage(chave, argumentos, LocaleContextHolder.getLocale())

    private companion object {
        const val RETRY_CURTO = "1"
        const val RETRY_LONGO = "30"
    }
}
