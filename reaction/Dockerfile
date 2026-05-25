FROM gradle:9.4.1-jdk21-alpine AS build
WORKDIR /app
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
RUN chmod +x gradlew
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon -q
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /app/build/libs/*.jar app.jar
EXPOSE 8080
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
