package com.tapflag;

import com.tapflag.team.Team;
import com.tapflag.team.TeamState;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TeamTest {

    private UUID uuid() { return UUID.randomUUID(); }

    @Test void startsActive() {
        Team t = new Team("red", uuid());
        assertEquals(TeamState.ACTIVE, t.getState());
        assertTrue(t.isActive());
    }

    @Test void leaderAutoAdded() {
        UUID leader = uuid();
        Team t = new Team("red", leader);
        assertTrue(t.containsPlayer(leader));
        assertEquals(1, t.getMembers().size());
    }

    @Test void memberManagement() {
        Team t = new Team("red", uuid());
        UUID member = uuid();
        t.addMember(member);
        assertTrue(t.containsPlayer(member));
        t.removeMember(member);
        assertFalse(t.containsPlayer(member));
    }

    @Test void flagTracking() {
        Team t = new Team("red", uuid());
        t.addFlag(1);
        t.addFlag(2);
        assertEquals(2, t.getFlagCount());
        assertTrue(t.ownsFlag(1));
        t.removeFlag(1);
        assertFalse(t.ownsFlag(1));
        assertEquals(1, t.getFlagCount());
    }

    @Test void stateTransitionToDisband() {
        Team t = new Team("red", uuid());
        t.setState(TeamState.DISBANDED);
        assertFalse(t.isActive());
    }
}
