package org.example.database.repository;

import org.example.database.entity.PostProductLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostProductLinkRepository extends JpaRepository<PostProductLink, Long> {
    // Метод для пошуку зв'язку за ID поста з Instagram
    Optional<PostProductLink> findByInstagramPostId(String instagramPostId);
}
