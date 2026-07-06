package com.tapflag;

import com.tapflag.flag.Flag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlagTest {

    private Flag flag() { return new Flag(1, null, 100); }

    @Test void damageReducesHp() {
        Flag f = flag();
        f.damage(30);
        assertEquals(70, f.getHp());
    }

    @Test void damageReturnsTrueOnCapture() {
        Flag f = flag();
        assertFalse(f.damage(50));
        assertTrue(f.damage(50));
        assertEquals(0, f.getHp());
    }

    @Test void damageDoesNotGoBelowZero() {
        Flag f = flag();
        f.damage(200);
        assertEquals(0, f.getHp());
    }

    @Test void resetRestoresFullHp() {
        Flag f = flag();
        f.damage(80);
        f.resetHp();
        assertEquals(100, f.getHp());
    }

    @Test void neutralByDefault() {
        assertTrue(flag().isNeutral());
    }

    @Test void ownershipTracking() {
        Flag f = flag();
        f.setOwningTeamId("red");
        assertFalse(f.isNeutral());
        assertTrue(f.isOwnedBy("red"));
        assertFalse(f.isOwnedBy("blue"));
    }

    @Test void hpPercentCorrect() {
        Flag f = flag();
        f.damage(25);
        assertEquals(0.75, f.getHpPercent(), 0.001);
    }
}
