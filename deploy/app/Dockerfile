FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY backend/.mvn ./.mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY backend/src ./src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre AS runtime

RUN apt-get update \
    && apt-get install --no-install-recommends --yes ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /var/lib/stocket/attachments /var/lib/stocket/backups \
    && chown -R 10001:10001 /var/lib/stocket

WORKDIR /app

COPY --from=build /workspace/target/stocket-backend-*.jar ./stocket.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/stocket.jar"]
