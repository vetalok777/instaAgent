# Database

## Сутності
- **Product**(id, name, price, description, availability, ...)
- **PostProductLink**(instagram_post_id, product_id)
- **Interaction**(user_id, role, message, message_id, created_at)

## Індекси
- `Interaction.message_id` — унікальний (ідемпотентність)
- `PostProductLink.instagram_post_id` — швидкий мапінг post→product
