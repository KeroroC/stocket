ARG GRAALVM_IMAGE=ghcr.io/graalvm/native-image-community:25
FROM ${GRAALVM_IMAGE} AS build

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
    && useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /var/lib/stocket/attachments /var/lib/stocket/backups \
    && chown -R 10001:10001 /var/lib/stocket

WORKDIR /app

COPY --from=build /workspace/target/stocket-backend ./stocket

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["./stocket"]
