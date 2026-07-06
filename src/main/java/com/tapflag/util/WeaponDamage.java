package com.tapflag.util;

import org.bukkit.inventory.ItemStack;

/**
 * 무기 종류별 깃발 데미지 테이블.
 * 순수 로직 — Bukkit Material 외 의존성 없음.
 */
public final class WeaponDamage {

    private WeaponDamage() {}

    public static int get(ItemStack item) {
        if (item == null) return 2; // 맨손

        return switch (item.getType()) {
            case NETHERITE_SWORD -> 25;
            case NETHERITE_AXE   -> 22;
            case DIAMOND_SWORD   -> 18;
            case DIAMOND_AXE     -> 16;
            case IRON_SWORD      -> 12;
            case IRON_AXE        -> 10;
            case GOLDEN_SWORD    -> 10;
            case GOLDEN_AXE      ->  8;
            case STONE_SWORD     ->  8;
            case STONE_AXE       ->  7;
            case WOODEN_SWORD    ->  5;
            case WOODEN_AXE      ->  4;
            default -> {
                // 기타 도구(삽, 곡괭이 등)는 이름 기반 폴백
                String name = item.getType().name();
                yield (name.contains("SWORD") || name.contains("AXE")) ? 4 : 2;
            }
        };
    }
}
