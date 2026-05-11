FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system finvibe && useradd --system --gid finvibe --home-dir /app finvibe
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN mkdir -p /app/runtime && chown -R finvibe:finvibe /app
USER finvibe
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
