package tech.kayys.cifr.engine.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EnterpriseAuditLedgerTest {

    private EnterpriseAuditLedger ledger;

    @BeforeEach
    public void setup() {
        ledger = new EnterpriseAuditLedger();
        ledger.init();
    }

    @Test
    public void testAuditChainIntegrity() {
        EnterpriseAuditLedger.AuditEntry e1 = ledger.log(
            "trace-1", "user@enterprise.internal", "TOOL_INVOKE", "BashTool", "SUCCESS", 120, Map.of("cmd", "mvn compile")
        );

        EnterpriseAuditLedger.AuditEntry e2 = ledger.log(
            "trace-1", "agent-coder", "FILE_PATCH", "Main.java", "SUCCESS", 45, Map.of("lines", 12)
        );

        assertNotNull(e1.currentHash());
        assertNotNull(e2.currentHash());
        assertEquals(e1.currentHash(), e2.prevHash(), "Chained hash must match previous current hash");

        boolean isIntegrityValid = ledger.verifyLedgerIntegrity();
        assertTrue(isIntegrityValid, "Cryptographic audit chain must verify as valid");
    }
}
