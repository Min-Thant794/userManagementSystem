# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/maven-wrapper.properties
COPY mvnw pom.xml ./
COPY src/main ./src/main

# Build the artifact here; run the test suites separately against test services.
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy AS runtime

# curl supplies the local HTTP health probe. The application runs as app, not root.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/*.jar ./app.jar
USER app:app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]