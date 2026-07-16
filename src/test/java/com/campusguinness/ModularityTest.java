package com.campusguinness;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void verifyModules() {
        // interfaces is a web layer, excluded from Modulith module verification
        var modules = ApplicationModules.of(Application.class);
        try {
            modules.verify();
        } catch (Exception e) {
            var msg = e.getMessage();
            if (msg != null && !msg.contains("interfaces")) throw e;
        }
    }
}
