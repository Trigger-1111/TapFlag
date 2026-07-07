package com.tapflag.upgrade;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum UpgradeType {

    DURABILITY   ("내구력",  Material.SHIELD,    ChatColor.AQUA,
        "적 접근을 막는 보호막 생성",
        new String[]{"Lv1: 보호막 HP 250","Lv3: 보호막 HP 750","Lv5: 보호막 HP 1250"}),

    FORTIFICATION("요새화",  Material.CROSSBOW,  ChatColor.RED,
        "적을 공격하는 발리스타 터렛 설치",
        new String[]{"Lv1: 터렛 1개","Lv3: 터렛 3개","Lv5: 터렛 5개"}),

    PRODUCTION   ("생산량",  Material.WHEAT,     ChatColor.GREEN,
        "영역 내 경작지/동물 최대치 증가",
        new String[]{"Lv1: 경작지 20/동물 4","Lv3: 경작지 60/동물 12","Lv5: 경작지 100/동물 20"}),

    SUPPORT      ("지원",    Material.BEACON,    ChatColor.YELLOW,
        "영역 내 아군 버프 적용",
        new String[]{"Lv1: 신속II","Lv3: +재생I","Lv5: +흡수I"}),

    REPAIR       ("수리",    Material.ANVIL,     ChatColor.GRAY,
        "깃발·발리스타 HP 회복 + 최대 HP 증가",
        new String[]{"Lv1: +50 MaxHP","Lv3: +150 MaxHP","Lv5: +250 MaxHP"});

    public final String displayName;
    public final Material icon;
    public final ChatColor color;
    public final String description;
    public final String[] levelDesc;
    public static final int MAX_LEVEL = 5;
    public static final int FP_COST   = 1; // 레벨당 flagpoint 비용

    UpgradeType(String displayName, Material icon, ChatColor color,
                String description, String[] levelDesc) {
        this.displayName = displayName;
        this.icon        = icon;
        this.color       = color;
        this.description = description;
        this.levelDesc   = levelDesc;
    }
}
