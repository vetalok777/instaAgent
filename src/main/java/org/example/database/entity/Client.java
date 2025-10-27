package org.example.database.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@ToString(exclude = "accessToken")
@Table(name = "clients")
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "instagram_page_id", nullable = false, unique = true)
    private String instagramPageId;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "ai_system_prompt", nullable = false, columnDefinition = "TEXT")
    private String aiSystemPrompt;

}
