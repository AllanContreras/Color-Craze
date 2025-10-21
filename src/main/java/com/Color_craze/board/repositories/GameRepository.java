package com.Color_craze.board.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.Color_craze.board.models.GameSession;

import java.util.Optional;

public interface GameRepository extends MongoRepository<GameSession, String> {
    Optional<GameSession> findByCode(String code);
    boolean existsByCode(String code);
}
