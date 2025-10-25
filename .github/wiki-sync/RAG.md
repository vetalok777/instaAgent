# RAG (Retrieval-Augmented Generation)

## Джерела даних
- Каталог товарів (SKU, опис, ціна, наявність)
- FAQ/політики
- Описи постів/сторіс

## Індексація
1) Екстракція → 2) Нормалізація → 3) Чанкування (200–500 токенів) → 4) Метадані → 5) Embeddings → 6) Vector DB

## Пошук
- Top-k: 8–12
- Фільтри: `lang`, `type`, `sku`, `updated_at`
- Ререйк: BM25 або cross-encoder (50→5)

## Промпт
[System Instruction] -> Контекст БД -> Контекст RAG -> Завдання

## Приклад схеми (pgvector)
CREATE TABLE rag_chunks (id bigserial PRIMARY KEY, doc_id text, type text, lang text, sku text, content text, meta jsonb, embedding vector(768), updated_at timestamptz);
