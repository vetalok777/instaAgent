package org.example.database.repository;


import org.example.database.entity.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    /**
     * Цей метод буде автоматично реалізований Spring Data JPA.
     * Він шукає останні 10 повідомлень для конкретного senderId,
     * сортуючи їх за часом у зворотному порядку (найновіші перші).
     * @param senderId ID користувача в Instagram
     * @return Список з 10 або менше останніх взаємодій
     */
    List<Interaction> findTop10BySenderIdOrderByTimestampDesc(String senderId);
    boolean existsByMessageId(String messageId);
}
