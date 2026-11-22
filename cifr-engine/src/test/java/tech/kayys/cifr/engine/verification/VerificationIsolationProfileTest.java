package tech.kayys.cifr.engine.verification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VerificationIsolationProfileTest {

    @Test
    void untrustedProfileDoesNotSatisfyStrictRequirements() {
        assertFalse(VerificationIsolationProfile.untrusted()
                .satisfiesStrictVerificationRequirements());
    }

    @Test
    void everyRequiredControlMustBeEnforced() {
        VerificationIsolationProfile complete = new VerificationIsolationProfile(
                true, true, true, true, true,
                true, true, true, true, true);
        assertTrue(complete.satisfiesStrictVerificationRequirements());

        VerificationIsolationProfile missingNetworkIsolation = new VerificationIsolationProfile(
                true, true, false, true, true,
                true, true, true, true, true);
        assertFalse(missingNetworkIsolation.satisfiesStrictVerificationRequirements());

        VerificationIsolationProfile missingWorkspaceBoundary = new VerificationIsolationProfile(
                true, false, true, true, true,
                true, true, true, true, true);
        assertFalse(missingWorkspaceBoundary.satisfiesStrictVerificationRequirements());
    }
}
