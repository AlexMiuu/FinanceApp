# Parameterized build for all backend services.
# Usage: docker build --build-arg MODULE=<service-dir> -f backend/Dockerfile backend/
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /build/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
