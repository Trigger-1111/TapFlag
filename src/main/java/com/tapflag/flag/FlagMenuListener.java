package com.tapflag.flag;

import com.tapflag.GameManager;
import com.tapflag.TapFlagPlugin;
import com.tapflag.team.Team;
import com.tapflag.team.TeamManager;
import com.tapflag.util.MessageUtil;
import com.tapflag.vault.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.*;

/**
 * 깃발 우클릭(ArmorStand 또는 블록) → GUI 메뉴
 * 메인: 상점 / 텔레포트 / 업그레이드 / 팀원 뽑기 / 금고
 */
public class FlagMenuListener implements Listener {

    // ─── 제목 ────────────────────────────────────────────────────────────────
    private static final String T_MAIN    = ChatColor.DARK_AQUA    + "깃발 메뉴";
    private static final String T_SHOP    = ChatColor.GOLD         + "상점";
    private static final String T_TP      = ChatColor.GREEN        + "텔레포트";
    private static final String T_RECRUIT = ChatColor.LIGHT_PURPLE + "팀원 뽑기";
    private static final String T_VAULT   = ChatColor.DARK_GREEN   + "금고";

    // ─── 상수 ────────────────────────────────────────────────────────────────
    private static final int  UPGRADE_COST    = 150;
    private static final int  UPGRADE_HP_GAIN = 100;
    private static final long TP_COOLDOWN_MS  = 15L * 60 * 1000;

    // ─── 판매 아이템 (카테고리당 4개 = 총 16개) ─────────────────────────────
    private record SellEntry(Material mat, String name, int price) {}

    private static final List<SellEntry> SELL_ITEMS = List.of(
        // 농사 (4)
        new SellEntry(Material.WHEAT,          "밀",          2),
        new SellEntry(Material.CARROT,         "당근",         3),
        new SellEntry(Material.POTATO,         "감자",         2),
        new SellEntry(Material.GOLDEN_CARROT,  "황금 당근",    15),
        // 낚시 (4)
        new SellEntry(Material.COD,            "대구",         3),
        new SellEntry(Material.SALMON,         "연어",         5),
        new SellEntry(Material.PUFFERFISH,     "복어",        10),
        new SellEntry(Material.NAUTILUS_SHELL, "노틸러스 껍데기", 25),
        // 사육 (4)
        new SellEntry(Material.LEATHER,        "가죽",         8),
        new SellEntry(Material.WHITE_WOOL,     "양털",         5),
        new SellEntry(Material.FEATHER,        "깃털",         3),
        new SellEntry(Material.RABBIT_HIDE,    "토끼 가죽",     4),
        // 광물 (4)
        new SellEntry(Material.IRON_INGOT,     "철 주괴",       8),
        new SellEntry(Material.GOLD_INGOT,     "금 주괴",      12),
        new SellEntry(Material.DIAMOND,        "다이아몬드",    40),
        new SellEntry(Material.EMERALD,        "에메랄드",      30)
    );

    // ─── 구매 아이템 ─────────────────────────────────────────────────────────
    private record BuyEntry(String name, int price, Material mat, PotionType potionType, int amount) {
        static BuyEntry of(Material mat, String name, int price)            { return new BuyEntry(name, price, mat, null, 1); }
        static BuyEntry bulk(Material mat, String name, int amt, int price) { return new BuyEntry(name + " x" + amt, price, mat, null, amt); }
        static BuyEntry potion(Material potMat, PotionType pt, String name, int price) {
            return new BuyEntry(name, price, potMat, pt, 1);
        }
        boolean isPotion() { return potionType != null; }
    }

    private static final List<BuyEntry> BUY_ITEMS = List.of(
        // 희귀 광물 (파는 가격보다 비쌈)
        BuyEntry.of(Material.IRON_INGOT,          "철 주괴",           15),   // sell 8
        BuyEntry.of(Material.GOLD_INGOT,          "금 주괴",           20),   // sell 12
        BuyEntry.of(Material.DIAMOND,             "다이아몬드",         60),   // sell 40
        BuyEntry.of(Material.ENDER_PEARL,         "엔더 진주",         15),
        // 물약 (일반)
        BuyEntry.potion(Material.POTION, PotionType.HEALING,          "회복 물약",       30),
        BuyEntry.potion(Material.POTION, PotionType.SWIFTNESS,        "신속 물약",       25),
        BuyEntry.potion(Material.POTION, PotionType.STRENGTH,         "근력 물약",       40),
        BuyEntry.potion(Material.POTION, PotionType.FIRE_RESISTANCE,  "내화 물약",       25),
        // 투척 물약
        BuyEntry.potion(Material.SPLASH_POTION, PotionType.HEALING,        "투척 회복 물약",   35),
        BuyEntry.potion(Material.SPLASH_POTION, PotionType.SWIFTNESS,      "투척 신속 물약",   30),
        BuyEntry.potion(Material.SPLASH_POTION, PotionType.STRENGTH,       "투척 근력 물약",   45),
        BuyEntry.potion(Material.SPLASH_POTION, PotionType.FIRE_RESISTANCE,"투척 내화 물약",   30),
        // 기타
        BuyEntry.bulk(Material.ARROW,            "화살", 16,            8),
        BuyEntry.of(Material.GOLDEN_APPLE,       "황금 사과",           25),
        BuyEntry.of(Material.BOOK,               "책",                  10),
        BuyEntry.of(Material.EXPERIENCE_BOTTLE,  "경험치 병",            20)
    );

    // ─── 메뉴 상태 ───────────────────────────────────────────────────────────
    private enum MenuType { MAIN, SHOP, TELEPORT, RECRUIT, VAULT }

    private record MenuState(int flagId, MenuType type, List<UUID> slotUuids, List<Integer> slotFlagIds) {}

    private final Map<UUID, MenuState> playerMenus    = new HashMap<>();
    private final Map<UUID, Long>      tpCooldowns   = new HashMap<>();
    /** 금고가 열린 플레이어 → 해당 팀 ID (팀 변경 시 저장 팀 오염 방지) */
    private final Map<UUID, String>    openVaultTeams = new HashMap<>();

    // ─── 의존성 ───────────────────────────────────────────────────────────────
    private final TapFlagPlugin plugin;
    private final FlagManager   flagManager;
    private final TeamManager   teamManager;
    private final VaultManager  vaultManager;

    public FlagMenuListener(TapFlagPlugin plugin, FlagManager flagManager,
                            TeamManager teamManager, VaultManager vaultManager) {
        this.plugin       = plugin;
        this.flagManager  = flagManager;
        this.teamManager  = teamManager;
        this.vaultManager = vaultManager;
    }

    // ─── 우클릭 감지: ArmorStand ─────────────────────────────────────────────

    @EventHandler
    public void onFlagRightClick(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
        Integer flagId = flagManager.getFlagIdByEntity(stand.getUniqueId());
        if (flagId == null) return;
        event.setCancelled(true);
        openMain(event.getPlayer(), flagId);
    }

    // ─── 우클릭 감지: 블록 직접 클릭 (중앙 보완) ─────────────────────────────

    @EventHandler
    public void onFlagBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;  // 오프핸드 중복 발사 방지
        Block block = event.getClickedBlock();
        if (block == null) return;
        Integer flagId = flagManager.getFlagIdByBlock(block.getLocation());
        if (flagId == null) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.getPlayer().updateInventory();
        openMain(event.getPlayer(), flagId);
    }

    // ─── 메인 메뉴 ────────────────────────────────────────────────────────────

    private void openMain(Player player, int flagId) {
        Flag flag = flagManager.getFlagById(flagId);
        Team team = teamManager.getTeamByPlayer(player.getUniqueId());
        int  gold = (team != null) ? team.getGold() : 0;

        Inventory inv = Bukkit.createInventory(null, 9,
            T_MAIN + " [" + FlagManager.getDisplayName(flagId) + "]");

        // 0: 상점
        inv.setItem(0, team != null
            ? item(Material.GOLD_INGOT, ChatColor.YELLOW + "상점",
                ChatColor.GRAY + "아이템 판매 / 구매", ChatColor.GOLD + "팀 금화: " + gold)
            : item(Material.BARRIER, ChatColor.RED + "상점", ChatColor.GRAY + "팀에 속해야 사용 가능"));

        // 2: 텔레포트
        if (team != null) {
            long expiry = tpCooldowns.getOrDefault(player.getUniqueId(), 0L);
            if (expiry > System.currentTimeMillis()) {
                inv.setItem(2, item(Material.CLOCK, ChatColor.YELLOW + "텔레포트",
                    ChatColor.RED + "쿨타임: " + fmtSec((expiry - System.currentTimeMillis()) / 1000)));
            } else {
                inv.setItem(2, item(Material.ENDER_PEARL, ChatColor.GREEN + "텔레포트",
                    ChatColor.GRAY + "자기 팀 깃발로 이동", ChatColor.AQUA + "쿨타임: 15분"));
            }
        } else {
            inv.setItem(2, item(Material.BARRIER, ChatColor.RED + "텔레포트",
                ChatColor.GRAY + "팀에 속해야 사용 가능"));
        }

        // 4: 업그레이드
        boolean isOwnFlag = flag != null && !flag.isNeutral() && team != null && flag.isOwnedBy(team.getId());
        inv.setItem(4, isOwnFlag
            ? item(Material.ANVIL, ChatColor.AQUA + "업그레이드",
                ChatColor.GRAY + "깃발 MaxHP +" + UPGRADE_HP_GAIN,
                ChatColor.GOLD + "비용: " + UPGRADE_COST + "골드",
                ChatColor.WHITE + "현재 MaxHP: " + flag.getMaxHp() + "  강화: " + flag.getUpgradeCount() + "회")
            : item(Material.BARRIER, ChatColor.RED + "업그레이드", ChatColor.GRAY + "자신의 깃발에서만 가능"));

        // 6: 팀원 뽑기
        boolean isLeader = team != null && team.getLeader().equals(player.getUniqueId());
        if (isLeader) {
            GameManager gm = plugin.getGameManager();
            int cnt = (gm != null) ? gm.getPendingPlayers().size() : 0;
            inv.setItem(6, item(Material.PLAYER_HEAD, ChatColor.LIGHT_PURPLE + "팀원 뽑기",
                ChatColor.GRAY + "대기 중인 플레이어를 영입", ChatColor.YELLOW + "대기: " + cnt + "명"));
        } else {
            inv.setItem(6, item(Material.BARRIER, ChatColor.RED + "팀원 뽑기",
                ChatColor.GRAY + (team != null ? "팀장만 사용 가능" : "팀에 속해야 사용 가능")));
        }

        // 8: 금고
        inv.setItem(8, team != null
            ? item(Material.CHEST, ChatColor.DARK_GREEN + "금고",
                ChatColor.GRAY + "팀 공유 보관소", ChatColor.GREEN + "절대 털리지 않음")
            : item(Material.BARRIER, ChatColor.RED + "금고", ChatColor.GRAY + "팀에 속해야 사용 가능"));

        playerMenus.put(player.getUniqueId(),
            new MenuState(flagId, MenuType.MAIN, List.of(), List.of()));
        player.openInventory(inv);
    }

    // ─── 상점 ─────────────────────────────────────────────────────────────────
    // 레이아웃 (54칸):
    //  Row 0 (0-8)  : 헤더 / 뒤로
    //  Row 1-2 (9-26): 판매 16개 + 빈칸 2개
    //  Row 3 (27-35) : 구분선 (gray glass)
    //  Row 4-5 (36-53): 구매 16개

    private void openShop(Player player, int flagId) {
        Inventory inv = Bukkit.createInventory(null, 54, T_SHOP);

        // 헤더
        inv.setItem(0, glass(Material.GRAY_STAINED_GLASS_PANE,
            ChatColor.GRAY + "─────── 판매 ───────"));
        inv.setItem(4, glass(Material.YELLOW_STAINED_GLASS_PANE,
            ChatColor.YELLOW + "─────── 구매 ───────"));
        inv.setItem(8, item(Material.ARROW, ChatColor.RED + "뒤로 가기"));

        // 판매 아이템 (slots 9-24, 16개)
        for (int i = 0; i < SELL_ITEMS.size(); i++) {
            inv.setItem(9 + i, makeSellStack(SELL_ITEMS.get(i), countItem(player, SELL_ITEMS.get(i).mat())));
        }
        // 나머지 판매 영역 (slots 25-26) 빈 유리
        inv.setItem(25, glass(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(26, glass(Material.GRAY_STAINED_GLASS_PANE, " "));

        // 구분선 (row 3, slots 27-35)
        for (int i = 27; i <= 35; i++) inv.setItem(i, glass(Material.GRAY_STAINED_GLASS_PANE, " "));

        // 구매 아이템 (slots 36-51, 16개)
        for (int i = 0; i < BUY_ITEMS.size() && i < 18; i++) {
            inv.setItem(36 + i, makeBuyStack(BUY_ITEMS.get(i)));
        }

        playerMenus.put(player.getUniqueId(),
            new MenuState(flagId, MenuType.SHOP, List.of(), List.of()));
        player.openInventory(inv);
    }

    private ItemStack makeSellStack(SellEntry e, int have) {
        return item(e.mat(), 1,
            ChatColor.WHITE + e.name(),
            ChatColor.GOLD  + "판매가: " + e.price() + "골드/개",
            ChatColor.AQUA  + "보유: " + have + "개",
            ChatColor.GRAY  + "클릭: 1개 판매");
    }

    private ItemStack makeBuyStack(BuyEntry e) {
        if (e.isPotion()) {
            ItemStack pot = new ItemStack(e.mat());
            PotionMeta meta = (PotionMeta) pot.getItemMeta();
            if (meta != null) {
                meta.setBasePotionType(e.potionType());
                meta.setDisplayName(ChatColor.WHITE + e.name());
                meta.setLore(List.of(
                    ChatColor.GOLD + "구매가: " + e.price() + "골드",
                    ChatColor.GRAY + "클릭: 구매"));
                pot.setItemMeta(meta);
            }
            return pot;
        }
        return item(e.mat(), e.amount(),
            ChatColor.WHITE + e.name(),
            ChatColor.GOLD  + "구매가: " + e.price() + "골드",
            ChatColor.GRAY  + "클릭: 구매");
    }

    // ─── 텔레포트 서브 메뉴 ───────────────────────────────────────────────────

    private void openTeleport(Player player, int flagId) {
        Team team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) { player.sendMessage(MessageUtil.warn("팀에 속해야 합니다.")); return; }

        long expiry = tpCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (expiry > System.currentTimeMillis()) {
            player.sendMessage(MessageUtil.warn("텔레포트 쿨타임: "
                + fmtSec((expiry - System.currentTimeMillis()) / 1000) + " 남음"));
            return;
        }

        List<Integer> ownFlags = flagManager.getAllFlags().values().stream()
            .filter(f -> f.isOwnedBy(team.getId()))
            .map(Flag::getId).sorted().toList();

        if (ownFlags.isEmpty()) {
            player.sendMessage(MessageUtil.warn("소유한 깃발이 없습니다."));
            return;
        }

        int size = Math.max(9, ((ownFlags.size() + 8) / 9) * 9);
        Inventory inv = Bukkit.createInventory(null, size, T_TP);

        for (int i = 0; i < ownFlags.size(); i++) {
            int fid = ownFlags.get(i);
            Flag f = flagManager.getFlagById(fid);
            String locStr = f != null
                ? "(" + f.getLocation().getBlockX() + ", " + f.getLocation().getBlockZ() + ")"
                : "위치 불명";
            inv.setItem(i, item(Material.ENDER_PEARL,
                ChatColor.GREEN + FlagManager.getDisplayName(fid),
                ChatColor.GRAY  + "위치: " + locStr,
                ChatColor.YELLOW + "클릭: 이동 (쿨타임 15분)"));
        }

        playerMenus.put(player.getUniqueId(),
            new MenuState(flagId, MenuType.TELEPORT, List.of(), ownFlags));
        player.openInventory(inv);
    }

    // ─── 팀원 뽑기 서브 메뉴 ─────────────────────────────────────────────────

    private void openRecruit(Player player, int flagId, Team team) {
        GameManager gm = plugin.getGameManager();
        List<UUID> candidates = new ArrayList<>();
        if (gm != null) candidates.addAll(gm.getPendingPlayers());
        if (candidates.isEmpty()) {
            for (UUID uuid : teamManager.getWanderers()) {
                if (plugin.getServer().getPlayer(uuid) != null) candidates.add(uuid);
            }
        }

        if (candidates.isEmpty()) {
            player.sendMessage(MessageUtil.info("현재 영입 가능한 플레이어가 없습니다."));
            openMain(player, flagId);
            return;
        }

        int size = Math.min(54, ((candidates.size() + 8) / 9) * 9);
        Inventory inv = Bukkit.createInventory(null, size, T_RECRUIT);

        for (int i = 0; i < candidates.size() && i < size; i++) {
            UUID uuid = candidates.get(i);
            @SuppressWarnings("deprecation")
            String name = plugin.getServer().getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);
            inv.setItem(i, item(Material.PLAYER_HEAD,
                ChatColor.YELLOW + name,
                ChatColor.GRAY   + "클릭하여 팀에 영입",
                ChatColor.WHITE  + "(영입 후 재접속 가능)"));
        }

        playerMenus.put(player.getUniqueId(),
            new MenuState(flagId, MenuType.RECRUIT, candidates, List.of()));
        player.openInventory(inv);
    }

    // ─── 인벤토리 클릭 ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 금고가 열린 경우 완전 자유 조작 (클릭 캔슬 없음)
        if (openVaultTeams.containsKey(player.getUniqueId())) return;

        MenuState state = playerMenus.get(player.getUniqueId());
        if (state == null) return;

        String title = event.getView().getTitle();
        if (!title.startsWith(T_MAIN) && !title.equals(T_SHOP)
            && !title.equals(T_TP)   && !title.equals(T_RECRUIT)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR
            || clicked.getType() == Material.BARRIER
            || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE
            || clicked.getType() == Material.YELLOW_STAINED_GLASS_PANE) return;

        switch (state.type()) {
            case MAIN     -> handleMainClick(player, state.flagId(), event.getSlot());
            case SHOP     -> handleShopClick(player, state, event.getSlot(), event.getInventory());
            case TELEPORT -> handleTeleportClick(player, state, event.getSlot());
            case RECRUIT  -> handleRecruitClick(player, state, event.getSlot());
        }
    }

    private void handleMainClick(Player player, int flagId, int slot) {
        Team team = teamManager.getTeamByPlayer(player.getUniqueId());
        switch (slot) {
            case 0 -> { // 상점
                if (team == null) { player.sendMessage(MessageUtil.warn("팀에 속해야 합니다.")); return; }
                player.closeInventory(); openShop(player, flagId);
            }
            case 2 -> { // 텔레포트
                if (team == null) { player.sendMessage(MessageUtil.warn("팀에 속해야 합니다.")); return; }
                player.closeInventory(); openTeleport(player, flagId);
            }
            case 4 -> { // 업그레이드
                if (team == null) { player.sendMessage(MessageUtil.warn("팀에 속해야 합니다.")); return; }
                Flag flag = flagManager.getFlagById(flagId);
                if (flag == null || !flag.isOwnedBy(team.getId())) {
                    player.sendMessage(MessageUtil.warn("자신의 깃발에서만 업그레이드 가능")); return;
                }
                if (team.getGold() < UPGRADE_COST) {
                    player.sendMessage(MessageUtil.warn("금화 부족 (필요: " + UPGRADE_COST + ", 보유: " + team.getGold() + ")")); return;
                }
                team.addGold(-UPGRADE_COST);
                flag.upgradeMaxHp(UPGRADE_HP_GAIN);
                player.closeInventory();
                player.sendMessage(MessageUtil.success("깃발 MaxHP +" + UPGRADE_HP_GAIN + " (현재: " + flag.getMaxHp() + ")"));
                plugin.getServer().broadcastMessage(MessageUtil.info(
                    "[" + team.getId() + "] 깃발 [" + FlagManager.getDisplayName(flagId) + "] 강화! MaxHP=" + flag.getMaxHp()));
                var hud = plugin.getHudManager();
                if (hud != null) hud.refreshAll();
            }
            case 6 -> { // 팀원 뽑기
                if (team == null || !team.getLeader().equals(player.getUniqueId())) {
                    player.sendMessage(MessageUtil.warn("팀장만 사용 가능")); return;
                }
                player.closeInventory(); openRecruit(player, flagId, team);
            }
            case 8 -> { // 금고
                if (team == null) { player.sendMessage(MessageUtil.warn("팀에 속해야 합니다.")); return; }
                Inventory vaultInv = vaultManager.createInventory(team.getId());
                player.closeInventory();  // MAIN 상태 제거 (close 이벤트 발생)
                openVaultTeams.put(player.getUniqueId(), team.getId());  // 금고 열림 등록 (팀 ID 고정)
                player.openInventory(vaultInv);
            }
        }
    }

    private void handleShopClick(Player player, MenuState state, int slot, Inventory inv) {
        Team team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) { player.sendMessage(MessageUtil.warn("팀에 속해야 합니다.")); return; }

        if (slot == 8) { player.closeInventory(); openMain(player, state.flagId()); return; }

        // 판매 (slots 9-24)
        if (slot >= 9 && slot <= 24) {
            int idx = slot - 9;
            if (idx >= SELL_ITEMS.size()) return;
            SellEntry e = SELL_ITEMS.get(idx);
            if (countItem(player, e.mat()) < 1) {
                player.sendMessage(MessageUtil.warn("판매할 " + e.name() + " 이(가) 없습니다.")); return;
            }
            if (!player.getInventory().removeItem(new ItemStack(e.mat(), 1)).isEmpty()) {
                player.sendMessage(MessageUtil.warn("아이템 제거 실패")); return;
            }
            team.addGold(e.price());
            player.sendMessage(MessageUtil.success(e.name() + " 판매 (+" + e.price() + "골드 → 팀: " + team.getGold() + ")"));
            inv.setItem(slot, makeSellStack(e, countItem(player, e.mat())));
            return;
        }

        // 구매 (slots 36-51)
        if (slot >= 36 && slot <= 51) {
            int idx = slot - 36;
            if (idx >= BUY_ITEMS.size()) return;
            BuyEntry e = BUY_ITEMS.get(idx);
            if (team.getGold() < e.price()) {
                player.sendMessage(MessageUtil.warn("금화 부족 (필요: " + e.price() + ", 보유: " + team.getGold() + ")")); return;
            }
            team.addGold(-e.price());
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(makeBuyStack(e));
            if (!leftover.isEmpty()) player.getWorld().dropItem(player.getLocation(), leftover.get(0));
            player.sendMessage(MessageUtil.success(e.name() + " 구매 (남은 금화: " + team.getGold() + ")"));
        }
    }

    private void handleTeleportClick(Player player, MenuState state, int slot) {
        List<Integer> flagIds = state.slotFlagIds();
        if (slot < 0 || slot >= flagIds.size()) return;

        long expiry = tpCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (expiry > System.currentTimeMillis()) {
            player.sendMessage(MessageUtil.warn("쿨타임: " + fmtSec((expiry - System.currentTimeMillis()) / 1000)));
            player.closeInventory(); return;
        }

        int fid = flagIds.get(slot);
        Flag flag = flagManager.getFlagById(fid);
        if (flag == null) { player.sendMessage(MessageUtil.warn("깃발을 찾을 수 없습니다.")); player.closeInventory(); return; }

        tpCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + TP_COOLDOWN_MS);
        player.closeInventory();
        Location dest = flag.getLocation().clone().add(0.5, 1, 0.5);
        dest.setYaw(player.getLocation().getYaw());
        player.teleport(dest);
        player.sendMessage(MessageUtil.success("[" + FlagManager.getDisplayName(fid) + "] 깃발로 텔레포트! (쿨타임 15분)"));
    }

    private void handleRecruitClick(Player player, MenuState state, int slot) {
        Team team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null || !team.getLeader().equals(player.getUniqueId())) return;

        List<UUID> candidates = state.slotUuids();
        if (slot < 0 || slot >= candidates.size()) return;

        UUID target = candidates.get(slot);
        if (teamManager.getTeamByPlayer(target) != null) {
            player.sendMessage(MessageUtil.warn("이미 팀에 속한 플레이어")); player.closeInventory(); return;
        }

        teamManager.addToTeam(team.getId(), target);
        GameManager gm = plugin.getGameManager();
        if (gm != null) gm.removePending(target);
        teamManager.save();

        @SuppressWarnings("deprecation")
        String targetName = plugin.getServer().getOfflinePlayer(target).getName();
        if (targetName == null) targetName = target.toString().substring(0, 8);

        player.sendMessage(MessageUtil.success(targetName + " 영입 완료! (이제 재접속 가능)"));
        plugin.getServer().broadcastMessage(MessageUtil.info(targetName + " 님이 [" + team.getId() + "] 팀에 영입!"));
        var hud = plugin.getHudManager();
        if (hud != null) hud.refreshAll();
        player.closeInventory();
    }

    // ─── 인벤토리 닫기 (금고 저장) ───────────────────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        playerMenus.remove(player.getUniqueId());

        // 금고 닫힘: 열릴 때 기록한 팀 ID로 저장 (팀 변경과 무관하게 올바른 팀에 저장)
        String vaultTeamId = openVaultTeams.remove(player.getUniqueId());
        if (vaultTeamId != null) {
            vaultManager.saveVault(vaultTeamId, event.getInventory());
        }
    }

    // ─── 유틸 ────────────────────────────────────────────────────────────────

    private int countItem(Player player, Material mat) {
        int n = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == mat) n += s.getAmount();
        }
        return n;
    }

    private static String fmtSec(long sec) {
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    private ItemStack glass(Material mat, String name) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.setDisplayName(name); s.setItemMeta(m); }
        return s;
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            if (lore.length > 0) m.setLore(Arrays.asList(lore));
            s.setItemMeta(m);
        }
        return s;
    }

    private ItemStack item(Material mat, int amount, String name, String... lore) {
        ItemStack s = new ItemStack(mat, amount);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            if (lore.length > 0) m.setLore(Arrays.asList(lore));
            s.setItemMeta(m);
        }
        return s;
    }
}
