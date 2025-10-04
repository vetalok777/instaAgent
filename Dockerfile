# --- Етап 1: Збірка проєкту за допомогою Maven ---
FROM maven:3.8.5-openjdk-11 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# --- Етап 2: Створення фінального, легкого образу ---
FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=builder /app/target/aiAgent-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]