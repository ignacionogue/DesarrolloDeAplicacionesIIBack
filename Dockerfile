FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp clean package

FROM eclipse-temurin:17-jre-jammy

RUN useradd --system --uid 10001 --create-home appuser
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
