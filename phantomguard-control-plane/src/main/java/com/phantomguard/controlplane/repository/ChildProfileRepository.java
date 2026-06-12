package com.phantomguard.controlplane.repository;

import com.phantomguard.controlplane.domain.ChildProfileDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChildProfileRepository extends MongoRepository<ChildProfileDocument, String> {

    Optional<ChildProfileDocument> findByClientId(String clientId);

    List<ChildProfileDocument> findByParentIdOrderByCreatedAtAsc(String parentId);

    boolean existsByParentIdAndChildNameIgnoreCase(String parentId, String childName);
}
