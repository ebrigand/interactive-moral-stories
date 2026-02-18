package com.istory.storyengine.repository;

import com.istory.storyengine.model.StoryNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoryNodeRepository extends JpaRepository<StoryNode, UUID> {

    Optional<StoryNode> findFirstBySessionIdAndSegmentIndex(UUID sessionId, int segmentIndex);

    Optional<StoryNode> findFirstBySessionIdOrderBySegmentIndexDesc(UUID sessionId);

    void deleteBySessionIdAndSegmentIndexGreaterThan(UUID sessionId, int segmentIndex);

    /**
     * ✅ Implémentation Java (default method) -> Spring Data ne tente PAS de générer de query.
     * Equivalent logique : dernier node = segmentIndex le plus élevé.
     */
    default Optional<StoryNode> findLastNode(UUID sessionId) {
        return findFirstBySessionIdOrderBySegmentIndexDesc(sessionId);
    }
}
