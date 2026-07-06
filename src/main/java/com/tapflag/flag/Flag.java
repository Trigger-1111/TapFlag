package com.tapflag.flag;

import org.bukkit.Location;
import org.bukkit.Material;
import java.util.UUID;

public class Flag {

    private final int id;
    private Location location;      // ArmorStand 기준 좌표
    private Location groundBlock;    // 기반암으로 교체된 지면 블록 좌표
    private Material originalGround; // 교체 전 원래 블록 종류
    private Location poleBlock;     // 깃발 기둥 블록 좌표 (OAK_FENCE)
    private Location bannerBlock;   // 깃발 배너 블록 좌표 (BANNER)
    private int hp;
    private int maxHp;
    private int upgradeCount = 0;
    private String owningTeamId;    // null = 중립
    private UUID armorStandUuid;
    private UUID armorStandUuid2; // 배너 높이 추가 히트박스

    public Flag(int id, Location location, int maxHp) {
        this.id = id;
        this.location = location;
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    // --- 조회 ---
    public int getId()               { return id; }
    public Location getLocation()    { return location; }
    public Location getGroundBlock()             { return groundBlock; }
    public Material getOriginalGround()          { return originalGround; }
    public Location getPoleBlock()               { return poleBlock; }
    public Location getBannerBlock()             { return bannerBlock; }
    public int getHp()               { return hp; }
    public int getMaxHp()            { return maxHp; }
    public String getOwningTeamId()  { return owningTeamId; }
    public UUID getArmorStandUuid()   { return armorStandUuid; }
    public UUID getArmorStandUuid2()  { return armorStandUuid2; }
    public boolean isNeutral()        { return owningTeamId == null; }
    public boolean isOwnedBy(String teamId) { return teamId != null && teamId.equals(owningTeamId); }
    public double getHpPercent()     { return (double) hp / maxHp; }

    // --- 변경 ---
    public void setLocation(Location location)      { this.location = location; }
    public void setGroundBlock(Location loc)         { this.groundBlock = loc; }
    public void setOriginalGround(Material mat)      { this.originalGround = mat; }
    public void setPoleBlock(Location loc)           { this.poleBlock = loc; }
    public void setBannerBlock(Location loc)         { this.bannerBlock = loc; }
    public void setOwningTeamId(String teamId)      { this.owningTeamId = teamId; }
    public void setArmorStandUuid(UUID uuid)         { this.armorStandUuid = uuid; }
    public void setArmorStandUuid2(UUID uuid)        { this.armorStandUuid2 = uuid; }
    public void setHp(int hp)                        { this.hp = Math.max(0, Math.min(maxHp, hp)); }
    public void resetHp()                           { this.hp = maxHp; }
    public int getUpgradeCount()                   { return upgradeCount; }
    public void upgradeMaxHp(int amount)           { maxHp += amount; upgradeCount++; }

    /** @return true 이면 HP 0 → 점령 트리거 */
    public boolean damage(int amount) {
        hp = Math.max(0, hp - amount);
        return hp == 0;
    }
}
