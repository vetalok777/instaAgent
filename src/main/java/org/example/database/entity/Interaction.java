package org.example.database.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "sender_psid", nullable = false)
    private String senderPsid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "message_author", nullable = false)
    private String author;

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "message_id", unique = true)
    private String messageId;

    public Interaction(String senderPsid, String author, String text) {
        this.senderPsid = senderPsid;
        this.author = author;
        this.text = text;
        this.timestamp = LocalDateTime.now();
    }
}
