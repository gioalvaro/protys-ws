package org.protys.ws.repository;

import org.protys.ws.model.StandardIncorporationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface StandardIncorporationSessionRepository extends JpaRepository<StandardIncorporationSession, UUID> {
}
