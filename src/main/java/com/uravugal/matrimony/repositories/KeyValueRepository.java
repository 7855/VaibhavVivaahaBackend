package com.uravugal.matrimony.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uravugal.matrimony.models.KeyValue;

public interface KeyValueRepository extends JpaRepository<KeyValue, Long> {
    
    /**
     * Checks if a key-value pair exists with the given key
     * @param key The key to check
     * @return true if a key-value pair with the given key exists, false otherwise
     */
    boolean existsByKey(String key);
    
    /**
     * Finds a key-value pair by its key
     * @param key The key to search for
     * @return An Optional containing the key-value pair if found, or empty if not found
     */
    Optional<KeyValue> findByKey(String key);
}
