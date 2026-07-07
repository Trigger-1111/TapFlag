package com.tapflag.world;

import com.tapflag.TapFlagPlugin;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

/**
 * 게임 시작 시 지상 돌출 광맥(피라미드/스파이크 형태) 생성, 종료 시 원상복원.
 *
 * 형태: 지표면을 기저로 위로 갈수록 좁아지는 삼각형(피라미드) 단면.
 *  dy=0 : 밑면(baseR 반지름 원)
 *  dy=H : 꼭대기(단일 블록)
 * 외피 STONE, 내부 코어(1~2블록 두께) 광물.
 * 모든 블록이 지표면(surfaceY) 이상에 위치 → 항상 지상 노출.
 */
public class OreManager {

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

        plugin.getLogger().info("[OreManager] generateVeins start: count=" + count
            + " center=(" + cx + "," + cz + ") radius=" + radius);

        int placed = 0;
        for (int attempt = 0; attempt < count * 10 && placed < count; attempt++) {
            double angle = rng.nextDouble() * 2 * Math.PI;
            double r     = radius * (0.15 + rng.nextDouble() * 0.65);
            int vx = cx + (int)(r * Math.cos(angle));
            int vz = cz + (int)(r * Math.sin(angle));

            world.getChunkAt(vx >> 4, vz >> 4).load();

            int surface = world.getHighestBlockYAt(vx, vz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            int height  = 5 + rng.nextInt(4);   // 스파이크 높이: 5~8
            int baseR   = 3 + rng.nextInt(3);   // 밑면 반지름: 3~5

            Material ore = ORE_POOL[rng.nextInt(ORE_POOL.length)];

            int replaced = placeSpike(world, vx, surface, vz, height, baseR, ore);
            if (replaced == 0) continue;

            plugin.getLogger().info("[OreManager]  placed #" + (placed + 1)
                + " pos=(" + vx + "," + surface + "," + vz + ")"
                + " height=" + height + " baseR=" + baseR
                + " ore=" + ore + " replaced=" + replaced);
            placed++;
        }
        plugin.getLogger().info("[OreManager] done: placed=" + placed + "/" + count);
    }

    /**
     * 피라미드 스파이크 배치.
     * dy=0 이 지표면(surfaceY), 위로 갈수록 반지름이 선형 감소.
     * 지표면 이상에만 배치하므로 항상 지상에 노출됨.
     */
    private int placeSpike(World world, int cx, int surfaceY, int cz,
                            int height, int baseR, Material oreType) {
        // 사용할 청크 선로드
        for (int chx = (cx - baseR) >> 4; chx <= (cx + baseR) >> 4; chx++) {
            for (int chz = (cz - baseR) >> 4; chz <= (cz + baseR) >> 4; chz++) {
                world.getChunkAt(chx, chz).load();
            }
        }

        int count = 0;
        for (int dy = 0; dy <= height; dy++) {
            // 높이에 따라 반지름 선형 감소: 밑면 baseR → 꼭대기 0
            double t       = (double) dy / height;
            double layerR  = baseR * (1.0 - t);
            // 광물 코어: 외피보다 1.2블록 얇음 (최소 0)
            double oreR    = Math.max(0.0, layerR - 1.2);

            int intR = (int) Math.ceil(layerR);
            for (int dx = -intR; dx <= intR; dx++) {
                for (int dz = -intR; dz <= intR; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > layerR) continue;

                    Block block = world.getBlockAt(cx + dx, surfaceY + dy, cz + dz);
                    if (!canPlaceOver(block.getType())) continue;

                    savedBlocks.putIfAbsent(block.getLocation(), block.getType());
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
        plugin.getLogger().info("[OreManager] veins removed.");
    }

    // ─── 내부 ─────────────────────────────────────────────────────────────────

    /** 지상 배치 허용 여부 — 기반암·특수 블록만 제외 */
    private static boolean canPlaceOver(Material m) {
        if (m == Material.BEDROCK) return false;
        String n = m.name();
        return !n.contains("CHEST") && !n.contains("CRAFTING") && !n.contains("FURNACE")
            && !n.contains("SPAWNER") && !n.contains("COMMAND") && !n.contains("SHULKER");
    }
}
