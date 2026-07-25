ARG JAVA_BUILD_IMAGE=maven:3.9.9-eclipse-temurin-17
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy

FROM ${JAVA_BUILD_IMAGE} AS build
WORKDIR /build
COPY apps/api/pom.xml .
COPY apps/api/src ./src
RUN mvn -q -DskipTests package

FROM ${JAVA_RUNTIME_IMAGE}
WORKDIR /app
ENV JAVA_OPTS=""
ENV PORT=3001
COPY --from=build /build/target/today-api-0.1.0.jar /app/app.jar
EXPOSE 3001
USER nobody
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
