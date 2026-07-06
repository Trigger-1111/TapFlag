package com.tapflag.team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 순수 데이터 모델 — Bukkit 의존성 없음, JUnit 테스트 가능
 */
public class Team {

    private final String id;
    private final UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private TeamState state = TeamState.ACTIVE;
    private final Set<Integer> ownedFlagIds = new HashSet<>();
    private int gold = 0;

    public Team(String id, UUID leader) {
        this.id = id;
        this.leader = leader;
        this.members.add(leader);
    }

    // --- 기본 조회 ---
    public String getId()                  { return id; }
    public UUID getLeader()               { return leader; }
    public Set<UUID> getMembers()         { return members; }
    public TeamState getState()           { return state; }
    public Set<Integer> getOwnedFlagIds() { return ownedFlagIds; }
    public boolean isActive()             { return state == TeamState.ACTIVE; }

    // --- 멤버 관리 ---
    public void addMember(UUID uuid)    { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public boolean containsPlayer(UUID uuid) { return members.contains(uuid); }

    // --- 상태 ---
    public void setState(TeamState state) { this.state = state; }

    // --- 깃발 관리 ---
    public void addFlag(int flagId)    { ownedFlagIds.add(flagId); }
    public void removeFlag(int flagId) { ownedFlagIds.remove(flagId); }
    public boolean ownsFlag(int flagId){ return ownedFlagIds.contains(flagId); }
    public int getFlagCount()          { return ownedFlagIds.size(); }

    // --- 금화 ---
    public int  getGold()              { return gold; }
    public void addGold(int amount)    { gold = Math.max(0, gold + amount); }
    public void resetGold()            { gold = 0; }
}
