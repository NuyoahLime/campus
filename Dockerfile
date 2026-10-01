FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S campus && adduser -S -G campus campus
WORKDIR /app

COPY --from=build --chown=campus:campus /workspace/target/campus-guinness-*.jar /app/app.jar

USER campus
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=production
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
