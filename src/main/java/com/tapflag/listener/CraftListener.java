package com.tapflag.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

/** 상자(CHEST, TRAPPED_CHEST) 제작 금지 — 통(BARREL)을 대신 사용 */
public class CraftListener implements Listener {

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        Material result = event.getRecipe().getResult().getType();
        if (result == Material.CHEST || result == Material.TRAPPED_CHEST) {
            event.getInventory().setResult(null);
        }
    }
}
