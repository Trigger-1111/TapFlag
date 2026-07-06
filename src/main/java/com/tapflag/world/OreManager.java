package com.tapflag.world;

import com.tapflag.TapFlagPlugin;
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

    public void generateVeins() {
        savedBlocks.clear();
        World world = plugin.getServer().getWorlds().get(0);
        Random rng = new Random();

        int count  = plugin.getConfig().getInt("ore-veins.count", 20);
        int cx     = plugin.getConfig().getInt("map.center-x", 0);
        int cz     = plugin.getConfig().getInt("map.center-z", 0);
        int radius = plugin.getConfig().getInt("map.radius", 500);
        int yMin   = plugin.getConfig().getInt("ore-veins.y-min", 15);
        int yMax   = plugin.getConfig().getInt("ore-veins.y-max", 55);

        int placed = 0;
        for (int attempt = 0; attempt < count * 8 && placed < count; attempt++) {
            double angle = rng.nextDouble() * 2 * Math.PI;
            double r     = radius * (0.15 + rng.nextDouble() * 0.65); // 15~80% 반경
            int vx = cx + (int)(r * Math.cos(angle));
            int vz = cz + (int)(r * Math.sin(angle));
            int vy = yMin + rng.nextInt(Math.max(1, yMax - yMin));

            world.getChunkAt(vx >> 4, vz >> 4).load();

            int outerR  = 3 + rng.nextInt(3); // 외부 반경 3~5
            int surface = world.getHighestBlockYAt(vx, vz);
            if (vy + outerR >= surface - 2) continue; // 지표 노출 방지

            Material ore = ORE_POOL[rng.nextInt(ORE_POOL.length)];
            int oreR = Math.max(1, outerR - 2); // 내부 광물 코어 반경

            placeVein(world, vx, vy, vz, outerR, oreR, ore);
            placed++;
        }
        plugin.getLogger().info("Ore veins placed: " + placed + "/" + count);
    }

    private void placeVein(World world, int cx, int cy, int cz,
                           int outerR, int oreR, Material oreType) {
        // 광맥이 걸치는 모든 청크 미리 로드
        for (int chx = (cx - outerR) >> 4; chx <= (cx + outerR) >> 4; chx++) {
            for (int chz = (cz - outerR) >> 4; chz <= (cz + outerR) >> 4; chz++) {
                world.getChunkAt(chx, chz).load();
            }
        }

        for (int dx = -outerR; dx <= outerR; dx++) {
            for (int dy = -outerR; dy <= outerR; dy++) {
                for (int dz = -outerR; dz <= outerR; dz++) {
                    double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                    if (dist > outerR) continue;

                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material orig = block.getType();
                    if (!canReplace(orig)) continue;

                    // 원래 블록은 최초 1회만 저장 (겹치는 광맥 대비)
                    savedBlocks.putIfAbsent(block.getLocation(), orig);
                    block.setType(dist <= oreR ? oreType : Material.STONE);
                }
            }
        }
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
        if (!m.isSolid() || m == Material.BEDROCK) return false;
        String n = m.name();
        return !n.contains("CHEST") && !n.contains("CRAFTING")
            && !n.contains("FURNACE") && !n.contains("SPAWNER")
            && !n.contains("COMMAND") && !n.contains("SHULKER");
    }
}
