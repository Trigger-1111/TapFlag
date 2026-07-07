package com.tapflag.flag;

import com.tapflag.GameManager;
import com.tapflag.TapFlagPlugin;
import com.tapflag.team.Team;
import com.tapflag.team.TeamManager;
import com.tapflag.upgrade.FlagUpgradeManager;
import com.tapflag.upgrade.UpgradeType;
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
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * 깃발 우클릭(ArmorStand 또는 블록) → GUI 메뉴
 * 메인: 상점 / 텔레포트 / 업그레이드 / 팀원 뽑기 / 금고
 */
public class FlagMenuListener implements Listener {

    // ─── 제목 ────────────────────────────────────────────────────────────────
    private static final String T_MAIN      = ChatColor.DARK_AQUA    + "깃발 메뉴";
    private static final String T_SHOP      = ChatColor.GOLD         + "상점";
    private static final String T_TP        = ChatColor.GREEN        + "텔레포트";
    private static final String T_RECRUIT   = ChatColor.LIGHT_PURPLE + "팀원 뽑기";
    private static final String T_VAULT     = ChatColor.DARK_GREEN   + "금고";
    private static final String T_UPGRADE   = ChatColor.AQUA         + "업그레이드";

    // ─── 상수 ────────────────────────────────────────────────────────────────
    private static final long TP_COOLDOWN_MS = 15L * 60 * 1000;

    // ─── 판매 아이템 (카테고리: 농사/낚시/사육/광물/사냥) ────────────────────
    private record SellEntry(Material mat, String name, int price, String category) {}

    private static final List<SellEntry> SELL_ITEMS = List.of(
        // 농사 (4)
        new SellEntry(Material.WHEAT,          "밀",              2,  "농사"),
        new SellEntry(Material.CARROT,         "당근",             3,  "농사"),
        new SellEntry(Material.POTATO,         "감자",             2,  "농사"),
        new SellEntry(Material.GOLDEN_CARROT,  "황금 당근",        15, "농사"),
        // 낚시 (4)
        new SellEntry(Material.COD,            "대구",             3,  "낚시"),
        new SellEntry(Material.SALMON,         "연어",             5,  "낚시"),
        new SellEntry(Material.PUFFERFISH,     "복어",            10, "낚시"),
        new SellEntry(Material.NAUTILUS_SHELL, "노틸러스 껍데기",  25, "낚시"),
        // 사육 (4)
        new SellEntry(Material.LEATHER,        "가죽",             8,  "사육"),
        new SellEntry(Material.WHITE_WOOL,     "양털",             5,  "사육"),
        new SellEntry(Material.FEATHER,        "깃털",             3,  "사육"),
        new SellEntry(Material.RABBIT_HIDE,    "토끼 가죽",         4, "사육"),
        // 광물 (4)
        new SellEntry(Material.IRON_INGOT,     "철 주괴",           8, "광물"),
        new SellEntry(Material.GOLD_INGOT,     "금 주괴",          12, "광물"),
        new SellEntry(Material.DIAMOND,        "다이아몬드",        40, "광물"),
        new SellEntry(Material.EMERALD,        "에메랄드",          30, "광물"),
        // 사냥 (2)
        new SellEntry(Material.SPIDER_EYE,     "거미 눈",           6, "사냥"),
        new SellEntry(Material.ROTTEN_FLESH,   "썩은 고기",         2, "사냥")
    );

    // ─── 포화(飽和) 시스템 ────────────────────────────────────────────────────
    /** 팀 당 한 분야 판매 누적 시 포화 발생 → 가격 -50%, 다른 분야 포화 시 해제 */
    private static final int SATURATION_THRESHOLD = 20;
    /** teamId → 현재 포화 카테고리 (null = 없음) */
    private final Map<String, String>               saturatedCategory = new HashMap<>();
    /** teamId → 카테고리 → 판매 누적량 */
    private final Map<String, Map<String, Integer>> saleCounts        = new HashMap<>();

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
    private enum MenuType { MAIN, SHOP, TELEPORT, RECRUIT, VAULT, UPGRADE }

    private record MenuState(int flagId, MenuType type, List<UUID> slotUuids, List<Integer> slotFlagIds) {}

    private final Map<UUID, MenuState> playerMenus    = new HashMap<>();
    private final Map<UUID, Long>      tpCooldowns   = new HashMap<>();
    /** 금고가 열린 플레이어 → 해당 팀 ID (팀 변경 시 저장 팀 오염 방지) */
    private final Map<UUID, String>    openVaultTeams = new HashMap<>();

    // ─── 의존성 ───────────────────────────────────────────────────────────────
    private final TapFlagPlugin      plugin;
    private final NamespacedKey      uiItemKey;
    private final FlagManager        flagManager;
    private final TeamManager        teamManager;
    private final VaultManager       vaultManager;
    private final FlagUpgradeManager upgradeManager;

    public FlagMenuListener(TapFlagPlugin plugin, FlagManager flagManager,
                            TeamManager teamManager, VaultManager vaultManager,
                            FlagUpgradeManager upgradeManager) {
        this.plugin          = plugin;
        this.flagManager     = flagManager;
        this.teamManager     = teamManager;
        this.vaultManager    = vaultManager;
        this.upgradeManager  = upgradeManager;
        this.uiItemKey       = new NamespacedKey(plugin, "ui_item");
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
        int  point = (team != null) ? team.getPoint() : 0;
        int  fp    = (team != null) ? team.getFlagpoint() : 0;

        Inventory inv = Bukkit.createInventory(null, 9,
            T_MAIN + " [" + FlagManager.getDisplayName(flagId) + "]");

        // 0: 상점
        inv.setItem(0, team != null
            ? item(Material.GOLD_INGOT, ChatColor.YELLOW + "상점",
                ChatColor.GRAY + "아이템 판매 / 구매", ChatColor.GOLD + "팀 포인트: " + point)
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
                ChatColor.GRAY + "내구력 / 요새화 / 생산량 / 지원 / 수리",
                ChatColor.LIGHT_PURPLE + "FP: " + fp + "개",
                ChatColor.GRAY + "클릭: 업그레이드 메뉴")
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

        // 8: 공유창고
        inv.setItem(8, team != null
            ? item(Material.CHEST, ChatColor.DARK_GREEN + "공유창고",
                ChatColor.GRAY + "팀 공유창고")
            : item(Material.BARRIER, ChatColor.RED + "공유창고", ChatColor.GRAY + "팀에 속해야 사용 가능"));

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

        // 행 0: 뒤로 버튼 + 판매 라벨 + 포화 상태 표시
        Team shopTeam = teamManager.getTeamByPlayer(player.getUniqueId());
        String shopSatCat = shopTeam != null ? saturatedCategory.getOrDefault(shopTeam.getId(), null) : null;

        inv.setItem(0, item(Material.ARROW, ChatColor.RED + "뒤로 가기"));
        for (int i = 1; i <= 5; i++)
            inv.setItem(i, glass(Material.GRAY_STAINED_GLASS_PANE,
                i == 3 ? ChatColor.GRAY + "━━━ 판매 (아이템 → 포인트) ━━━" : " "));
        // 포화 상태 표시 (slots 6-8)
        if (shopSatCat != null) {
            inv.setItem(6, glass(Material.GRAY_STAINED_GLASS_PANE,
                ChatColor.RED + "⚠ [" + shopSatCat + "] 포화 상태 (-50%)",
                ChatColor.YELLOW + "다른 분야 " + SATURATION_THRESHOLD + "개 판매 시 해제"));
        } else {
            inv.setItem(6, glass(Material.GRAY_STAINED_GLASS_PANE,
                ChatColor.GREEN + "✔ 모든 분야 정상",
                ChatColor.GRAY + "한 분야 " + SATURATION_THRESHOLD + "개 판매 시 포화 발생"));
        }
        inv.setItem(7, glass(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(8, glass(Material.GRAY_STAINED_GLASS_PANE, " "));

        // 판매 아이템 (slots 9-26, 18개)
        for (int i = 0; i < SELL_ITEMS.size(); i++) {
            SellEntry se = SELL_ITEMS.get(i);
            boolean sat = shopSatCat != null && shopSatCat.equals(se.category());
            inv.setItem(9 + i, makeSellStack(se, countItem(player, se.mat()), sat));
        }

        // 구분선 행 (row 3, slots 27-35) — 중앙에 "구매" 라벨
        for (int i = 27; i <= 35; i++)
            inv.setItem(i, glass(Material.YELLOW_STAINED_GLASS_PANE,
                i == 31 ? ChatColor.YELLOW + "━━━ 구매 (포인트 → 아이템) ━━━" : " "));

        // 구매 아이템 (slots 36-51, 16개)
        for (int i = 0; i < BUY_ITEMS.size() && i < 18; i++) {
            inv.setItem(36 + i, makeBuyStack(BUY_ITEMS.get(i)));
        }

        playerMenus.put(player.getUniqueId(),
            new MenuState(flagId, MenuType.SHOP, List.of(), List.of()));
        player.openInventory(inv);
    }

    private ItemStack makeSellStack(SellEntry e, int have, boolean saturated) {
        int actualPrice = saturated ? Math.max(1, e.price() / 2) : e.price();
        List<String> lore = new ArrayList<>(List.of(
            ChatColor.GOLD + "판매가: " + actualPrice + "포인트/개",
            ChatColor.AQUA + "보유: " + have + "개",
            ChatColor.GRAY + "클릭: 1개 판매"
        ));
        if (saturated) lore.add(1, ChatColor.RED + "⚠ 포화 상태 (-50%)");
        return item(e.mat(), 1, ChatColor.WHITE + e.name(), lore.toArray(new String[0]));
    }

    /** 상점 UI 슬롯에 표시할 구매 아이템 (UI 태그 포함 — 금고에 저장되면 필터링됨). */
    private ItemStack makeBuyStack(BuyEntry e) {
        if (e.isPotion()) {
            ItemStack pot = new ItemStack(e.mat());
            PotionMeta meta = (PotionMeta) pot.getItemMeta();
            if (meta != null) {
                meta.setBasePotionType(e.potionType());
                meta.setDisplayName(ChatColor.WHITE + e.name());
                meta.setLore(List.of(
                    ChatColor.GOLD + "구매가: " + e.price() + "포인트",
                    ChatColor.GRAY + "클릭: 구매"));
                meta.getPersistentDataContainer().set(uiItemKey, PersistentDataType.BYTE, (byte) 1);
                pot.setItemMeta(meta);
            }
            return pot;
        }
        return item(e.mat(), e.amount(),
            ChatColor.WHITE + e.name(),
            ChatColor.GOLD  + "구매가: " + e.price() + "포인트",
            ChatColor.GRAY  + "클릭: 구매");
    }

    /** 실제로 플레이어에게 지급되는 구매 아이템 (UI 태그 없음 — 금고 보관 가능). */
    private ItemStack makeGivenBuyStack(BuyEntry e) {
        if (e.isPotion()) {
            ItemStack pot = new ItemStack(e.mat());
            PotionMeta meta = (PotionMeta) pot.getItemMeta();
            if (meta != null) { meta.setBasePotionType(e.potionType()); pot.setItemMeta(meta); }
            return pot;
        }
        return new ItemStack(e.mat(), e.amount());
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
            && !title.equals(T_TP)   && !title.equals(T_RECRUIT)
            && !title.startsWith(T_UPGRADE)) return;

        event.setCancelled(true);

        // 플레이어 자신의 인벤토리 칸 클릭은 무시 (상점 로직 오작동 방지)
        if (event.getClickedInventory() == null ||
                !event.getView().getTopInventory().equals(event.getClickedInventory())) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        boolean inUpgradeMenu = state.type() == MenuType.UPGRADE;
        if (clicked.getType() == Material.BARRIER
            || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE
            || (clicked.getType() == Material.YELLOW_STAINED_GLASS_PANE && !inUpgradeMenu)
            || clicked.getType() == Material.LIME_STAINED_GLASS_PANE
            || (clicked.getType() == Material.LIGHT_BLUE_STAINED_GLASS_PANE && !inUpgradeMenu)
            || (clicked.getType() == Material.PURPLE_STAINED_GLASS_PANE && !inUpgradeMenu)) return;
        // 상점 뒤로가기 ARROW — 슬롯 0에서만 처리
        if (clicked.getType() == Material.ARROW
            && event.getView().getTitle().equals(T_SHOP) && event.getSlot() == 0) {
            var st = playerMenus.get(player.getUniqueId());
            if (st != null) { player.closeInventory(); openMain(player, st.flagId()); }
            return;
        }

        switch (state.type()) {
            case MAIN     -> handleMainClick(player, state.flagId(), event.getSlot());
            case SHOP     -> handleShopClick(player, state, event.getSlot(), event.getInventory());
            case TELEPORT -> handleTeleportClick(player, state, event.getSlot());
            case RECRUIT  -> handleRecruitClick(player, state, event.getSlot());
            case UPGRADE  -> handleUpgradeClick(player, state.flagId(), event.getSlot());
            default -> {}
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
            case 4 -> { // 업그레이드 메뉴
                if (team == null) { player.sendMessage(MessageUtil.warn("팀에 속해야 합니다.")); return; }
                Flag flag = flagManager.getFlagById(flagId);
                if (flag == null || !flag.isOwnedBy(team.getId())) {
                    player.sendMessage(MessageUtil.warn("자신의 깃발에서만 업그레이드 가능")); return;
                }
                player.closeInventory();
                openUpgrade(player, flagId);
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

    // ─── 업그레이드 메뉴 ─────────────────────────────────────────────────────
    // 레이아웃 (54칸, 6행 9열):
    //  Row 0: [뒤로] [glass*4] [타이틀] [FP표시] [glass*2]
    //  Row 1-5: [카테고리 아이콘] [Lv1][Lv2][Lv3][Lv4][Lv5] [glass*3]

    private void openUpgrade(Player player, int flagId) {
        Team team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) return;

        Inventory inv = Bukkit.createInventory(null, 54,
            T_UPGRADE + " [" + FlagManager.getDisplayName(flagId) + "]");

        // Row 0: 헤더
        inv.setItem(0, item(Material.ARROW, ChatColor.RED + "뒤로 가기"));
        for (int i = 1; i <= 3; i++) inv.setItem(i, glass(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(4, glass(Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            ChatColor.AQUA + "" + ChatColor.BOLD + "업그레이드 메뉴"));
        inv.setItem(5, glass(Material.PURPLE_STAINED_GLASS_PANE,
            ChatColor.LIGHT_PURPLE + "FP: " + team.getFlagpoint() + "개"));
        for (int i = 6; i <= 8; i++) inv.setItem(i, glass(Material.GRAY_STAINED_GLASS_PANE, " "));

        // Row 1-5: 각 업그레이드 종류
        UpgradeType[] types = UpgradeType.values();
        for (int row = 0; row < types.length; row++) {
            UpgradeType type = types[row];
            int currentLevel = upgradeManager.getLevel(team.getId(), flagId, type);
            int baseSlot = 9 + row * 9;

            // 카테고리 아이콘 (col 0)
            inv.setItem(baseSlot, item(type.icon, type.color + "" + ChatColor.BOLD + type.displayName,
                ChatColor.GRAY + type.description,
                ChatColor.WHITE + "현재 레벨: " + currentLevel + "/" + UpgradeType.MAX_LEVEL,
                ChatColor.GRAY + type.levelDesc[0],
                ChatColor.GRAY + type.levelDesc[1],
                ChatColor.GRAY + type.levelDesc[2]));

            // 레벨 버튼 (col 1-5)
            for (int lv = 1; lv <= UpgradeType.MAX_LEVEL; lv++) {
                int slot = baseSlot + lv;
                if (lv <= currentLevel) {
                    // 완료된 레벨 (녹색)
                    inv.setItem(slot, glass(Material.LIME_STAINED_GLASS_PANE,
                        ChatColor.GREEN + "레벨 " + lv + " ✔"));
                } else if (lv == currentLevel + 1) {
                    // 다음 구매 가능한 레벨 (노랑)
                    inv.setItem(slot, glass(Material.YELLOW_STAINED_GLASS_PANE,
                        ChatColor.YELLOW + "레벨 " + lv + " ▶ 구매",
                        ChatColor.LIGHT_PURPLE + "비용: FP " + UpgradeType.FP_COST + "개",
                        ChatColor.WHITE + "보유: " + team.getFlagpoint() + "개"));
                } else {
                    // 잠금
                    inv.setItem(slot, glass(Material.GRAY_STAINED_GLASS_PANE,
                        ChatColor.DARK_GRAY + "레벨 " + lv + " 🔒"));
                }
            }

            // 나머지 칸 (col 6-8) 구분선
            for (int c = 6; c <= 8; c++) inv.setItem(baseSlot + c, glass(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        playerMenus.put(player.getUniqueId(),
            new MenuState(flagId, MenuType.UPGRADE, List.of(), List.of()));
        player.openInventory(inv);
    }

    private void handleUpgradeClick(Player player, int flagId, int slot) {
        if (slot == 0) { player.closeInventory(); openMain(player, flagId); return; }
        if (slot < 9) return; // 헤더 무시

        int row = (slot - 9) / 9;     // 0-4 → 업그레이드 종류
        int col = (slot - 9) % 9;     // 1-5 → 레벨 버튼
        if (col == 0 || col > 5) return; // 아이콘/구분선 무시

        UpgradeType[] types = UpgradeType.values();
        if (row >= types.length) return;
        UpgradeType type = types[row];
        int targetLevel = col;

        Team team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) return;
        int currentLevel = upgradeManager.getLevel(team.getId(), flagId, type);
        if (targetLevel != currentLevel + 1) return; // 순서대로만 구매 가능

        String err = upgradeManager.upgrade(player, flagId, type);
        if (err != null) {
            player.sendMessage(MessageUtil.warn(err)); return;
        }

        player.sendMessage(MessageUtil.success(
            type.displayName + " 레벨 " + targetLevel + " 업그레이드 완료! (FP 잔여: " + team.getFlagpoint() + ")"));
        plugin.getServer().broadcastMessage(MessageUtil.info(
            "[" + team.getId() + "] " + FlagManager.getDisplayName(flagId)
            + " " + type.displayName + " Lv" + targetLevel));
        player.closeInventory();
        openUpgrade(player, flagId); // 메뉴 갱신
    }

    private ItemStack glass(Material mat, String name, String... lore) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            if (lore.length > 0) m.setLore(Arrays.asList(lore));
            m.getPersistentDataContainer().set(uiItemKey, PersistentDataType.BYTE, (byte) 1);
            s.setItemMeta(m);
        }
        return s;
    }

    private void handleShopClick(Player player, MenuState state, int slot, Inventory inv) {
        Team team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) { player.sendMessage(MessageUtil.warn("팀에 속해야 합니다.")); return; }

        // 판매 (slots 9-26)
        if (slot >= 9 && slot <= 26) {
            int idx = slot - 9;
            if (idx >= SELL_ITEMS.size()) return;
            SellEntry e = SELL_ITEMS.get(idx);
            if (countItem(player, e.mat()) < 1) {
                player.sendMessage(MessageUtil.warn("판매할 " + e.name() + " 이(가) 없습니다.")); return;
            }
            if (!player.getInventory().removeItem(new ItemStack(e.mat(), 1)).isEmpty()) {
                player.sendMessage(MessageUtil.warn("아이템 제거 실패")); return;
            }

            String cat    = e.category();
            String satCat = saturatedCategory.getOrDefault(team.getId(), null);
            boolean isSat = cat.equals(satCat);
            int actualPrice = isSat ? Math.max(1, e.price() / 2) : e.price();
            team.addPoint(actualPrice);

            String msg = MessageUtil.success(e.name() + " 판매 (+" + actualPrice + "pt → 팀: " + team.getPoint() + ")");
            if (isSat) msg += ChatColor.RED + " [포화 -50%]";
            player.sendMessage(msg);

            // 판매 누적 → 포화 전환 확인
            Map<String, Integer> counts = saleCounts.computeIfAbsent(team.getId(), k -> new HashMap<>());
            int newCount = counts.merge(cat, 1, Integer::sum);
            boolean satChanged = false;
            if (newCount >= SATURATION_THRESHOLD && !cat.equals(satCat)) {
                String oldSat = satCat;
                saturatedCategory.put(team.getId(), cat);
                counts.clear();
                satChanged = true;
                notifyTeamPlayers(team, MessageUtil.warn("[" + cat + "] 분야 포화! 이 분야 판매가 50% 감소"));
                notifyTeamPlayers(team, MessageUtil.info("다른 분야를 포화시키면 [" + cat + "] 분야가 해제됩니다."));
                if (oldSat != null)
                    notifyTeamPlayers(team, MessageUtil.success("[" + oldSat + "] 포화 해제! 가격 정상화"));
            }

            if (satChanged) {
                player.closeInventory();
                openShop(player, state.flagId());
            } else {
                inv.setItem(slot, makeSellStack(e, countItem(player, e.mat()), isSat));
            }
            return;
        }

        // 구매 (slots 36-51)
        if (slot >= 36 && slot <= 51) {
            int idx = slot - 36;
            if (idx >= BUY_ITEMS.size()) return;
            BuyEntry e = BUY_ITEMS.get(idx);
            if (team.getPoint() < e.price()) {
                player.sendMessage(MessageUtil.warn("포인트 부족 (필요: " + e.price() + ", 보유: " + team.getPoint() + ")")); return;
            }
            team.addPoint(-e.price());
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(makeGivenBuyStack(e));
            if (!leftover.isEmpty()) player.getWorld().dropItem(player.getLocation(), leftover.get(0));
            player.sendMessage(MessageUtil.success(e.name() + " 구매 (남은 포인트: " + team.getPoint() + ")"));
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
            vaultManager.saveVault(vaultTeamId, event.getView().getTopInventory());
        }
    }

    // ─── 낚시 제한 (깃발 영역 내부에서만 가능) ──────────────────────────────────

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) return;
        GameManager gm = plugin.getGameManager();
        if (gm == null || !gm.isRunning()) return;
        Player p = event.getPlayer();
        if (isInAnyFlagZone(p.getLocation())) return;
        event.setCancelled(true);
        event.getHook().remove();
        p.sendMessage(MessageUtil.warn("낚시는 깃발 영역 내부에서만 가능합니다."));
    }

    private boolean isInAnyFlagZone(Location loc) {
        double zone = FlagZoneDisplay.ZONE_HALF;
        for (Flag flag : flagManager.getAllFlags().values()) {
            if (flag.isNeutral()) continue;
            Location center = flag.getLocation();
            if (!center.getWorld().equals(loc.getWorld())) continue;
            if (Math.abs(loc.getX() - center.getX()) <= zone
             && Math.abs(loc.getZ() - center.getZ()) <= zone) return true;
        }
        return false;
    }

    // ─── 유틸 ────────────────────────────────────────────────────────────────

    private void notifyTeamPlayers(Team team, String message) {
        for (UUID uuid : team.getMembers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        }
    }

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
        if (m != null) {
            m.setDisplayName(name);
            m.getPersistentDataContainer().set(uiItemKey, PersistentDataType.BYTE, (byte) 1);
            s.setItemMeta(m);
        }
        return s;
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            if (lore.length > 0) m.setLore(Arrays.asList(lore));
            m.getPersistentDataContainer().set(uiItemKey, PersistentDataType.BYTE, (byte) 1);
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
            m.getPersistentDataContainer().set(uiItemKey, PersistentDataType.BYTE, (byte) 1);
            s.setItemMeta(m);
        }
        return s;
    }
}
