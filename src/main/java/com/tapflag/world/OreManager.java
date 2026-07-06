package com.tapflag.world;

import com.tapflag.TapFlagPlugin;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

/**
 * 게임 시작 시 랜덤 광맥 생성, 종료 시 원상복원.
 * 구조: 외부 돌(STONE) 껍질 + 내부 광물 코어.
 */
public class OreManager {

    // 광물 가중치 풀 (총 100개)
    private static final Material[] ORE_POOL = buildPool();

    private static Material[] buildPool() {
        List<Material> pool = new ArrayList<>();
        add(pool, Material.IRON_ORE,     30);
        add(pool, Material.COAL_ORE,     20);
        add(pool, Material.COPPER_ORE,   15);
        add(pool, Material.GOLD_ORE,     12);
        add(pool, Material.LAPIS_ORE,     8);
        add(pool, Material.REDSTONE_ORE,  8);
        add(pool, Material.DIAMOND_ORE,   5);
        add(pool, Material.EMERALD_ORE,   2);
        return pool.toArray(new Material[0]);
    }

    private static void add(List<Material> pool, Material m, int weight) {
        for (int i = 0; i < weight; i++) pool.add(m);
    }

    private final TapFlagPlugin plugin;
    // 위치 → 원래 블록 (복원용)
    private final Map<Location, Material> savedBlocks = new LinkedHashMap<>();

    public OreManager(TapFlagPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── 생성 ─────────────────────────────────────────────────────────────────

    /**
     * 지상 돌출형 광맥 생성.
     * 광맥 중심을 surface - outerR + 1 에 배치해 돌(STONE) 껍질이 지표면을 뚫고 나오게 함.
     * 플레이어가 지표에서 바로 발견/채굴 가능.
     */
    public void generateVeins() {
        savedBlocks.clear();
        World world = plugin.getServer().getWorlds().get(0);
        Random rng = new Random();

        int count  = plugin.getConfig().getInt("ore-veins.count", 20);
        int cx     = plugin.getConfig().getInt("map.center-x", 0);
        int cz     = plugin.getConfig().getInt("map.center-z", 0);
        int radius = plugin.getConfig().getInt("map.radius", 500);

        plugin.getLogger().info("[OreManager] generateVeins start: count=" + count
            + " center=(" + cx + "," + cz + ") radius=" + radius);

        int placed = 0, skipNoBlocks = 0;
        for (int attempt = 0; attempt < count * 10 && placed < count; attempt++) {
            double angle = rng.nextDouble() * 2 * Math.PI;
            double r     = radius * (0.15 + rng.nextDouble() * 0.65);
            int vx = cx + (int)(r * Math.cos(angle));
            int vz = cz + (int)(r * Math.sin(angle));

            world.getChunkAt(vx >> 4, vz >> 4).load();

            int surface = world.getHighestBlockYAt(vx, vz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            int outerR  = 3 + rng.nextInt(3);  // 3~5

            // 중심을 지표 위에 배치 → 구 하반구만 땅에 묻히고 상반구가 지상 돌출
            int vy = surface + 1;

            Material ore = ORE_POOL[rng.nextInt(ORE_POOL.length)];
            int oreR = Math.max(1, outerR - 2);

            int replaced = placeVein(world, vx, vy, vz, outerR, oreR, ore, surface);
            if (replaced == 0) {
                skipNoBlocks++;
                if (skipNoBlocks <= 5) {
                    Block sample = world.getBlockAt(vx, vy, vz);
                    plugin.getLogger().info("[OreManager]  skip(noReplace) pos=("
                        + vx + "," + vy + "," + vz + ") surface=" + surface
                        + " centerBlock=" + sample.getType());
                }
                continue;
            }

            plugin.getLogger().info("[OreManager]  placed #" + (placed + 1)
                + " pos=(" + vx + "," + vy + "," + vz + ")"
                + " surface=" + surface + " outerR=" + outerR
                + " ore=" + ore + " replaced=" + replaced);
            placed++;
        }
        plugin.getLogger().info("[OreManager] done: placed=" + placed + "/" + count
            + " skipNoBlocks=" + skipNoBlocks);
    }

    /**
     * 광맥 배치.
     * surfaceY 이상 위치(공기 포함)도 배치 허용 → 돔 형태로 지상 돌출.
     */
    private int placeVein(World world, int cx, int cy, int cz,
                          int outerR, int oreR, Material oreType, int surfaceY) {
        for (int chx = (cx - outerR) >> 4; chx <= (cx + outerR) >> 4; chx++) {
            for (int chz = (cz - outerR) >> 4; chz <= (cz + outerR) >> 4; chz++) {
                world.getChunkAt(chx, chz).load();
            }
        }

        int count = 0;
        for (int dx = -outerR; dx <= outerR; dx++) {
            for (int dy = -outerR; dy <= outerR; dy++) {
                for (int dz = -outerR; dz <= outerR; dz++) {
                    double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                    if (dist > outerR) continue;

                    int blockY = cy + dy;
                    Block block = world.getBlockAt(cx + dx, blockY, cz + dz);
                    Material orig = block.getType();

                    if (blockY >= surfaceY) {
                        // 지표면 이상: 공기에도 배치 (돌출 돔 생성), 특수 블록은 건드리지 않음
                        if (orig == Material.BEDROCK) continue;
                        String n = orig.name();
                        if (n.contains("CHEST") || n.contains("CRAFTING") || n.contains("FURNACE")
                            || n.contains("SPAWNER") || n.contains("COMMAND") || n.contains("SHULKER")) continue;
                    } else {
                        // 지하: 기존 방식
                        if (!canReplace(orig)) continue;
                    }

                    savedBlocks.putIfAbsent(block.getLocation(), orig);
                    block.setType(dist <= oreR ? oreType : Material.STONE);
                    count++;
                }
            }
        }
        return count;
    }

    // ─── 제거 ─────────────────────────────────────────────────────────────────

    public void removeAllVeins() {
        for (var entry : savedBlocks.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }
        savedBlocks.clear();
        plugin.getLogger().info("Ore veins removed.");
    }

    // ─── 내부 ─────────────────────────────────────────────────────────────────

    private static boolean canReplace(Material m) {
        // 공기, 액체, 기반암, 특수 블록은 건드리지 않음
        if (m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR) return false;
        if (m == Material.WATER || m == Material.LAVA) return false;
        if (m == Material.BEDROCK) return false;
        String n = m.name();
        if (n.contains("CHEST") || n.contains("CRAFTING") || n.contains("FURNACE")
            || n.contains("SPAWNER") || n.contains("COMMAND") || n.contains("SHULKER")) return false;
        return true;
    }
}
