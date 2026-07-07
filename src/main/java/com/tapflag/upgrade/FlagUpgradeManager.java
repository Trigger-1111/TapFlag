package com.tapflag.upgrade;

import com.tapflag.TapFlagPlugin;
import com.tapflag.flag.Flag;
import com.tapflag.flag.FlagManager;
import com.tapflag.flag.FlagZoneDisplay;
import com.tapflag.team.Team;
import com.tapflag.team.TeamManager;
import com.tapflag.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 깃발 업그레이드 시스템 통합 관리자.
 *  - 업그레이드 레벨 저장/로드
 *  - 내구력: 보호막 ArmorStand
 *  - 요새화: 발리스타 터렛 ArmorStand
 *  - 생산량: 경작지/동물 한도 검사
 *  - 지원: 영역 내 아군 포션 효과
 *  - 수리: 깃발 HP 회복 + MaxHP 증가
 */
public class FlagUpgradeManager extends BukkitRunnable implements Listener {

    // ─── 상수 ────────────────────────────────────────────────────────────────

    /** 보호막 HP = 레벨 × 이 값 */
    private static final int SHIELD_HP_PER_LEVEL = 250;
    /** 수리 레벨 1당 추가 maxHP */
    private static final int REPAIR_MAX_HP_PER_LEVEL = 50;
    /** 수리 1회당 회복량 (레벨 × 이 값, 20초마다) */
    private static final int REPAIR_REGEN_PER_LEVEL = 2;

    private static final int[] PROD_MAX_FARMLAND = {0, 20, 40, 60, 80, 100};
    private static final int[] PROD_MAX_ANIMALS  = {0,  4,  8, 12, 16,  20};

    /** 터렛 배치 오프셋 (깃발 중심 기준, X/Z) */
    private static final int[][] TURRET_OFFSETS = {
        { 3, 0}, {-3, 0}, { 0, 3}, { 0,-3}, { 2, 2}
    };
    private static final int BALLISTA_HP    = 300;
    private static final int BALLISTA_RANGE = 40;

    // ─── PDC 키 ───────────────────────────────────────────────────────────────
    private final NamespacedKey SHIELD_KEY;
    private final NamespacedKey BALLISTA_KEY;
    private final NamespacedKey BALLISTA_TEAM_KEY;

    // ─── 의존성 ───────────────────────────────────────────────────────────────
    private final TapFlagPlugin plugin;
    private final FlagManager   flagManager;
    private final TeamManager   teamManager;

    // ─── 업그레이드 데이터 ─────────────────────────────────────────────────────
    // teamId → flagId → UpgradeType → level
    private final Map<String, Map<Integer, EnumMap<UpgradeType, Integer>>> upgrades = new HashMap<>();

    // ─── 엔티티 상태 ──────────────────────────────────────────────────────────
    private final Map<Integer, UUID>        shieldEntities  = new HashMap<>();
    private final Map<Integer, Integer>     shieldCurrentHp = new HashMap<>();
    private final Map<Integer, Integer>     shieldMaxHp     = new HashMap<>();
    private final Map<Integer, List<UUID>>  ballistaTurrets = new HashMap<>();
    private final Map<UUID, Integer>        ballistaHp      = new HashMap<>();
    private final Map<UUID, String>         ballistaTeamId  = new HashMap<>();
    private final Map<UUID, Integer>        ballistaFlagId  = new HashMap<>();
    /** 터렛 UUID → TURRET_OFFSETS 슬롯 인덱스 (재업글 시 빈 슬롯 탐색에 사용) */
    private final Map<UUID, Integer>        ballistaIndex   = new HashMap<>();

    private int tick = 0;

    private final File upgradesFile;

    // ─── 생성자 ──────────────────────────────────────────────────────────────

    public FlagUpgradeManager(TapFlagPlugin plugin, FlagManager flagManager, TeamManager teamManager) {
        this.plugin      = plugin;
        this.flagManager = flagManager;
        this.teamManager = teamManager;
        this.upgradesFile = new File(plugin.getDataFolder(), "upgrades.yml");
        SHIELD_KEY       = new NamespacedKey(plugin, "shield_flag");
        BALLISTA_KEY     = new NamespacedKey(plugin, "ballista_flag");
        BALLISTA_TEAM_KEY= new NamespacedKey(plugin, "ballista_team");

        load();
        // flags.yml 로드 완료 후 엔티티 복원 (5틱 대기)
        new BukkitRunnable() {
            @Override public void run() { restoreEntities(); }
        }.runTaskLater(plugin, 10L);
    }

    // ─── BukkitRunnable (1초마다) ─────────────────────────────────────────────

    @Override
    public void run() {
        tick++;
        if (tick % 5  == 0) applySupportBuffs();
        if (tick % 20 == 0) applyRepairEffects();
        if (tick % 2  == 0) runBallistas();
    }

    // ─── 업그레이드 조회 ──────────────────────────────────────────────────────

    public int getLevel(String teamId, int flagId, UpgradeType type) {
        var byTeam = upgrades.get(teamId);
        if (byTeam == null) return 0;
        var byFlag = byTeam.get(flagId);
        if (byFlag == null) return 0;
        return byFlag.getOrDefault(type, 0);
    }

    // ─── 업그레이드 실행 ──────────────────────────────────────────────────────

    /**
     * 플레이어가 업그레이드 버튼을 눌렀을 때 호출.
     * @return null = 성공, 오류 문자열 = 실패 이유
     */
    public String upgrade(Entity actor, int flagId, UpgradeType type) {
        if (!(actor instanceof Player player)) return "플레이어만 업그레이드 가능";
        Team team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) return "팀에 속해야 합니다.";

        Flag flag = flagManager.getFlagById(flagId);
        if (flag == null || !flag.isOwnedBy(team.getId())) return "자신의 깃발에서만 업그레이드 가능";

        int current = getLevel(team.getId(), flagId, type);
        if (current >= UpgradeType.MAX_LEVEL) return type.displayName + "는 이미 최고 레벨입니다.";
        if (team.getFlagpoint() < UpgradeType.FP_COST)
            return "플래그포인트 부족 (필요: " + UpgradeType.FP_COST + ", 보유: " + team.getFlagpoint() + ")";

        team.addFlagpoint(-UpgradeType.FP_COST);
        setLevel(team.getId(), flagId, type, current + 1);
        int newLevel = current + 1;

        // 효과 즉시 적용
        applyUpgradeEffect(flag, team.getId(), type, newLevel);

        teamManager.save();
        save();
        return null;
    }

    private void setLevel(String teamId, int flagId, UpgradeType type, int level) {
        upgrades.computeIfAbsent(teamId, k -> new HashMap<>())
                .computeIfAbsent(flagId, k -> new EnumMap<>(UpgradeType.class))
                .put(type, level);
    }

    private void applyUpgradeEffect(Flag flag, String teamId, UpgradeType type, int level) {
        switch (type) {
            case DURABILITY -> {
                int maxHp = level * SHIELD_HP_PER_LEVEL;
                shieldMaxHp.put(flag.getId(), maxHp);
                shieldCurrentHp.put(flag.getId(), maxHp);
                spawnShield(flag, teamId);
            }
            case FORTIFICATION -> syncBallistaTurrets(flag, teamId, level);
            case REPAIR -> {
                int baseHp = flag.getBaseMaxHp();
                flag.setMaxHp(baseHp + level * REPAIR_MAX_HP_PER_LEVEL);
            }
            default -> {} // PRODUCTION, SUPPORT — 주기 태스크에서 처리
        }
    }

    // ─── 깃발 점령 시 업그레이드 초기화 ───────────────────────────────────────

    public void clearFlagUpgrades(int flagId, String prevTeamId) {
        // 물리 엔티티 제거
        removeShield(flagId);
        removeAllTurrets(flagId);

        // 레벨 데이터 삭제
        if (prevTeamId != null) {
            var byTeam = upgrades.get(prevTeamId);
            if (byTeam != null) byTeam.remove(flagId);
        }

        // 깃발 MaxHP 원래대로 복원
        Flag flag = flagManager.getFlagById(flagId);
        if (flag != null) flag.setMaxHp(flag.getBaseMaxHp());

        save();
    }

    // ─── 게임 종료 시 플래그포인트 지급 ───────────────────────────────────────

    public void distributeFlagpoints() {
        for (Team team : teamManager.getAllTeams()) {
            int stable = team.getStableFlagCount();
            if (stable > 0) {
                team.addFlagpoint(stable);
                plugin.getServer().broadcastMessage(
                    MessageUtil.success("[" + team.getId() + "] 안정 점령 " + stable + "개 → 플래그포인트 +" + stable)
                );
            }
            team.clearCapturePhaseChanges();
        }
        teamManager.save();
    }

    // ─── 보호막 (내구력) ─────────────────────────────────────────────────────

    private void spawnShield(Flag flag, String teamId) {
        removeShield(flag.getId());

        Location loc = flag.getLocation().clone().add(0, 3, 0);
        loc.getChunk().load();
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(false);
        stand.setArms(false);
        stand.setBasePlate(false);
        stand.setCustomNameVisible(true);
        stand.getPersistentDataContainer().set(SHIELD_KEY, PersistentDataType.INTEGER, flag.getId());
        stand.getPersistentDataContainer().set(BALLISTA_TEAM_KEY, PersistentDataType.STRING, teamId);

        updateShieldName(stand, flag.getId());
        shieldEntities.put(flag.getId(), stand.getUniqueId());
    }

    private void updateShieldName(ArmorStand stand, int flagId) {
        int cur = shieldCurrentHp.getOrDefault(flagId, 0);
        int max = shieldMaxHp.getOrDefault(flagId, 0);
        stand.setCustomName(ChatColor.AQUA + "⚔ 보호막 " + cur + "/" + max);
    }

    private void removeShield(int flagId) {
        UUID uuid = shieldEntities.remove(flagId);
        if (uuid == null) return;
        for (World w : plugin.getServer().getWorlds()) {
            Entity e = w.getEntity(uuid);
            if (e != null) { e.remove(); break; }
        }
        shieldCurrentHp.remove(flagId);
        shieldMaxHp.remove(flagId);
    }

    public boolean hasActiveShield(int flagId) {
        return shieldEntities.containsKey(flagId) && shieldCurrentHp.getOrDefault(flagId, 0) > 0;
    }

    public int getShieldCurrentHp(int flagId) { return shieldCurrentHp.getOrDefault(flagId, 0); }
    public int getShieldMaxHp(int flagId)     { return shieldMaxHp.getOrDefault(flagId, 0); }

    private void damageShield(int flagId, int damage, Player attacker) {
        int cur = shieldCurrentHp.getOrDefault(flagId, 0) - damage;
        if (cur <= 0) {
            // 영구 파괴: 엔티티 제거 + 내구력 레벨 초기화 → 처음부터 재업그레이드 필요
            Flag flag = flagManager.getFlagById(flagId);
            String teamId = flag != null ? flag.getOwningTeamId() : null;
            removeShield(flagId);
            if (teamId != null) {
                setLevel(teamId, flagId, UpgradeType.DURABILITY, 0);
                save();
                notifyTeam(teamId, MessageUtil.warn(
                    "깃발 [" + FlagManager.getDisplayName(flagId) + "] 보호막 완전 파괴! 다시 업그레이드하세요."));
            }
            plugin.getServer().broadcastMessage(
                MessageUtil.warn("깃발 [" + FlagManager.getDisplayName(flagId) + "] 보호막이 파괴되었습니다!"));
            if (attacker != null) attacker.sendMessage(MessageUtil.success("보호막 완전 파괴!"));
        } else {
            shieldCurrentHp.put(flagId, cur);
            UUID uuid = shieldEntities.get(flagId);
            if (uuid != null) {
                ArmorStand s = findArmorStand(uuid);
                if (s != null) updateShieldName(s, flagId);
            }
            if (attacker != null) {
                attacker.sendMessage(ChatColor.AQUA + "보호막 HP: " + cur + "/" + shieldMaxHp.getOrDefault(flagId, 0));
            }
        }
    }

    // ─── 발리스타 터렛 (요새화) ───────────────────────────────────────────────

    private void syncBallistaTurrets(Flag flag, String teamId, int level) {
        List<UUID> turrets = ballistaTurrets.computeIfAbsent(flag.getId(), k -> new ArrayList<>());
        int current = turrets.size();

        if (level > current) {
            // 이미 사용 중인 슬롯 수집 (파괴 후 빈 슬롯에만 배치)
            Set<Integer> usedSlots = new HashSet<>();
            for (UUID uuid : turrets) {
                Integer idx = ballistaIndex.get(uuid);
                if (idx != null) usedSlots.add(idx);
            }
            int spawned = 0;
            for (int i = 0; i < TURRET_OFFSETS.length && spawned < (level - current); i++) {
                if (!usedSlots.contains(i)) {
                    spawnTurret(flag, teamId, i);
                    spawned++;
                }
            }
        } else if (level < current) {
            while (turrets.size() > level) {
                UUID uuid = turrets.remove(turrets.size() - 1);
                removeTurretEntity(uuid);
            }
        }
    }

    private void spawnTurret(Flag flag, String teamId, int slotIndex) {
        if (slotIndex >= TURRET_OFFSETS.length) return;
        int[] off = TURRET_OFFSETS[slotIndex];
        Location center = flag.getLocation();
        World world = center.getWorld();
        int x = center.getBlockX() + off[0];
        int z = center.getBlockZ() + off[1];
        world.getChunkAt(x >> 4, z >> 4).load();
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        // 깃발 중심에서 바깥쪽을 바라보도록 yaw 설정
        float yaw = (float) Math.toDegrees(Math.atan2(-off[0], off[1]));
        Location loc = new Location(world, x + 0.5, y, z + 0.5, yaw, 0);

        ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(true);
        stand.setGravity(false);
        stand.setInvulnerable(false);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setCustomNameVisible(true);
        // 석궁을 겨누는 포즈
        stand.setRightArmPose(new EulerAngle(-1.2, 0, 0.2));
        stand.setLeftArmPose(new EulerAngle(-0.5, 0, 0.5));
        stand.getEquipment().setItemInMainHand(new ItemStack(Material.CROSSBOW));
        stand.getPersistentDataContainer().set(BALLISTA_KEY, PersistentDataType.INTEGER, flag.getId());
        stand.getPersistentDataContainer().set(BALLISTA_TEAM_KEY, PersistentDataType.STRING, teamId);
        updateTurretName(stand, BALLISTA_HP);

        UUID uuid = stand.getUniqueId();
        ballistaTurrets.computeIfAbsent(flag.getId(), k -> new ArrayList<>()).add(uuid);
        ballistaHp.put(uuid, BALLISTA_HP);
        ballistaTeamId.put(uuid, teamId);
        ballistaFlagId.put(uuid, flag.getId());
        ballistaIndex.put(uuid, slotIndex);
    }

    private void updateTurretName(ArmorStand stand, int hp) {
        stand.setCustomName(ChatColor.RED + "⚙ 발리스타 HP: " + hp + "/" + BALLISTA_HP);
    }

    private void removeTurretEntity(UUID uuid) {
        for (World w : plugin.getServer().getWorlds()) {
            Entity e = w.getEntity(uuid);
            if (e != null) { e.remove(); break; }
        }
        ballistaHp.remove(uuid);
        ballistaTeamId.remove(uuid);
        ballistaFlagId.remove(uuid);
        ballistaIndex.remove(uuid);
    }

    private void removeAllTurrets(int flagId) {
        List<UUID> turrets = ballistaTurrets.remove(flagId);
        if (turrets == null) return;
        for (UUID uuid : turrets) removeTurretEntity(uuid);
    }

    private void runBallistas() {
        for (var entry : ballistaTurrets.entrySet()) {
            int flagId = entry.getKey();
            Flag flag = flagManager.getFlagById(flagId);
            if (flag == null || flag.isNeutral()) continue;
            String ownerTeam = flag.getOwningTeamId();

            for (UUID uuid : new ArrayList<>(entry.getValue())) {
                if (ballistaHp.getOrDefault(uuid, 0) <= 0) continue;
                ArmorStand stand = findArmorStand(uuid);
                if (stand == null) continue;

                Player target = findNearestEnemy(stand.getLocation(), ownerTeam, BALLISTA_RANGE);
                if (target == null) continue;

                Vector dir = target.getLocation().add(0, 1, 0)
                    .toVector().subtract(stand.getLocation().toVector()).normalize();
                Arrow arrow = stand.getWorld().spawnArrow(
                    stand.getLocation().add(0, 1.5, 0), dir, 1.6f, 1.0f);
                arrow.setDamage(4.0);
                arrow.setShooter(stand);
                arrow.getPersistentDataContainer().set(BALLISTA_TEAM_KEY, PersistentDataType.STRING, ownerTeam);
            }
        }
    }

    private ArmorStand findArmorStand(UUID uuid) {
        for (World w : plugin.getServer().getWorlds()) {
            Entity e = w.getEntity(uuid);
            if (e instanceof ArmorStand s) return s;
        }
        return null;
    }

    private Player findNearestEnemy(Location origin, String ownerTeam, double range) {
        Player nearest = null;
        double bestDist = range * range;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.getWorld().equals(origin.getWorld())) continue;
            Team t = teamManager.getTeamByPlayer(p.getUniqueId());
            if (t != null && t.getId().equals(ownerTeam)) continue; // 아군 제외
            double d = p.getLocation().distanceSquared(origin);
            if (d < bestDist) { bestDist = d; nearest = p; }
        }
        return nearest;
    }

    // ─── 지원 (Support) ──────────────────────────────────────────────────────

    private void applySupportBuffs() {
        double zone = FlagZoneDisplay.ZONE_HALF;
        for (Flag flag : flagManager.getAllFlags().values()) {
            if (flag.isNeutral()) continue;
            String teamId = flag.getOwningTeamId();
            int level = getLevel(teamId, flag.getId(), UpgradeType.SUPPORT);
            if (level == 0) continue;

            Location center = flag.getLocation();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!p.getWorld().equals(center.getWorld())) continue;
                Team t = teamManager.getTeamByPlayer(p.getUniqueId());
                if (t == null || !t.getId().equals(teamId)) continue;
                double dx = Math.abs(p.getLocation().getX() - center.getX());
                double dz = Math.abs(p.getLocation().getZ() - center.getZ());
                if (dx > zone || dz > zone) continue;

                applyBuff(p, PotionEffectType.SPEED, 1, 7 * 20);   // 신속 II (레벨 1부터)
                if (level >= 3) applyBuff(p, PotionEffectType.REGENERATION, 0, 7 * 20);
                if (level >= 5) applyBuff(p, PotionEffectType.ABSORPTION, 0, 7 * 20);
            }
        }
    }

    private void applyBuff(Player p, PotionEffectType type, int amplifier, int duration) {
        p.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false));
    }

    // ─── 수리 (Repair) ───────────────────────────────────────────────────────

    private void applyRepairEffects() {
        for (Flag flag : flagManager.getAllFlags().values()) {
            if (flag.isNeutral()) continue;
            int level = getLevel(flag.getOwningTeamId(), flag.getId(), UpgradeType.REPAIR);
            if (level == 0) continue;

            int regen = level * REPAIR_REGEN_PER_LEVEL;
            flag.setHp(Math.min(flag.getMaxHp(), flag.getHp() + regen));

            // 보호막 회복
            if (shieldCurrentHp.containsKey(flag.getId())) {
                int cur = shieldCurrentHp.get(flag.getId());
                int max = shieldMaxHp.getOrDefault(flag.getId(), 0);
                if (cur < max) {
                    int newHp = Math.min(max, cur + regen);
                    shieldCurrentHp.put(flag.getId(), newHp);
                    UUID uuid = shieldEntities.get(flag.getId());
                    if (uuid != null) {
                        ArmorStand s = findArmorStand(uuid);
                        if (s != null) updateShieldName(s, flag.getId());
                    }
                }
            }

            // 발리스타 회복
            List<UUID> turrets = ballistaTurrets.get(flag.getId());
            if (turrets != null) {
                for (UUID uuid : turrets) {
                    int cur = ballistaHp.getOrDefault(uuid, 0);
                    if (cur > 0 && cur < BALLISTA_HP) {
                        int newHp = Math.min(BALLISTA_HP, cur + regen);
                        ballistaHp.put(uuid, newHp);
                        ArmorStand s = findArmorStand(uuid);
                        if (s != null) updateTurretName(s, newHp);
                    }
                }
            }
        }
    }

    // ─── 생산량 (Production) — 외부 호출용 ───────────────────────────────────

    /** BuildListener에서 호출: 경작지 설치 허용 여부 */
    public boolean canPlaceFarmland(Location loc) {
        FlagZoneInfo info = getFlagZoneInfo(loc);
        if (info == null) return true; // 영역 밖 — 제한 없음
        int level = getLevel(info.teamId(), info.flagId(), UpgradeType.PRODUCTION);
        int max = PROD_MAX_FARMLAND[level];
        if (max == 0) return false;
        return countMaterialInZone(info.flagCenter(), Material.FARMLAND) < max;
    }

    /** CreatureSpawnEvent에서 호출: 동물 추가 허용 여부 */
    public boolean canSpawnAnimal(Location loc) {
        FlagZoneInfo info = getFlagZoneInfo(loc);
        if (info == null) return true;
        int level = getLevel(info.teamId(), info.flagId(), UpgradeType.PRODUCTION);
        int max = PROD_MAX_ANIMALS[level];
        if (max == 0) return false;
        return countAnimalsInZone(info.flagCenter()) < max;
    }

    private record FlagZoneInfo(int flagId, String teamId, Location flagCenter) {}

    private FlagZoneInfo getFlagZoneInfo(Location loc) {
        double zone = FlagZoneDisplay.ZONE_HALF;
        for (Flag flag : flagManager.getAllFlags().values()) {
            if (flag.isNeutral()) continue;
            Location center = flag.getLocation();
            if (!center.getWorld().equals(loc.getWorld())) continue;
            double dx = Math.abs(loc.getX() - center.getX());
            double dz = Math.abs(loc.getZ() - center.getZ());
            if (dx <= zone && dz <= zone) {
                return new FlagZoneInfo(flag.getId(), flag.getOwningTeamId(), center);
            }
        }
        return null;
    }

    private int countMaterialInZone(Location center, Material mat) {
        int count = 0;
        int zone = (int) FlagZoneDisplay.ZONE_HALF;
        World world = center.getWorld();
        for (int dx = -zone; dx <= zone; dx++) {
            for (int dz = -zone; dz <= zone; dz++) {
                for (int dy = -5; dy <= 5; dy++) {
                    if (world.getBlockAt(
                            center.getBlockX() + dx,
                            center.getBlockY() + dy,
                            center.getBlockZ() + dz).getType() == mat) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int countAnimalsInZone(Location center) {
        double zone = FlagZoneDisplay.ZONE_HALF;
        int count = 0;
        for (Entity e : center.getWorld().getNearbyEntities(center, zone, 10, zone)) {
            if (e instanceof Animals) count++;
        }
        return count;
    }

    // ─── 이벤트 핸들러 ────────────────────────────────────────────────────────

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 1. 발리스타 화살 아군 피해 방지
        if (event.getDamager() instanceof Arrow arrow && event.getEntity() instanceof Player victim) {
            String team = arrow.getPersistentDataContainer()
                .get(BALLISTA_TEAM_KEY, PersistentDataType.STRING);
            if (team != null) {
                Team vt = teamManager.getTeamByPlayer(victim.getUniqueId());
                if (vt != null && vt.getId().equals(team)) event.setCancelled(true);
            }
        }

        // 2. 보호막 ArmorStand 피격
        if (event.getEntity() instanceof ArmorStand stand) {
            Integer flagId = stand.getPersistentDataContainer()
                .get(SHIELD_KEY, PersistentDataType.INTEGER);
            if (flagId != null) {
                event.setCancelled(true);
                if (!(event.getDamager() instanceof Player attacker)) return;
                // 아군은 보호막 피해 불가
                String ownerTeam = stand.getPersistentDataContainer()
                    .get(BALLISTA_TEAM_KEY, PersistentDataType.STRING);
                Team at = teamManager.getTeamByPlayer(attacker.getUniqueId());
                if (at != null && ownerTeam != null && at.getId().equals(ownerTeam)) return;

                int dmg = weaponDamage(attacker);
                damageShield(flagId, dmg, attacker);
                return;
            }

            // 3. 발리스타 터렛 피격
            Integer bFlagId = stand.getPersistentDataContainer()
                .get(BALLISTA_KEY, PersistentDataType.INTEGER);
            if (bFlagId != null) {
                event.setCancelled(true);
                if (!(event.getDamager() instanceof Player attacker)) return;
                // 아군은 터렛 피해 불가
                String ownerTeam = stand.getPersistentDataContainer()
                    .get(BALLISTA_TEAM_KEY, PersistentDataType.STRING);
                Team at = teamManager.getTeamByPlayer(attacker.getUniqueId());
                if (at != null && ownerTeam != null && at.getId().equals(ownerTeam)) return;

                UUID uuid = stand.getUniqueId();
                int dmg = weaponDamage(attacker);
                int cur = ballistaHp.getOrDefault(uuid, 0) - dmg;
                if (cur <= 0) {
                    // 영구 파괴: 엔티티 제거 + 요새화 레벨 -1 → 재업그레이드 필요
                    String ownerTeamForLevel = ballistaTeamId.get(uuid);
                    List<UUID> turretList = ballistaTurrets.get(bFlagId);
                    if (turretList != null) turretList.remove(uuid);
                    removeTurretEntity(uuid);
                    if (ownerTeamForLevel != null) {
                        int curLevel = getLevel(ownerTeamForLevel, bFlagId, UpgradeType.FORTIFICATION);
                        int newLevel = Math.max(0, curLevel - 1);
                        setLevel(ownerTeamForLevel, bFlagId, UpgradeType.FORTIFICATION, newLevel);
                        save();
                        notifyTeam(ownerTeamForLevel, MessageUtil.warn(
                            "발리스타 파괴! 요새화 " + curLevel + "→" + newLevel + " (재업그레이드 필요)"));
                    }
                    attacker.sendMessage(MessageUtil.success("발리스타 파괴!"));
                } else {
                    ballistaHp.put(uuid, cur);
                    updateTurretName(stand, cur);
                    attacker.sendMessage(ChatColor.RED + "발리스타 HP: " + cur + "/" + BALLISTA_HP);
                }
            }
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Animals)) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BREEDING
            && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return;
        if (!canSpawnAnimal(event.getLocation())) {
            event.setCancelled(true);
            // 플레이어에게 메시지 (breeding이면 주변 플레이어에게)
        }
    }

    // ─── 엔티티 복원 (서버 재시작 후) ────────────────────────────────────────

    private void restoreEntities() {
        for (Flag flag : flagManager.getAllFlags().values()) {
            if (flag.isNeutral()) continue;
            String teamId = flag.getOwningTeamId();

            int durLevel = getLevel(teamId, flag.getId(), UpgradeType.DURABILITY);
            if (durLevel > 0) {
                shieldMaxHp.put(flag.getId(), durLevel * SHIELD_HP_PER_LEVEL);
                shieldCurrentHp.put(flag.getId(), durLevel * SHIELD_HP_PER_LEVEL);
                spawnShield(flag, teamId);
            }

            int fortLevel = getLevel(teamId, flag.getId(), UpgradeType.FORTIFICATION);
            if (fortLevel > 0) {
                for (int i = 0; i < fortLevel; i++) spawnTurret(flag, teamId, i);
            }

            int repairLevel = getLevel(teamId, flag.getId(), UpgradeType.REPAIR);
            if (repairLevel > 0) {
                flag.setMaxHp(flag.getBaseMaxHp() + repairLevel * REPAIR_MAX_HP_PER_LEVEL);
            }
        }
    }

    // ─── 영속성 ──────────────────────────────────────────────────────────────

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (var teamEntry : upgrades.entrySet()) {
            for (var flagEntry : teamEntry.getValue().entrySet()) {
                String path = "upgrades." + teamEntry.getKey() + "." + flagEntry.getKey();
                for (var typeEntry : flagEntry.getValue().entrySet()) {
                    config.set(path + "." + typeEntry.getKey().name(), typeEntry.getValue());
                }
            }
        }
        try { config.save(upgradesFile); }
        catch (IOException e) { plugin.getLogger().severe("upgrades.yml 저장 실패: " + e.getMessage()); }
    }

    public void load() {
        if (!upgradesFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(upgradesFile);
        if (!config.isConfigurationSection("upgrades")) return;
        for (String teamId : config.getConfigurationSection("upgrades").getKeys(false)) {
            var byFlag = config.getConfigurationSection("upgrades." + teamId);
            if (byFlag == null) continue;
            for (String flagIdStr : byFlag.getKeys(false)) {
                int flagId;
                try { flagId = Integer.parseInt(flagIdStr); } catch (NumberFormatException ignored) { continue; }
                for (UpgradeType type : UpgradeType.values()) {
                    int level = config.getInt("upgrades." + teamId + "." + flagIdStr + "." + type.name(), 0);
                    if (level > 0) setLevel(teamId, flagId, type, level);
                }
            }
        }
    }

    // ─── 조회 (외부용) ────────────────────────────────────────────────────────

    /** 전체 업그레이드 레벨 맵 조회 (업그레이드 메뉴 렌더링용) */
    public Map<UpgradeType, Integer> getLevels(String teamId, int flagId) {
        var byTeam = upgrades.get(teamId);
        if (byTeam == null) return Map.of();
        var byFlag = byTeam.get(flagId);
        if (byFlag == null) return Map.of();
        return Collections.unmodifiableMap(byFlag);
    }

    // ─── 유틸 ────────────────────────────────────────────────────────────────

    private void notifyTeam(String teamId, String message) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            com.tapflag.team.Team t = teamManager.getTeamByPlayer(p.getUniqueId());
            if (t != null && t.getId().equals(teamId)) p.sendMessage(message);
        }
    }

    private static int weaponDamage(Player player) {
        return switch (player.getInventory().getItemInMainHand().getType()) {
            case WOODEN_SWORD, GOLDEN_SWORD      -> 4;
            case STONE_SWORD                     -> 5;
            case IRON_SWORD                      -> 6;
            case DIAMOND_SWORD                   -> 7;
            case NETHERITE_SWORD                 -> 8;
            case WOODEN_AXE, GOLDEN_AXE          -> 7;
            case STONE_AXE, IRON_AXE, DIAMOND_AXE -> 9;
            case NETHERITE_AXE                   -> 10;
            default                              -> 1;
        };
    }
}
