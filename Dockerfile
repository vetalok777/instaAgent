# --- Етап 1: Збірка проєкту за допомогою Maven ---
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline --no-transfer-progress
COPY src ./src
RUN mvn clean package -DskipTests

# --- Етап 2: Створення фінального, легкого образу ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN groupadd --system appgroup && useradd --system --gid appgroup appuser
USER appuser

# Копіюємо зібраний .jar файл з етапу збірки
COPY --from=builder /app/target/*.jar app.jar

# Вказуємо порт, на якому працює додаток
EXPOSE 8080
# Запускаємо додаток
ENTRYPOINT ["java", "-jar", "app.jar"]