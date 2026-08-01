ARG JAVA_BUILD_IMAGE=maven:3.9.9-eclipse-temurin-17
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy

FROM ${JAVA_BUILD_IMAGE} AS build
WORKDIR /build
COPY apps/api/pom.xml .
COPY apps/api/docker/maven-settings.xml /root/.m2/settings.xml
COPY apps/api/src ./src
# -B: batch logs (no -q so dependency/network errors are visible)
# Aliyun mirror via maven-settings.xml for mainland China pulls.
RUN mvn -B -DskipTests package

FROM ${JAVA_RUNTIME_IMAGE}
WORKDIR /app
ENV JAVA_OPTS=""
ENV PORT=3001
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl bash \
  && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/target/today-api-0.1.0.jar /app/app.jar
COPY apps/api/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 755 /app/docker-entrypoint.sh \
  && mkdir -p /data/media \
  && chown -R nobody:nogroup /app /data/media
USER nobody
EXPOSE 3001
VOLUME ["/data/media"]
ENTRYPOINT ["/app/docker-entrypoint.sh"]
