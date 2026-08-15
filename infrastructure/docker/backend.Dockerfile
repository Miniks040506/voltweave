FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY libs libs
COPY services services
COPY simulator simulator
COPY tests tests

RUN chmod +x mvnw && ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S voltweave && adduser -S -u 10001 -G voltweave voltweave
WORKDIR /app
USER voltweave
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS api-gateway
COPY --from=build --chown=voltweave:voltweave \
  /workspace/services/api-gateway/target/api-gateway-*.jar /app/app.jar

FROM runtime AS portfolio-service
COPY --from=build --chown=voltweave:voltweave \
  /workspace/services/portfolio-service/target/portfolio-service-*.jar /app/app.jar

FROM runtime AS telemetry-service
COPY --from=build --chown=voltweave:voltweave \
  /workspace/services/telemetry-service/target/telemetry-service-*.jar /app/app.jar

FROM runtime AS intelligence-service
COPY --from=build --chown=voltweave:voltweave \
  /workspace/services/intelligence-service/target/intelligence-service-*.jar /app/app.jar

FROM runtime AS dispatch-service
COPY --from=build --chown=voltweave:voltweave \
  /workspace/services/dispatch-service/target/dispatch-service-*.jar /app/app.jar

FROM runtime AS settlement-service
COPY --from=build --chown=voltweave:voltweave \
  /workspace/services/settlement-service/target/settlement-service-*.jar /app/app.jar
