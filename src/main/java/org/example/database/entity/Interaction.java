package org.example.database.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

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

    // Конструктор без аргументів (обов'язковий для Hibernate)
    public Interaction() {
    }

    // Зручний конструктор для створення об'єктів
    public Interaction(String senderId, String author, String text) {
        this.senderId = senderId;
        this.author = author;
        this.text = text;
        this.timestamp = LocalDateTime.now(); // Автоматично встановлюємо поточний час
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
