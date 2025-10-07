package org.example.database.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "interactions")
public class Interaction {

    @Id // Позначає первинний ключ (Primary Key)
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Автоматична генерація ID базою даних
    private Long id;

    @Column(name = "sender_id", nullable = false) // ID користувача з Instagram
    private String senderId;

    @Column(name = "message_author", nullable = false) // Хто автор: "USER" чи "AI"
    private String author;

     // Вказує, що це поле може зберігати великий обсяг тексту
    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "message_id", unique = true)
    private String messageId;

    // Конструктор для зручного створення об'єктів залишаємо,
    // оскільки він містить логіку встановлення часу.
    public Interaction(String senderId, String author, String text) {
        this.senderId = senderId;
        this.author = author;
        this.text = text;
        this.timestamp = LocalDateTime.now();
    }
}
