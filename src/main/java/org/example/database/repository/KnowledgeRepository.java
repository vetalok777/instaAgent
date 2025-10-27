package org.example.database.repository;

import org.example.database.entity.Knowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for the {@link Knowledge} entity.
 * <p>
 * Provides standard CRUD operations and custom queries for vector search
 * to find relevant knowledge base entries.
 */
@Repository
public interface KnowledgeRepository extends JpaRepository<Knowledge, Long> {

    /**
     * Finds the nearest neighbors for a given query vector within a specific client's knowledge base.
     * This method uses the cosine distance operator (<->) from the pgvector extension.
     */
    @Query(value = "SELECT * FROM knowledge WHERE client_id = :clientId ORDER BY embedding <-> CAST(:queryVector AS vector) LIMIT :limit", nativeQuery = true)
    List<Knowledge> findNearestNeighbors(@Param("clientId") Long clientId, @Param("queryVector") float[] queryVector, @Param("limit") int limit);

    void deleteAllByCatalogItemId(Long catalogItemId);

    /**
     * Finds all knowledge entries for a specific client that are not associated with any catalog item.
     * These entries represent general information about the client's business.
     *
     * @param clientId The ID of the client.
     * @return A list of general {@link Knowledge} entries.
     */
    List<Knowledge> findByClientIdAndCatalogItemIsNull(Long clientId);
}