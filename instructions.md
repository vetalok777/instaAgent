### **Фаза 1: Підготовчий етап — База Даних та Моделі (Foundation)**

На цьому етапі ми готуємо фундамент нашої нової архітектури. Ми не пишемо бізнес-логіку, а лише створюємо структури для зберігання даних.

- **Завдання 1.1: Модифікація Схеми Бази Даних**
    1. **Увімкніть розширення `pg_vector`** у вашій базі даних Supabase. Це робиться в UI Supabase (`Database` -> `Extensions`).
    2. **Створіть таблицю `Clients`**:
        - `id` (BIGINT, Primary Key, Identity)
        - `client_name` (VARCHAR)
        - `instagram_page_id` (VARCHAR, Unique, Not Null)
        - `encrypted_access_token` (TEXT, Not Null)
        - `ai_system_prompt` (TEXT, Not Null)
    3. **Створіть таблицю `Knowledge`**:
        - `id` (BIGINT, Primary Key, Identity)
        - `client_id` (BIGINT, Foreign Key -> `Clients.id`)
        - `content` (TEXT)
        - `embedding` (VECTOR(768)) - *Розмірність 768 є стандартом для багатьох моделей Gemini embedding.*
    4. **Модифікуйте таблицю `Interactions`**:
        - Додайте колонку `client_id` (BIGINT, Foreign Key -> `Clients.id`).
        - Перейменуйте колонку `sender_id` на `sender_psid` (Page-Scoped ID) для ясності.
- **Завдання 1.2: Створення та Оновлення JPA Entities**
    1. **Створіть `Client.java`**: Нова Entity, що відповідає таблиці `Clients`.
    2. **Створіть `Knowledge.java`**: Нова Entity, що відповідає таблиці `Knowledge`.
    3. **Оновіть `Interaction.java`**:
        - Додайте поле `private Client client;` з анотацією `@ManyToOne`.
        - Перейменуйте поле `senderId` на `senderPsid` з анотацією `@Column(name = "sender_psid")`.
    4. **Видаліть `Product.java`, `ProductVariant.java`, `PostProductLink.java`**: Вони більше не потрібні в новій архітектурі.
- **Завдання 1.3: Створення та Оновлення JPA Repositories**
    1. **Створіть `ClientRepository.java`**: Додайте метод `Optional<Client> findByInstagramPageId(String pageId);`.
    2. **Створіть `KnowledgeRepository.java`**: Буде містити метод для векторного пошуку.
    3. **Оновіть `InteractionRepository.java`**: Змініть метод для пошуку історії на: `List<Interaction> findTop10ByClientIdAndSenderPsidOrderByTimestampDesc(Long clientId, String senderPsid);`.
    4. **Видаліть `ProductRepository.java`, `ProductVariantRepository.java`, `PostProductLinkRepository.java`**.

---

### **Фаза 2: Реалізація RAG — Ядро Знань (Knowledge Core)**

На цьому етапі ми створюємо сервіси для роботи з векторами — наповнення бази знань та пошуку в ній.

- **Завдання 2.1: Створення `GeminiEmbeddingService.java`**
    - Створіть новий сервіс.
    - Реалізуйте один метод: `float[] getEmbedding(String text)`. Цей метод буде викликати відповідний ендпоінт Gemini Embedding API, передавати текст і повертати масив чисел (вектор).
- **Завдання 2.2: Створення `RAGService.java` (замість `ProductService`)**
    - Цей сервіс буде залежати від `KnowledgeRepository` та `GeminiEmbeddingService`.
    - **Реалізуйте метод для наповнення:** `void createAndStoreEmbedding(Client client, String content)`. Він буде:
        1. Викликати `geminiEmbeddingService.getEmbedding(content)`.
        2. Створювати новий об'єкт `Knowledge`.
        3. Зберігати його через `knowledgeRepository.save()`.
    - **Реалізуйте метод для пошуку:** `String findRelevantContext(Client client, String userQuery, int limit)`. Він буде:
        1. Викликати `geminiEmbeddingService.getEmbedding(userQuery)` для отримання вектора запиту.
        2. Викликати спеціальний метод у `KnowledgeRepository` для векторного пошуку.
        3. Форматувати знайдені `content` у єдиний текстовий блок контексту.
- **Завдання 2.3: Реалізація Векторного Пошуку в `KnowledgeRepository.java`**
    - Додайте метод, який використовує `@Query(nativeQuery = true, ...)` для виконання SQL-запиту з оператором `<->` (косинусна відстань) з `pg_vector`.Java
        
        `@Query(value = "SELECT * FROM knowledge WHERE client_id = :clientId ORDER BY embedding <-> :queryVector LIMIT :limit", nativeQuery = true)
        List<Knowledge> findNearestNeighbors(Long clientId, float[] queryVector, int limit);`
        

---

### **Фаза 3: Рефакторинг Основної Логіки (Core Logic Refactoring)**

Тепер, коли база даних та RAG готові, ми адаптуємо існуючі сервіси для роботи в multi-tenant режимі.

- **Завдання 3.1: Рефакторинг `InstagramMessageService.java`**
    - Змініть сигнатуру методу `sendReply`, щоб він приймав токен доступу: `void sendReply(String accessToken, String recipientId, String text)`. Це необхідно, оскільки кожен клієнт матиме свій токен.
- **Завдання 3.2: Рефакторинг `GeminiChatService.java`**
    - **Видаліть залежність `ProductService`** з конструктора.
    - **Додайте залежність `RAGService`**.
    - Змініть сигнатуру методу `sendMessage` на: `String sendMessage(Client client, String userPsid, String userMessage)`.
    - **Оновіть логіку методу:**
        1. Отримайте системний промпт з `client.getAiSystemPrompt()`.
        2. Отримайте історію чату, викликавши `interactionRepository.findTop10ByClientIdAndSenderPsidOrderByTimestampDesc(client.getId(), userPsid)`.
        3. Отримайте релевантний контекст, викликавши `ragService.findRelevantContext(client, userMessage, 3)`.
        4. Скомпонуйте фінальний промпт (системний промпт + історія + RAG-контекст + повідомлення користувача) і відправте в Gemini.
- **Завдання 3.3: Рефакторинг `WebhookProcessingService.java`**
    - **Додайте залежність `ClientRepository`**.
    - **Оновіть логіку методу `processWebhookPayload`**:
        1. Витягніть `recipient.id` (це `instagram_page_id`) та `sender.id` (це `sender_psid`) з JSON-пейлоаду.
        2. Зробіть запит `clientRepository.findByInstagramPageId(...)`.
        3. Якщо клієнт не знайдений — залогуйте помилку та завершіть виконання.
        4. При збереженні `Interaction` встановлюйте `client` (`interaction.setClient(client)`).
        5. Викликайте оновлені методи `geminiChatService.sendMessage(client, ...)` та `instagramMessageService.sendReply(client.getDecryptedAccessToken(), ...)`.

---

### **Фаза 4: Підключення Клієнтів та Деплой (Onboarding & Deployment)**

Фінальні кроки для запуску системи.

- **Завдання 4.1: Створення Скрипту/Ендпоінту для "Онбордингу"**
    - Створіть простий інструмент (це може бути окремий Java-клас з `main` методом або захищений `@RestController` ендпоінт), який:
        1. Приймає на вхід `clientId` та шлях до файлу (CSV/PDF).
        2. Читає файл, "нарізає" його на шматки тексту.
        3. В циклі викликає `ragService.createAndStoreEmbedding(client, chunk)` для кожного шматка.
- **Завдання 4.2: Оновлення Конфігурації та Секретів**
    - **Видаліть** `instagram.page.id` та `instagram.access.token` з `application.properties` [cite: application.properties] та секретів Cloud Run [cite: image_c40d36.png]. Вони тепер зберігаються в базі даних.
    - Реалізуйте простий механізм шифрування/дешифрування для `encrypted_access_token` в таблиці `Clients` (наприклад, за допомогою `Jasypt Spring Boot Starter`).
- **Завдання 4.3: Тестування та Деплой**
    1. **Додайте вручну** в таблицю `Clients` два тестових клієнти (напр., "Магазин одягу" та "Магазин солодощів") з їхніми Page ID та промптами.
    2. Запустіть скрипт онбордингу, щоб наповнити їхні бази знань.
    3. Протестуйте повний цикл для обох клієнтів, щоб переконатися в повній ізоляції даних та логіки.
    4. Розгорніть оновлений додаток на Cloud Run.