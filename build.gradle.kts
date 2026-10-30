plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.openapi.generator") version "7.14.0"
}

group = "com.clau"
version = "0.0.1-SNAPSHOT"
description = "payment-service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-redis") {
        exclude(group = "io.lettuce", module = "lettuce-core")
    }
    implementation("redis.clients:jedis")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-oracle")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.3.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.3.0")
    implementation("io.github.resilience4j:resilience4j-bulkhead:2.3.0")
    implementation("io.github.resilience4j:resilience4j-micrometer:2.3.0")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("com.oracle.database.jdbc:ojdbc17")
    runtimeOnly("io.micrometer:micrometer-registry-otlp")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-opentelemetry-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val contratoRest = layout.projectDirectory.file("apis/payment-service.openapi.yaml")
val contratoGerado = layout.buildDirectory.dir("generated/openapi")

val esquemasEscritosAMao = mapOf(
    "SolicitarCobranca" to "com.clau.payment_service.web.dto.request.SolicitarCobrancaRequest",
    "Pagamento" to "com.clau.payment_service.web.dto.response.PagamentoResponse",
    "PaginaDePagamentos" to "com.clau.payment_service.web.dto.response.PaginaDePagamentosResponse",
    "Parcela" to "com.clau.payment_service.web.dto.response.ParcelaResponse",
    "InstrucaoDePagamento" to "com.clau.payment_service.web.dto.response.InstrucaoDePagamentoResponse",
    "InstrucaoPix" to "com.clau.payment_service.web.dto.response.InstrucaoPixResponse",
    "InstrucaoBoleto" to "com.clau.payment_service.web.dto.response.InstrucaoBoletoResponse",
    "InstrucaoCartao" to "com.clau.payment_service.web.dto.response.InstrucaoCartaoResponse",
    "Valor" to "com.clau.payment_service.web.dto.fragmento.ValorDto",
    "Pagador" to "com.clau.payment_service.web.dto.fragmento.PagadorDto",
    "Documento" to "com.clau.payment_service.web.dto.fragmento.DocumentoDto",
    "Contato" to "com.clau.payment_service.web.dto.fragmento.ContatoDto",
    "Cartao" to "com.clau.payment_service.web.dto.fragmento.CartaoDto",
    "Problema" to "com.clau.payment_service.web.error.ProblemaResponse",
    "Violacao" to "com.clau.payment_service.web.error.ProblemaResponse.ViolacaoResponse",
    "MeioDePagamento" to "com.clau.payment_service.model.enums.MeioDePagamento",
    "SituacaoDoPagamento" to "com.clau.payment_service.model.enums.SituacaoDoPagamento",
    "SituacaoDaParcela" to "com.clau.payment_service.model.enums.SituacaoDaParcela",
    "TipoDeDocumento" to "com.clau.payment_service.web.validation.TipoDeDocumento"
)

openApiValidate {
    inputSpec = contratoRest.asFile.path
    recommend = true
}

openApiGenerate {
    generatorName = "kotlin-spring"
    inputSpec = contratoRest.asFile.path
    outputDir = contratoGerado.map { it.asFile.path }
    apiPackage = "com.clau.payment_service.web.api"
    modelPackage = "com.clau.payment_service.web.dto"
    schemaMappings = esquemasEscritosAMao
    importMappings = esquemasEscritosAMao
    globalProperties = mapOf(
        "apis" to "",
        "models" to "false",
        "supportingFiles" to "false"
    )
    configOptions = mapOf(
        "useSpringBoot3" to "true",
        "interfaceOnly" to "true",
        "skipDefaultInterface" to "true",
        "useTags" to "true",
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
        "serializationLibrary" to "jackson",
        "useBeanValidation" to "true",
        "enumPropertyNaming" to "UPPERCASE"
    )
}

sourceSets {
    main {
        kotlin.srcDir(contratoGerado.map { it.dir("src/main/kotlin") })
    }
}

tasks.named("compileKotlin") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.named("openApiGenerate") {
    dependsOn(tasks.named("openApiValidate"))
}
