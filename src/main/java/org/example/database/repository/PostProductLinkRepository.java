package org.example.database.repository;

import org.example.database.entity.PostProductLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostProductLinkRepository extends JpaRepository<PostProductLink, Long> {


    Optional<PostProductLink> findByInstagramPostId(String instagramPostId);
}
