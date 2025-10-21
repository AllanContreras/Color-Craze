package com.Color_craze.auth.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.Color_craze.auth.models.AuthUser;

public interface AuthRepository extends MongoRepository<AuthUser, String> {

    // Busca un usuario por su email
    Optional<AuthUser> findByEmail(String email);

    // Verifica si existe un usuario con ese email
    boolean existsByEmail(String email);
}
