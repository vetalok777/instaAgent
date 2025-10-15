package org.example.database.repository;


import org.example.database.entity.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    /**
     * Searching for the last 10 messages of senderId,
     * sorting them by the time in descending order.
     * @param senderId ID of the user from Instagram
     * @return The list of 10 or fewer interactions
     */
    List<Interaction> findTop10BySenderIdOrderByTimestampDesc(String senderId);
    boolean existsByMessageId(String messageId);
}
