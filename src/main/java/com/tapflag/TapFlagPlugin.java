package com.tapflag;

import com.tapflag.command.TapFlagCommand;
import com.tapflag.flag.FlagListener;
import com.tapflag.flag.FlagManager;
import com.tapflag.flag.FlagMenuListener;
import com.tapflag.flag.FlagZoneDisplay;
import com.tapflag.hud.HudManager;
import com.tapflag.world.OreManager;
import com.tapflag.listener.BuildListener;
import com.tapflag.listener.CraftListener;
import com.tapflag.listener.DeathBanListener;
import com.tapflag.listener.LockdownListener;
import com.tapflag.listener.NetherEndListener;
import com.tapflag.listener.PlayerLoginListener;
import com.tapflag.team.TeamManager;
import com.tapflag.timer.GameTimer;
import com.tapflag.vault.VaultManager;
import org.bukkit.plugin.java.JavaPlugin;

public class TapFlagPlugin extends JavaPlugin {

    private TeamManager teamManager;
    private FlagManager flagManager;
    private GameTimer gameTimer;
    private GameManager gameManager;
    private OreManager oreManager;
    private HudManager hudManager;
    private VaultManager vaultManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        teamManager  = new TeamManager(this);
        gameTimer    = new GameTimer(this);
        flagManager  = new FlagManager(this, teamManager);
        oreManager   = new OreManager(this);
        gameManager  = new GameManager(this, teamManager, flagManager, gameTimer, oreManager);
        hudManager   = new HudManager(this, teamManager);
        vaultManager = new VaultManager(this);
        hudManager.runTaskTimer(this, 0L, 20L);

        var pm = getServer().getPluginManager();
        pm.registerEvents(hudManager, this);
        pm.registerEvents(new FlagListener(flagManager, teamManager, gameTimer), this);
        pm.registerEvents(new NetherEndListener(), this);
        pm.registerEvents(new DeathBanListener(this, hudManager), this);
        pm.registerEvents(new PlayerLoginListener(gameManager, teamManager), this);
        pm.registerEvents(new LockdownListener(this, gameTimer, gameManager, flagManager, teamManager), this);
        pm.registerEvents(new BuildListener(this, gameManager, flagManager, teamManager), this);
        pm.registerEvents(new FlagMenuListener(this, flagManager, teamManager, vaultManager), this);
        pm.registerEvents(new CraftListener(), this);

        var cmd = new TapFlagCommand(this, teamManager, flagManager, gameTimer, gameManager);
        var tapFlagCmd = getCommand("tapflag");
        if (tapFlagCmd != null) {
            tapFlagCmd.setExecutor(cmd);
            tapFlagCmd.setTabCompleter(cmd);
        }

        // 깃발 구역 파티클 표시 + 발광 효과 (60틱 = 3초마다)
        new FlagZoneDisplay(this, flagManager, teamManager)
            .runTaskTimer(this, 60L, 60L);

        getLogger().info("TapFlag 활성화 완료.");
    }

    @Override
    public void onDisable() {
        if (gameTimer != null) gameTimer.stop();
        if (flagManager != null) flagManager.save();
        if (teamManager != null) teamManager.save();
        getLogger().info("TapFlag 비활성화.");
    }

    public TeamManager getTeamManager() { return teamManager; }
    public FlagManager getFlagManager() { return flagManager; }
    public GameTimer getGameTimer()     { return gameTimer; }
    public GameManager getGameManager() { return gameManager; }
    public OreManager getOreManager()  { return oreManager; }
    public HudManager getHudManager()  { return hudManager; }
}
