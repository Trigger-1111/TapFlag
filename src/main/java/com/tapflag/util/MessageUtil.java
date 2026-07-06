package com.tapflag.util;

import org.bukkit.ChatColor;

public final class MessageUtil {

    private static final String PREFIX =
        ChatColor.DARK_GRAY + "[" + ChatColor.RED + "TapFlag" + ChatColor.DARK_GRAY + "] " + ChatColor.RESET;

    private MessageUtil() {}

    public static String prefix() { return PREFIX; }

    public static String info(String msg)    { return PREFIX + ChatColor.WHITE  + msg; }
    public static String success(String msg) { return PREFIX + ChatColor.GREEN  + msg; }
    public static String error(String msg)   { return PREFIX + ChatColor.RED    + msg; }
    public static String warn(String msg)    { return PREFIX + ChatColor.YELLOW + msg; }
}
