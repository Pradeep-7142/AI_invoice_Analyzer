# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r invoiceiq && useradd -r -g invoiceiq invoiceiq
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /data/storage && chown -R invoiceiq:invoiceiq /data/storage
USER invoiceiq

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
