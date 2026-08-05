FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --version

COPY src ./src
RUN ./gradlew clean bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
