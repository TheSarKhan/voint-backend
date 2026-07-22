package com.starsoft.voint.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PanelUserRepository extends JpaRepository<PanelUser, UUID> {

    Optional<PanelUser> findByEmail(String email);
}
