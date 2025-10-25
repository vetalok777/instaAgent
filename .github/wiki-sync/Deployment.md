# Deployment

## Передумови
- Java 17+, Maven 3+
- Docker (рекомендовано)
- Токени Instagram Graph API, ключ LLM

## Локальний запуск
```bash
mvn clean package -DskipTests
java -jar target/instaagent.jar
```

## Змінні оточення
| Name | Опис |
|------|-----|
| IG_VERIFY_TOKEN | Верифікація вебхука |
| IG_ACCESS_TOKEN | Відправка відповідей |
| LLM_API_KEY | Ключ для LLM |
| DB_URL / DB_USER / DB_PASS | Підключення до БД |

## Docker Compose (приклад)
```yaml
version: '3.8'
services:
  app:
    image: instaagent:latest
    build: .
    ports: ['8080:8080']
    environment:
      - IG_VERIFY_TOKEN=${IG_VERIFY_TOKEN}
      - IG_ACCESS_TOKEN=${IG_ACCESS_TOKEN}
      - LLM_API_KEY=${LLM_API_KEY}
      - DB_URL=${DB_URL}
      - DB_USER=${DB_USER}
      - DB_PASS=${DB_PASS}
  db:
    image: postgres:16
    ports: ['5432:5432']
    environment:
      - POSTGRES_PASSWORD=postgres
```
