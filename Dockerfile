# Base images come from the AWS public mirror of Docker Hub (Docker Hub limits anonymous pulls). Another registry with
# the same official images can be chosen with --build-arg BASE_REGISTRY=...; GitHub Actions does it when one refuses.
ARG BASE_REGISTRY=public.ecr.aws/docker/library

FROM ${BASE_REGISTRY}/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM ${BASE_REGISTRY}/eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --create-home appuser
COPY --from=build /build/target/wifisense-backend-*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
