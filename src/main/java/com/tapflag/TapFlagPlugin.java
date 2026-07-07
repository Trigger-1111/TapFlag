package com.tapflag;

import com.tapflag.command.TapFlagCommand;
import com.tapflag.flag.FlagBeaconTask;
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
import com.tapflag.listener.MovementLimitListener;
import com.tapflag.listener.NetherEndListener;
import com.tapflag.listener.PlayerLoginListener;
import com.tapflag.team.TeamManager;
import com.tapflag.timer.BossBarManager;
import com.tapflag.timer.GameTimer;
import com.tapflag.upgrade.FlagUpgradeManager;
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
    private BossBarManager bossBarManager;
    private FlagUpgradeManager flagUpgradeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // config 값으로 영역 반경 초기화 (다른 매니저 생성 전)
        FlagZoneDisplay.ZONE_HALF   = getConfig().getInt("flag.zone-radius",   50) / 2.0;
        FlagZoneDisplay.DETECT_HALF = getConfig().getInt("flag.detect-radius", 100) / 2.0;

        teamManager  = new TeamManager(this);
        gameTimer    = new GameTimer(this);
        flagManager  = new FlagManager(this, teamManager);
        oreManager   = new OreManager(this);
        gameManager  = new GameManager(this, teamManager, flagManager, gameTimer, oreManager);
        hudManager   = new HudManager(this, teamManager);
        vaultManager = new VaultManager(this);
        hudManager.runTaskTimer(this, 0L, 20L);

        flagUpgradeManager = new FlagUpgradeManager(this, flagManager, teamManager);
        flagUpgradeManager.runTaskTimer(this, 20L, 20L);

        var pm = getServer().getPluginManager();
        pm.registerEvents(hudManager, this);
        pm.registerEvents(flagUpgradeManager, this);
        pm.registerEvents(new FlagListener(flagManager, teamManager, gameTimer, flagUpgradeManager), this);
        pm.registerEvents(new NetherEndListener(), this);
        pm.registerEvents(new DeathBanListener(this, hudManager), this);
        pm.registerEvents(new PlayerLoginListener(gameManager, teamManager), this);
        pm.registerEvents(new LockdownListener(this, gameTimer, gameManager, flagManager, teamManager), this);
        pm.registerEvents(new MovementLimitListener(gameManager), this);
        pm.registerEvents(new BuildListener(this, gameManager, flagManager, teamManager), this);
        pm.registerEvents(new FlagMenuListener(this, flagManager, teamManager, vaultManager, flagUpgradeManager), this);
        pm.registerEvents(new CraftListener(), this);

        var cmd = new TapFlagCommand(this, teamManager, flagManager, gameTimer, gameManager);
        var tapFlagCmd = getCommand("tapflag");
        if (tapFlagCmd != null) {
            tapFlagCmd.setExecutor(cmd);
            tapFlagCmd.setTabCompleter(cmd);
        }

        // 깃발 구역 파티클 표시 + 발광 효과 (30틱 = 1.5초마다)
        new FlagZoneDisplay(this, flagManager, teamManager)
            .runTaskTimer(this, 30L, 30L);

        // 깃발 위 신호기 빛기둥 (20틱 = 1초마다)
        new FlagBeaconTask(this).runTaskTimer(this, 20L, 20L);

        // 점령 단계 보스바 (20틱 = 1초마다)
        bossBarManager = new BossBarManager(this);
        bossBarManager.runTaskTimer(this, 20L, 20L);

        getLogger().info("TapFlag 활성화 완료.");
    }

    @Override
    public void onDisable() {
        if (gameTimer != null) gameTimer.stop();
        if (flagManager != null) flagManager.save();
        if (teamManager != null) teamManager.save();
        getLogger().info("TapFlag 비활성화.");
    }

    public TeamManager getTeamManager()       { return teamManager; }
    public FlagManager getFlagManager()       { return flagManager; }
    public GameTimer getGameTimer()           { return gameTimer; }
    public GameManager getGameManager()       { return gameManager; }
    public OreManager getOreManager()         { return oreManager; }
    public HudManager getHudManager()         { return hudManager; }
    public BossBarManager getBossBarManager()         { return bossBarManager; }
    public FlagUpgradeManager getFlagUpgradeManager() { return flagUpgradeManager; }
}
