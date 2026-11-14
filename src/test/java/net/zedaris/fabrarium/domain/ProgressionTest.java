package net.zedaris.fabrarium.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProgressionTest {
    @Test
    void unlocksResearchAtStableIntegerThresholds() {
        Progression progression = Progression.initial(UUID.randomUUID())
                .addExperience(10)
                .addExperience(20)
                .addExperience(45);

        assertEquals(75, progression.experience());
        assertEquals(3, progression.level());
        assertEquals(3, progression.unlocks().size());
    }

    @Test
    void rejectsNonPositiveExperience() {
        Progression progression = Progression.initial(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> progression.addExperience(0));
    }
}
