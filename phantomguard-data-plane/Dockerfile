# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY phantomguard-common/pom.xml phantomguard-common/
COPY phantomguard-data-plane/pom.xml phantomguard-data-plane/
COPY phantomguard-control-plane/pom.xml phantomguard-control-plane/
RUN mvn -q -pl phantomguard-data-plane -am dependency:go-offline || true
COPY phantomguard-common phantomguard-common
COPY phantomguard-data-plane phantomguard-data-plane
RUN mvn -q -pl phantomguard-data-plane -am package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 phantomguard
USER phantomguard
WORKDIR /app
COPY --from=build /workspace/phantomguard-data-plane/target/phantomguard-data-plane-*.jar app.jar
EXPOSE 8443 853
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
