# Архітектура

```
Instagram → WebhookController → WebhookProcessingService → (DB + RAG + LLM) → Instagram Reply
```

## Компоненти
- **WebhookController** — прийом вебхуків, верифікація підпису
- **WebhookProcessingService** — фільтрація, парсинг, антидублікат, тригер RAG/LLM
- **GeminiChatService** — генерація відповідей
- **InteractionRepository** — історія повідомлень
- **ProductRepository** — товари/ціни/наявність (джерело істини)
- **RetrievalService** *(опц.)* — пошук у векторній БД

## Потік даних
1. Прийняли вебхук → зберегли interaction (ідемпотентно)
2. Витягнули контекст з БД (товар, ціна, наявність)
3. (Опц.) RAG: отримали фрагменти FAQ/постів через векторний пошук
4. Зібрали prompt: System + CoreFacts + RAG + Історія
5. Згенерували відповідь → валідували політиками → надіслали в IG

## Нефункціональні вимоги
- Відповідь вебхуку < 1s, генерація 1.5–2s
- Маскування PII у логах
- Метріки: latency, hit@k, конверсія
