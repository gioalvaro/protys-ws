package org.protys.ws.repository;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.protys.ws.model.StandardIncorporationSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

/** Real Hibernate persistence in a disposable database; never uses the configured PostgreSQL. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:p1-wizard-sessions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.file.name=target/p1-persistence-tests.log",
        "logging.level.org.hibernate.SQL=WARN"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StandardIncorporationSessionRepositoryTest {
    @Autowired StandardIncorporationSessionRepository repository;
    @Autowired TestEntityManager entityManager;

    private StandardIncorporationSession session(String name) {
        var session = new StandardIncorporationSession();
        UUID id = UUID.randomUUID();
        session.setId(id);
        session.setStandardName(name);
        session.setTemporaryGraph("urn:test:temp:" + id);
        session.setPermanentGraph("urn:test:permanent:" + id);
        session.setPhase("UPLOADED");
        session.setStartedAt(LocalDateTime.now());
        return session;
    }

    @Test void rereadRestoresOwnershipSelectionAndVerificationAfterPersistenceContextIsCleared() {
        var session = session("Same standard name");
        String selection = UUID.randomUUID() + "," + UUID.randomUUID();
        session.setSelectedRuleIds(selection);
        session.setConsistencyVerified(true);
        session.setInferenceVerified(true);
        session.setExecutedRulesJson("[]");
        session.setRuleDefinitionsSnapshot("");
        session.setRuleDefinitionsSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        session.setPermanentWriteStarted(true);
        session.setTripleCount(12);
        session.setInferredTriples(3);
        UUID finalId = UUID.randomUUID();
        session.setFinalModuleId(finalId);
        var saved = repository.saveAndFlush(session);
        Long revision = saved.getRevision();
        entityManager.clear();

        var restored = repository.findById(session.getId()).orElseThrow();
        assertEquals(session.getTemporaryGraph(), restored.getTemporaryGraph());
        assertEquals(session.getPermanentGraph(), restored.getPermanentGraph());
        assertEquals(selection, restored.getSelectedRuleIds());
        assertTrue(restored.isConsistencyVerified());
        assertTrue(restored.isInferenceVerified());
        assertEquals("[]", restored.getExecutedRulesJson());
        assertEquals("", restored.getRuleDefinitionsSnapshot());
        assertEquals(session.getRuleDefinitionsSha256(), restored.getRuleDefinitionsSha256());
        assertTrue(restored.isPermanentWriteStarted());
        assertEquals(12, restored.getTripleCount());
        assertEquals(3, restored.getInferredTriples());
        assertEquals(finalId, restored.getFinalModuleId());
        restored.setPhase("COMPLETED");
        var updated = repository.saveAndFlush(restored);
        assertTrue(updated.getRevision() > revision);
    }

    @Test void sameNameSessionsHaveIndependentRecordsAndDeletingOnePreservesTheOther() {
        var a = repository.saveAndFlush(session("Repeated name"));
        var b = repository.saveAndFlush(session("Repeated name"));
        assertNotEquals(a.getTemporaryGraph(), b.getTemporaryGraph());
        assertNotEquals(a.getPermanentGraph(), b.getPermanentGraph());
        repository.deleteById(a.getId());
        repository.flush();
        entityManager.clear();
        assertTrue(repository.findById(a.getId()).isEmpty());
        assertEquals(b.getTemporaryGraph(), repository.findById(b.getId()).orElseThrow().getTemporaryGraph());
    }

    @Test void duplicateTemporaryGraphCannotBeOwnedByAnotherSession() {
        var a = repository.saveAndFlush(session("A"));
        var b = session("B");
        b.setTemporaryGraph(a.getTemporaryGraph());
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(b));
    }

    @Test void duplicatePermanentGraphCannotBeOwnedByAnotherSession() {
        var a = repository.saveAndFlush(session("A"));
        var b = session("B");
        b.setPermanentGraph(a.getPermanentGraph());
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(b));
    }
}
