package org.example.database.repository;

import org.example.database.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for {@link Client} entity.
 * Provides CRUD operations and custom queries for managing clients.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Finds a client by their unique Instagram page ID.
     *
     * @param instagramPageId The unique ID of the client's Instagram page.
     * @return An {@link Optional} containing the found {@link Client} entity, or an empty Optional if no client is found.
     */
    Optional<Client> findByInstagramPageId(String instagramPageId);
}