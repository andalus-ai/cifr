package tech.kayys.aljabr.engine.capability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.action.ActionProposal;
import tech.kayys.aljabr.engine.action.ActionTarget;
import tech.kayys.aljabr.engine.action.ActionType;
import tech.kayys.aljabr.engine.action.DefaultActionValidator;
import tech.kayys.aljabr.engine.action.DefaultEvidenceExtractor;
import tech.kayys.aljabr.engine.action.Observation;
import tech.kayys.aljabr.engine.action.ValidationResult;
import tech.kayys.aljabr.engine.store.Evidence;
import tech.kayys.aljabr.engine.store.EvidenceType;
import tech.kayys.aljabr.engine.task.TaskId;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityAndActionValidationTest {

    private DefaultCapabilityRegistry registry;
    private DefaultActionValidator validator;
    private DefaultEvidenceExtractor extractor;

    @BeforeEach
    void setUp() {
        registry = new DefaultCapabilityRegistry();
        validator = new DefaultActionValidator();
        extractor = new DefaultEvidenceExtractor();
    }

    @Test
    void testCapabilityRegistrationAndLookup() {
        Optional<Capability> cap = registry.find("filesystem.read");
        assertTrue(cap.isPresent());
        assertEquals(CapabilityCategory.READ, cap.get().category());
        assertEquals(CapabilityRisk.SAFE, cap.get().risk());
    }

    @Test
    void testActionProposalValidation() {
        ActionProposal validProposal = ActionProposal.of(
            "prop-1",
            TaskId.of("task-1"),
            ActionType.READ,
            ActionTarget.file("src/Auth.java"),
            "Inspect auth"
        );

        Capability cap = registry.find("filesystem.read").orElseThrow();
        ValidationResult res = validator.validate(validProposal, cap, null);

        assertTrue(res.isValid());
        assertTrue(res.errors().isEmpty());
    }

    @Test
    void testEvidenceExtractionFromObservation() {
        Observation obs = Observation.of(0, "Build Succeeded", "");
        Evidence ev = extractor.extract("exec-101", obs);

        assertNotNull(ev);
        assertEquals(EvidenceType.TOOL_RESULT, ev.type());
        assertEquals("Observation exitCode 0", ev.payload().summary());
    }
}
