package com.tapflag.flag;

import com.tapflag.TapFlagPlugin;
import com.tapflag.team.TeamManager;
import com.tapflag.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FlagManager {

    private final TapFlagPlugin plugin;
    private final TeamManager teamManager;
    private final Map<Integer, Flag> flags = new HashMap<>();
    private final Map<UUID, Integer> entityToFlag = new HashMap<>();

    /** 게임 시작 시 설정: 깃발 ID → 소속 팀 ID */
    private final Map<Integer, String> flagTeamMap = new HashMap<>();

    private final File flagsFile;
    private FileConfiguration flagsConfig;

    private final int maxHp;
    private final int totalFlagCount;

    public FlagManager(TapFlagPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.maxHp      = plugin.getConfig().getInt("flag.max-hp", 300);
        this.totalFlagCount = plugin.getConfig().getInt("flag.count", 5);
        this.flagsFile  = new File(plugin.getDataFolder(), "flags.yml");
        this.flagsConfig = YamlConfiguration.loadConfiguration(flagsFile);
        plugin.getServer().getScheduler().runTask(plugin, this::load);
    }

    // ─── 팀 매핑 ─────────────────────────────────────────────────────────────

    public void setFlagTeamMap(Map<Integer, String> map) {
        flagTeamMap.clear();
        flagTeamMap.putAll(map);
    }

    public void clearFlagTeamMap() { flagTeamMap.clear(); }

    // ─── 배치 / 제거 ─────────────────────────────────────────────────────────

    public Flag placeFlag(int id, Location location) {
        Flag existing = flags.get(id);
        if (existing != null) removeFlagFully(existing);

        Flag flag = new Flag(id, location, maxHp);
        spawnArmorStand(flag);
        placeFlagBlocks(flag);
        flags.put(id, flag);
        entityToFlag.put(flag.getArmorStandUuid(), id);
        save();
        return flag;
    }

    public void removeFlag(int id) {
        Flag flag = flags.remove(id);
        if (flag != null) removeFlagFully(flag);
        entityToFlag.values().removeIf(fid -> fid == id);
        save();
    }

    public void removeAllFlags() {
        for (Flag flag : new ArrayList<>(flags.values())) {
            removeFlagFully(flag);
        }
        flags.clear();
        entityToFlag.clear();
        save();
    }

    /** 게임 시작 시: 균등 각도 + 약간의 반경 랜덤으로 N개 깃발 배치. */
    public boolean spawnRandomFlags(int count) {
        removeAllFlags();
        World world = plugin.getServer().getWorlds().get(0);
        int cx = plugin.getConfig().getInt("map.center-x", 0);
        int cz = plugin.getConfig().getInt("map.center-z", 0);
        int radius = plugin.getConfig().getInt("map.radius", 500);
        Random rng = new Random();

        double baseAngle = rng.nextDouble() * 2 * Math.PI;
        double angleStep = 2 * Math.PI / count;

        for (int flagId = 1; flagId <= count; flagId++) {
            double angle = baseAngle + (flagId - 1) * angleStep;
            // 반경: 55%~80% 사이 랜덤 (너무 외곽/중심 배제)
            double r = radius * (0.55 + rng.nextDouble() * 0.25);
            int x = cx + (int)(r * Math.cos(angle));
            int z = cz + (int)(r * Math.sin(angle));

            world.getChunkAt(x >> 4, z >> 4).load();
            Location loc = findSurface(world, x, z);
            if (loc != null) {
                placeFlag(flagId, loc);
                plugin.getLogger().info("Flag #" + flagId + " placed at "
                    + (int)loc.getX() + "," + (int)loc.getY() + "," + (int)loc.getZ());
            } else {
                plugin.getLogger().warning("Flag #" + flagId + ": no valid surface found.");
            }
        }
        return flags.size() == count;
    }

    private Location findSurface(World world, int x, int z) {
        // MOTION_BLOCKING_NO_LEAVES: 나무 꼭대기 제외, 지면 우선
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Block top = world.getBlockAt(x, y, z);
        if (top.isLiquid()) return null;
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    // ─── 전투 로직 ───────────────────────────────────────────────────────────

    /**
     * @param damage 실제 무기 데미지 (쿨타임 반영된 값)
     * @return true = 점령 발생
     */
    public boolean hitFlag(int flagId, UUID attackerUuid, int damage) {
        Flag flag = flags.get(flagId);
        if (flag == null) return false;

        boolean captured = flag.damage(damage);
        refreshDisplay(flag);

        if (captured) {
            var team = teamManager.getTeamByPlayer(attackerUuid);
            if (team != null) {
                captureFlag(flagId, team.getId());
                return true;
            }

            // 방랑자(wanderer)가 마지막 타격 → 미리 매핑된 팀으로 합류
            String mappedTeam = flagTeamMap.get(flagId);
            if (mappedTeam != null) {
                // 팀이 해체된 경우 재창설 (전 팀원이 깃발 재탈환 시)
                if (teamManager.getTeam(mappedTeam) == null) {
                    teamManager.createTeam(mappedTeam, attackerUuid);
                    plugin.getServer().broadcastMessage(
                        MessageUtil.warn("팀 [" + mappedTeam + "] 이(가) 재창설되었습니다!")
                    );
                }
                teamManager.addToTeam(mappedTeam, attackerUuid);
                var gm = plugin.getGameManager();
                if (gm != null) gm.removePending(attackerUuid);
                captureFlag(flagId, mappedTeam);
                Player p = plugin.getServer().getPlayer(attackerUuid);
                if (p != null) {
                    p.sendMessage(MessageUtil.success("[" + mappedTeam + "] 팀에 합류했습니다!"));
                }
                return true;
            }

            // 매핑 없으면 HP 1로 보류 (정상 게임에서는 발생하지 않아야 함)
            flag.setHp(1);
            refreshDisplay(flag);
        }
        return false;
    }

    public void captureFlag(int flagId, String teamId) {
        Flag flag = flags.get(flagId);
        if (flag == null) return;

        flag.setOwningTeamId(teamId);
        flag.resetHp();
        teamManager.onFlagCaptured(flagId, teamId);
        updateBannerColor(flag);
        refreshDisplay(flag);

        // 점령 팀에 금화 지급
        var capturedTeam = teamManager.getTeam(teamId);
        if (capturedTeam != null) capturedTeam.addGold(100);

        plugin.getServer().broadcastMessage(
            MessageUtil.prefix() + ChatColor.GOLD + "깃발 [" + getDisplayName(flagId) + "] 이(가) ["
            + teamId + "] 팀에 점령되었습니다!"
        );

        // HUD 즉시 갱신
        var hud = plugin.getHudManager();
        if (hud != null) hud.refreshAll();

        checkVictory();
        save();
    }

    private void checkVictory() {
        for (var team : teamManager.getAllTeams()) {
            if (team.getFlagCount() >= totalFlagCount) {
                plugin.getServer().broadcastMessage(
                    ChatColor.GOLD + "" + ChatColor.BOLD
                    + "★ 팀 [" + team.getId() + "] 이(가) 모든 깃발을 점령하여 승리했습니다! ★"
                );
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    var gm = plugin.getGameManager();
                    if (gm != null && gm.isRunning()) gm.stopGame();
                }, 200L);
                break;
            }
        }
    }

    // ─── 조회 ────────────────────────────────────────────────────────────────

    public Flag getFlagById(int id)              { return flags.get(id); }
    public Map<Integer, Flag> getAllFlags()       { return flags; }
    public Integer getFlagIdByEntity(UUID uuid)  { return entityToFlag.get(uuid); }

    // ─── ArmorStand + 블록 관리 ───────────────────────────────────────────────

    private void spawnArmorStand(Flag flag) {
        Location base = flag.getLocation().clone(); // surfaceY+1 (기둥 높이)
        base.getChunk().load();

        // 1번 스탠드: 기둥 높이 (surfaceY+1) — 이름표 표시
        ArmorStand stand1 = createRawStand(base);
        stand1.setCustomNameVisible(true);
        flag.setArmorStandUuid(stand1.getUniqueId());
        entityToFlag.put(stand1.getUniqueId(), flag.getId());
        updateStandName(flag, stand1);

        // 2번 스탠드: 배너 높이 (surfaceY+2) — 추가 히트박스 전용
        Location bannerLoc = base.clone().add(0, 1, 0);
        ArmorStand stand2 = createRawStand(bannerLoc);
        stand2.setCustomNameVisible(false);
        flag.setArmorStandUuid2(stand2.getUniqueId());
        entityToFlag.put(stand2.getUniqueId(), flag.getId());
    }

    private ArmorStand createRawStand(Location loc) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(false);
        stand.setArms(false);
        stand.setBasePlate(false);
        return stand;
    }

    /** 기반암(지면) + fence 기둥 + 팀 배너 배치 */
    private void placeFlagBlocks(Flag flag) {
        Location base = flag.getLocation(); // surfaceY+1
        int bx = base.getBlockX();
        int by = base.getBlockY();           // surfaceY+1
        int bz = base.getBlockZ();
        World world = base.getWorld();

        // 지면(surfaceY) → 기반암으로 교체 (원래 블록 저장)
        Block groundBlock = world.getBlockAt(bx, by - 1, bz);
        flag.setGroundBlock(groundBlock.getLocation());
        flag.setOriginalGround(groundBlock.getType());
        groundBlock.setType(Material.BEDROCK);

        Block poleBlock   = world.getBlockAt(bx, by,     bz);
        Block bannerBlock = world.getBlockAt(bx, by + 1, bz);

        poleBlock.setType(Material.OAK_FENCE);
        Material bannerMat = getBannerMaterial(flag.getOwningTeamId());
        bannerBlock.setType(bannerMat);
        if (bannerBlock.getBlockData() instanceof Rotatable rot) {
            rot.setRotation(BlockFace.SOUTH);
            bannerBlock.setBlockData(rot);
        }

        flag.setPoleBlock(poleBlock.getLocation());
        flag.setBannerBlock(bannerBlock.getLocation());
    }

    /** 팀 변경 시 배너 색상만 교체 */
    private void updateBannerColor(Flag flag) {
        if (flag.getBannerBlock() == null) return;
        Block bannerBlock = flag.getBannerBlock().getBlock();
        Material mat = getBannerMaterial(flag.getOwningTeamId());
        bannerBlock.setType(mat);
        if (bannerBlock.getBlockData() instanceof Rotatable rot) {
            rot.setRotation(BlockFace.SOUTH);
            bannerBlock.setBlockData(rot);
        }
    }

    private void removeFlagFully(Flag flag) {
        // ArmorStand 제거 (1번 + 2번)
        if (flag.getArmorStandUuid() != null) {
            entityToFlag.remove(flag.getArmorStandUuid());
            for (World w : plugin.getServer().getWorlds()) {
                Entity e = w.getEntity(flag.getArmorStandUuid());
                if (e != null) { e.remove(); break; }
            }
        }
        if (flag.getArmorStandUuid2() != null) {
            entityToFlag.remove(flag.getArmorStandUuid2());
            for (World w : plugin.getServer().getWorlds()) {
                Entity e = w.getEntity(flag.getArmorStandUuid2());
                if (e != null) { e.remove(); break; }
            }
        }
        // 블록 제거 (AIR 복원)
        if (flag.getBannerBlock() != null) flag.getBannerBlock().getBlock().setType(Material.AIR);
        if (flag.getPoleBlock()   != null) flag.getPoleBlock().getBlock().setType(Material.AIR);
        // 지면 원래 블록 복원
        if (flag.getGroundBlock() != null && flag.getOriginalGround() != null) {
            flag.getGroundBlock().getBlock().setType(flag.getOriginalGround());
        }
    }

    // (구버전 호환 — 외부에서 직접 호출될 수 있는 경우 대비)
    public void removeArmorStand(Flag flag) { removeFlagFully(flag); }

    private void refreshDisplay(Flag flag) {
        if (flag.getArmorStandUuid() == null) return;
        for (World w : plugin.getServer().getWorlds()) {
            Entity e = w.getEntity(flag.getArmorStandUuid());
            if (e instanceof ArmorStand stand) { updateStandName(flag, stand); return; }
        }
    }

    private void updateStandName(Flag flag, ArmorStand stand) {
        String teamDisplay = flag.isNeutral()
            ? ChatColor.WHITE + "중립"
            : ChatColor.GOLD + flag.getOwningTeamId();
        stand.setCustomName(
            ChatColor.YELLOW + "[" + getDisplayName(flag.getId()) + "] "
            + ChatColor.RED + flag.getHp() + "/" + flag.getMaxHp() + " "
            + buildHpBar(flag) + " " + teamDisplay
        );
    }

    private String buildHpBar(Flag flag) {
        int filled = (int) Math.ceil(flag.getHpPercent() * 10);
        StringBuilder bar = new StringBuilder(ChatColor.GREEN + "[");
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? ChatColor.GREEN + "|" : ChatColor.DARK_GRAY + "|");
        }
        bar.append(ChatColor.GREEN + "]");
        return bar.toString();
    }

    /** 깃발 ID → 영문 표시 이름 */
    public static String getDisplayName(int flagId) {
        return switch (flagId) {
            case 1 -> "Forest";
            case 2 -> "Ocean";
            case 3 -> "Mountain";
            case 4 -> "Center";
            case 5 -> "Darkforest";
            default -> "#" + flagId;
        };
    }

    /** 블록 위치로 깃발 ID 조회 (기반암/기둥/배너 모두 포함). 없으면 null */
    public Integer getFlagIdByBlock(Location loc) {
        for (Map.Entry<Integer, Flag> entry : flags.entrySet()) {
            Flag f = entry.getValue();
            if (blockLocEq(f.getGroundBlock(), loc)
                || blockLocEq(f.getPoleBlock(),   loc)
                || blockLocEq(f.getBannerBlock(),  loc)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean blockLocEq(Location a, Location b) {
        if (a == null || b == null) return false;
        return a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ();
    }

    /** 팀 ID → 배너 Material 매핑 */
    static Material getBannerMaterial(String teamId) {
        if (teamId == null) return Material.WHITE_BANNER;
        return switch (teamId) {
            case "빨강" -> Material.RED_BANNER;
            case "파랑" -> Material.BLUE_BANNER;
            case "초록" -> Material.GREEN_BANNER;
            case "노랑" -> Material.YELLOW_BANNER;
            case "보라" -> Material.PURPLE_BANNER;
            case "주황" -> Material.ORANGE_BANNER;
            case "하늘" -> Material.CYAN_BANNER;
            case "분홍" -> Material.PINK_BANNER;
            default     -> Material.LIGHT_BLUE_BANNER;
        };
    }

    // ─── 영속성 ──────────────────────────────────────────────────────────────

    public void save() {
        flagsConfig = new YamlConfiguration();
        for (Flag flag : flags.values()) {
            String path = "flags." + flag.getId();
            Location loc = flag.getLocation();
            flagsConfig.set(path + ".world", loc.getWorld().getName());
            flagsConfig.set(path + ".x", loc.getX());
            flagsConfig.set(path + ".y", loc.getY());
            flagsConfig.set(path + ".z", loc.getZ());
            flagsConfig.set(path + ".hp", flag.getHp());
            flagsConfig.set(path + ".owningTeam", flag.getOwningTeamId());
            if (flag.getArmorStandUuid() != null)
                flagsConfig.set(path + ".armorStandUuid", flag.getArmorStandUuid().toString());
            if (flag.getArmorStandUuid2() != null)
                flagsConfig.set(path + ".armorStandUuid2", flag.getArmorStandUuid2().toString());
            if (flag.getGroundBlock() != null) {
                Location g = flag.getGroundBlock();
                flagsConfig.set(path + ".groundBlock.x", g.getBlockX());
                flagsConfig.set(path + ".groundBlock.y", g.getBlockY());
                flagsConfig.set(path + ".groundBlock.z", g.getBlockZ());
            }
            if (flag.getOriginalGround() != null)
                flagsConfig.set(path + ".originalGround", flag.getOriginalGround().name());
        }
        try { flagsConfig.save(flagsFile); }
        catch (IOException e) { plugin.getLogger().severe("flags.yml 저장 실패: " + e.getMessage()); }
    }

    private void load() {
        if (!flagsFile.exists()) return;
        flagsConfig = YamlConfiguration.loadConfiguration(flagsFile);
        if (!flagsConfig.isConfigurationSection("flags")) return;

        for (String idStr : flagsConfig.getConfigurationSection("flags").getKeys(false)) {
            int id = Integer.parseInt(idStr);
            String path = "flags." + id;
            String worldName = flagsConfig.getString(path + ".world");
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) { plugin.getLogger().warning("Flag #" + id + ": world '" + worldName + "' missing"); continue; }

            double x = flagsConfig.getDouble(path + ".x");
            double y = flagsConfig.getDouble(path + ".y");
            double z = flagsConfig.getDouble(path + ".z");
            Location loc = new Location(world, x, y, z);
            loc.getChunk().load();

            Flag flag = new Flag(id, loc, maxHp);
            flag.setHp(flagsConfig.getInt(path + ".hp", maxHp));
            flag.setOwningTeamId(flagsConfig.getString(path + ".owningTeam", null));

            // 1번 ArmorStand 복원 또는 재생성
            String uuidStr = flagsConfig.getString(path + ".armorStandUuid");
            boolean stand1Found = false;
            if (uuidStr != null) {
                UUID uuid = UUID.fromString(uuidStr);
                Entity existing = world.getEntity(uuid);
                if (existing instanceof ArmorStand stand) {
                    flag.setArmorStandUuid(uuid);
                    stand.setCustomNameVisible(true);
                    updateStandName(flag, stand);
                    entityToFlag.put(uuid, id);
                    stand1Found = true;
                }
            }
            if (!stand1Found) {
                // spawnArmorStand이 두 스탠드 모두 등록
                spawnArmorStand(flag);
            } else {
                // 2번 ArmorStand 복원 또는 재생성
                String uuidStr2 = flagsConfig.getString(path + ".armorStandUuid2");
                boolean stand2Found = false;
                if (uuidStr2 != null) {
                    UUID uuid2 = UUID.fromString(uuidStr2);
                    Entity existing2 = world.getEntity(uuid2);
                    if (existing2 instanceof ArmorStand stand2) {
                        stand2.setCustomNameVisible(false);
                        flag.setArmorStandUuid2(uuid2);
                        entityToFlag.put(uuid2, id);
                        stand2Found = true;
                    }
                }
                if (!stand2Found) {
                    Location bannerLoc = loc.clone().add(0, 1, 0);
                    ArmorStand stand2 = createRawStand(bannerLoc);
                    stand2.setCustomNameVisible(false);
                    flag.setArmorStandUuid2(stand2.getUniqueId());
                    entityToFlag.put(stand2.getUniqueId(), id);
                }
            }

            // groundBlock 복원
            String groundKey = path + ".groundBlock";
            if (flagsConfig.isSet(groundKey + ".x")) {
                int gx = flagsConfig.getInt(groundKey + ".x");
                int gy = flagsConfig.getInt(groundKey + ".y");
                int gz = flagsConfig.getInt(groundKey + ".z");
                flag.setGroundBlock(new Location(world, gx, gy, gz));
            }
            String origGround = flagsConfig.getString(path + ".originalGround");
            if (origGround != null) {
                try { flag.setOriginalGround(Material.valueOf(origGround)); }
                catch (IllegalArgumentException ignored) {}
            }

            // 블록 복원 (로드 시 항상 재배치)
            placeFlagBlocks(flag);

            flags.put(id, flag);
        }
        plugin.getLogger().info("Flags loaded: " + flags.size());
    }
}
