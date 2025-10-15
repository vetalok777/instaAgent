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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private String senderId;

    @Column(name = "message_author", nullable = false)
    private String author;

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "message_id", unique = true)
    private String messageId;

    public Interaction(String senderId, String author, String text) {
        this.senderId = senderId;
        this.author = author;
        this.text = text;
        this.timestamp = LocalDateTime.now();
    }
}
