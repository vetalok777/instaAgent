package org.example.database.repository;


import org.example.database.entity.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    /**
     * Finds the last 10 interactions for a specific user (sender) of a specific client,
     * ordered by timestamp in descending order.
     *
     * @param clientId   The ID of the client.
     * @param senderPsid The Page-Scoped ID of the user.
     * @return The list of 10 or fewer interactions
     */
    List<Interaction> findTop10ByClientIdAndSenderPsidOrderByTimestampDesc(Long clientId, String senderPsid);

    boolean existsByMessageId(String messageId);
}
