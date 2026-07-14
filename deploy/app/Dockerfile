FROM ghcr.io/graalvm/native-image-community:25 AS build

WORKDIR /workspace

COPY backend/.mvn ./.mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY backend/src ./src
RUN ./mvnw -B -Pnative -DskipTests package native:compile

FROM debian:bookworm-slim AS runtime

RUN apt-get update \
    && apt-get install --no-install-recommends --yes ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app

COPY --from=build /workspace/target/stocket-backend ./stocket

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["./stocket"]
