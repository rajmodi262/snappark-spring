# syntax=docker/dockerfile:1

# ---- build: package with the Maven wrapper (tests run in the CI pipeline, not here) ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests -Djacoco.skip=true \
 && java -Djarmode=tools -jar target/snappark-*.jar extract --layers --launcher --destination /layers

# ---- runtime: JRE only, non-root, dependencies in their own cacheable layer ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S snappark && adduser -S snappark -G snappark
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER snappark
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
