# syntax=docker/dockerfile:1.7

ARG JAVA_VERSION=25

FROM eclipse-temurin:${JAVA_VERSION}-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test

RUN mv build/libs/*-SNAPSHOT.jar /workspace/aplicacao.jar


FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS extracao

WORKDIR /extracao

COPY --from=build /workspace/aplicacao.jar ./aplicacao.jar

RUN java -Djarmode=tools -jar aplicacao.jar extract --layers --launcher --destination extraido


FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime

RUN addgroup -S payment && adduser -S payment -G payment

WORKDIR /app

COPY --from=extracao --chown=payment:payment /extracao/extraido/dependencies/ ./
COPY --from=extracao --chown=payment:payment /extracao/extraido/spring-boot-loader/ ./
COPY --from=extracao --chown=payment:payment /extracao/extraido/snapshot-dependencies/ ./
COPY --from=extracao --chown=payment:payment /extracao/extraido/application/ ./

USER payment

ENV JDK_JAVA_OPTIONS="\
-XX:MaxRAMPercentage=60 \
-XX:InitialRAMPercentage=25 \
-XX:+UseSerialGC \
-XX:+ExitOnOutOfMemoryError \
-Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
