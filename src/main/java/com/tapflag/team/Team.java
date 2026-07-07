package com.tapflag.team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 순수 데이터 모델 — Bukkit 의존성 없음, JUnit 테스트 가능
 */
public class Team {

    private final String id;
    private UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private TeamState state = TeamState.ACTIVE;
    private final Set<Integer> ownedFlagIds = new HashSet<>();
    private int point     = 0;   // 상점 재화 (구 금화)
    private int flagpoint = 0;   // 업그레이드 재화

    /** 점령 단계 중 소유권이 변경된 깃발 — 세션 임시값, 영속하지 않음 */
    private final Set<Integer> capturePhaseChangedFlags = new HashSet<>();

    public Team(String id, UUID leader) {
        this.id = id;
        this.leader = leader;
        this.members.add(leader);
    }

    // --- 기본 조회 ---
    public String getId()                  { return id; }
    public UUID getLeader()               { return leader; }
    public void setLeader(UUID uuid)      { leader = uuid; members.add(uuid); }
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

    // --- 포인트 (상점 재화) ---
    public int  getPoint()              { return point; }
    public void addPoint(int amount)    { point = Math.max(0, point + amount); }
    public void resetPoint()            { point = 0; }

    // --- 플래그포인트 (업그레이드 재화) ---
    public int  getFlagpoint()          { return flagpoint; }
    public void addFlagpoint(int amount){ flagpoint = Math.max(0, flagpoint + amount); }

    // --- 안정 점령 추적 (점령 단계 중 소유권 변경) ---
    public void markFlagChanged(int flagId)   { capturePhaseChangedFlags.add(flagId); }
    public void clearCapturePhaseChanges()    { capturePhaseChangedFlags.clear(); }

    /** 점령 단계 종료 시 안정적으로 보유한 깃발 수 (변경 없던 것만) */
    public int getStableFlagCount() {
        return (int) ownedFlagIds.stream()
            .filter(id -> !capturePhaseChangedFlags.contains(id)).count();
    }
}
