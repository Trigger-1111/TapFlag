package com.tapflag.command;

import com.tapflag.GameManager;
import com.tapflag.TapFlagPlugin;
import com.tapflag.flag.FlagManager;
import com.tapflag.team.TeamManager;
import com.tapflag.timer.GameTimer;
import com.tapflag.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /tapflag 명령어 트리
 *
 * start <팀수> / stop
 * team  : create / list / disband / join / info
 * flag  : place / list / info / hit / capture / reset / remove
 * timer : start / stop / status / capture
 * status / reload
 */
public class TapFlagCommand implements CommandExecutor, TabCompleter {

    private final TapFlagPlugin plugin;
    private final TeamManager teamManager;
    private final FlagManager flagManager;
    private final GameTimer gameTimer;
    private final GameManager gameManager;

    public TapFlagCommand(TapFlagPlugin plugin, TeamManager teamManager,
                          FlagManager flagManager, GameTimer gameTimer, GameManager gameManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.flagManager = flagManager;
        this.gameTimer = gameTimer;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        return switch (args[0].toLowerCase()) {
            case "start"    -> handleStart(sender, args);
            case "stop"     -> handleStop(sender);
            case "team"     -> handleTeam(sender, args);
            case "flag"     -> handleFlag(sender, args);
            case "timer"    -> handleTimer(sender, args);
            case "status"   -> handleStatus(sender);
            case "reload"   -> handleReload(sender);
            case "playtest" -> handlePlaytest(sender, args);
            default -> { sender.sendMessage(MessageUtil.error("알 수 없는 명령어: " + args[0])); yield true; }
        };
    }

    // ─── start / stop ────────────────────────────────────────────────────────

    private boolean handleStart(CommandSender sender, String[] args) {
        // /tapflag start <팀장1> <팀장2> [팀장3] [팀장4] [팀장5]
        if (args.length < 3 || args.length > 6) {
            sender.sendMessage(MessageUtil.error("사용법: /tapflag start <팀장1> <팀장2> [팀장3] [팀장4] [팀장5]"));
            return true;
        }
        List<UUID> leaders = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Player p = plugin.getServer().getPlayer(args[i]);
            if (p == null) {
                sender.sendMessage(MessageUtil.error("온라인 플레이어를 찾을 수 없음: " + args[i]));
                return true;
            }
            leaders.add(p.getUniqueId());
        }
        String error = gameManager.startGame(leaders);
        if (error != null) sender.sendMessage(MessageUtil.error(error));
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!gameManager.isRunning()) {
            sender.sendMessage(MessageUtil.warn("진행 중인 게임이 없습니다."));
            return true;
        }
        gameManager.stopGame();
        return true;
    }

    // ─── team ────────────────────────────────────────────────────────────────

    private boolean handleTeam(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.error("사용법: /tapflag team <create|list|disband|join|info>"));
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "create" -> {
                requireArg(sender, args, 3, "/tapflag team create <id>");
                Player p = requirePlayer(sender);
                if (p == null) yield true;
                var team = teamManager.createTeam(args[2], p.getUniqueId());
                if (team == null) {
                    sender.sendMessage(MessageUtil.error("이미 존재하는 팀 ID: " + args[2]));
                } else {
                    sender.sendMessage(MessageUtil.success("팀 [" + args[2] + "] 생성 완료. 리더: " + p.getName()));
                    teamManager.save();
                }
                yield true;
            }
            case "list" -> {
                var list = teamManager.getAllTeams();
                if (list.isEmpty()) { sender.sendMessage(MessageUtil.info("등록된 팀이 없습니다.")); yield true; }
                sender.sendMessage(MessageUtil.info("=== 팀 목록 ==="));
                for (var t : list) {
                    sender.sendMessage(ChatColor.YELLOW + "• [" + t.getId() + "] "
                        + "멤버:" + t.getMembers().size()
                        + " 깃발:" + t.getFlagCount()
                        + " 상태:" + t.getState().name());
                }
                yield true;
            }
            case "disband" -> {
                if (!requireArg(sender, args, 3, "/tapflag team disband <id>")) yield true;
                if (teamManager.disbandTeam(args[2])) {
                    sender.sendMessage(MessageUtil.success("팀 [" + args[2] + "] 해체 완료."));
                    teamManager.save();
                } else {
                    sender.sendMessage(MessageUtil.error("팀을 찾을 수 없음: " + args[2]));
                }
                yield true;
            }
            case "join" -> {
                if (!requireArg(sender, args, 3, "/tapflag team join <id>")) yield true;
                Player p = requirePlayer(sender);
                if (p == null) yield true;
                if (teamManager.getTeam(args[2]) == null) {
                    sender.sendMessage(MessageUtil.error("팀을 찾을 수 없음: " + args[2]));
                } else {
                    teamManager.addToTeam(args[2], p.getUniqueId());
                    sender.sendMessage(MessageUtil.success("[" + args[2] + "] 팀에 합류했습니다."));
                    teamManager.save();
                }
                yield true;
            }
            case "info" -> {
                if (!requireArg(sender, args, 3, "/tapflag team info <id>")) yield true;
                var t = teamManager.getTeam(args[2]);
                if (t == null) { sender.sendMessage(MessageUtil.error("팀을 찾을 수 없음: " + args[2])); yield true; }
                sender.sendMessage(MessageUtil.info("=== 팀 [" + t.getId() + "] ==="));
                sender.sendMessage(ChatColor.YELLOW + "상태: " + t.getState().name());
                sender.sendMessage(ChatColor.YELLOW + "멤버: " + t.getMembers().size() + "명");
                sender.sendMessage(ChatColor.YELLOW + "소유 깃발: " + t.getOwnedFlagIds());
                yield true;
            }
            case "recruit" -> {
                if (!requireArg(sender, args, 3, "/tapflag team recruit <playerName>")) yield true;
                Player p = requirePlayer(sender);
                if (p == null) yield true;
                String error = gameManager.recruitPlayer(p, args[2]);
                if (error != null) {
                    sender.sendMessage(MessageUtil.error(error));
                } else {
                    sender.sendMessage(MessageUtil.success(args[2] + " 팀원 영입 완료."));
                }
                yield true;
            }
            default -> { sender.sendMessage(MessageUtil.error("알 수 없는 team 명령: " + args[1])); yield true; }
        };
    }

    // ─── flag ────────────────────────────────────────────────────────────────

    private boolean handleFlag(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.error("사용법: /tapflag flag <place|list|info|hit|capture|reset|remove>"));
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "place" -> {
                if (!requireArg(sender, args, 3, "/tapflag flag place <id>")) yield true;
                Player p = requirePlayer(sender);
                if (p == null) yield true;
                int id = parseInt(sender, args[2]); if (id < 0) yield true;
                var flag = flagManager.placeFlag(id, p.getLocation().getBlock().getLocation().add(0, 0, 0));
                sender.sendMessage(MessageUtil.success("깃발 #" + id + " 배치 완료 ("
                    + (int)p.getLocation().getX() + "," + (int)p.getLocation().getY() + "," + (int)p.getLocation().getZ() + ")"));
                yield true;
            }
            case "list" -> {
                var all = flagManager.getAllFlags();
                if (all.isEmpty()) { sender.sendMessage(MessageUtil.info("배치된 깃발이 없습니다.")); yield true; }
                sender.sendMessage(MessageUtil.info("=== 깃발 목록 ==="));
                for (var f : all.values()) {
                    sender.sendMessage(ChatColor.YELLOW + "• #" + f.getId()
                        + " HP:" + f.getHp() + "/" + f.getMaxHp()
                        + " 팀:" + (f.isNeutral() ? "중립" : f.getOwningTeamId()));
                }
                yield true;
            }
            case "info" -> {
                if (!requireArg(sender, args, 3, "/tapflag flag info <id>")) yield true;
                int id = parseInt(sender, args[2]); if (id < 0) yield true;
                var f = flagManager.getFlagById(id);
                if (f == null) { sender.sendMessage(MessageUtil.error("깃발을 찾을 수 없음: #" + id)); yield true; }
                var loc = f.getLocation();
                sender.sendMessage(MessageUtil.info("=== 깃발 #" + id + " ==="));
                sender.sendMessage(ChatColor.YELLOW + "HP: " + f.getHp() + "/" + f.getMaxHp());
                sender.sendMessage(ChatColor.YELLOW + "팀: " + (f.isNeutral() ? "중립" : f.getOwningTeamId()));
                sender.sendMessage(ChatColor.YELLOW + "위치: " + loc.getWorld().getName()
                    + " " + (int)loc.getX() + "," + (int)loc.getY() + "," + (int)loc.getZ());
                yield true;
            }
            case "hit" -> {
                if (!requireArg(sender, args, 4, "/tapflag flag hit <id> <damage>")) yield true;
                Player p = requirePlayer(sender); if (p == null) yield true;
                int id = parseInt(sender, args[2]); if (id < 0) yield true;
                int dmg = parseInt(sender, args[3]); if (dmg < 0) yield true;
                flagManager.hitFlag(id, p.getUniqueId(), dmg);
                var f = flagManager.getFlagById(id);
                sender.sendMessage(MessageUtil.info("깃발 #" + id + " 피격 (" + dmg + "). HP: "
                    + (f != null ? f.getHp() + "/" + f.getMaxHp() : "?")));
                yield true;
            }
            case "capture" -> {
                if (!requireArg(sender, args, 4, "/tapflag flag capture <id> <teamId>")) yield true;
                int id = parseInt(sender, args[2]); if (id < 0) yield true;
                flagManager.captureFlag(id, args[3]);
                sender.sendMessage(MessageUtil.success("깃발 #" + id + " → [" + args[3] + "] 강제 점령."));
                yield true;
            }
            case "reset" -> {
                if (!requireArg(sender, args, 3, "/tapflag flag reset <id>")) yield true;
                int id = parseInt(sender, args[2]); if (id < 0) yield true;
                var f = flagManager.getFlagById(id);
                if (f == null) { sender.sendMessage(MessageUtil.error("깃발을 찾을 수 없음: #" + id)); yield true; }
                f.resetHp();
                flagManager.save();
                sender.sendMessage(MessageUtil.success("깃발 #" + id + " HP 초기화."));
                yield true;
            }
            case "remove" -> {
                if (!requireArg(sender, args, 3, "/tapflag flag remove <id>")) yield true;
                int id = parseInt(sender, args[2]); if (id < 0) yield true;
                if (flagManager.getFlagById(id) == null) {
                    sender.sendMessage(MessageUtil.error("깃발을 찾을 수 없음: #" + id)); yield true;
                }
                flagManager.removeFlag(id);
                sender.sendMessage(MessageUtil.success("깃발 #" + id + " 제거."));
                yield true;
            }
            default -> { sender.sendMessage(MessageUtil.error("알 수 없는 flag 명령: " + args[1])); yield true; }
        };
    }

    // ─── timer ───────────────────────────────────────────────────────────────

    private boolean handleTimer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.error("사용법: /tapflag timer <start|stop|status|capture>"));
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "start" -> {
                gameTimer.start();
                sender.sendMessage(MessageUtil.success("게임 타이머 시작."));
                yield true;
            }
            case "stop" -> {
                gameTimer.stop();
                sender.sendMessage(MessageUtil.success("게임 타이머 정지."));
                yield true;
            }
            case "status" -> {
                sender.sendMessage(MessageUtil.info("타이머: " + gameTimer.getStatusDisplay()));
                yield true;
            }
            case "capture" -> {
                if (!requireArg(sender, args, 3, "/tapflag timer capture <on|off>")) yield true;
                boolean on = args[2].equalsIgnoreCase("on");
                gameTimer.setForceCapture(on);
                sender.sendMessage(MessageUtil.success("점령 강제 모드: " + (on ? "ON" : "OFF")));
                yield true;
            }
            default -> { sender.sendMessage(MessageUtil.error("알 수 없는 timer 명령: " + args[1])); yield true; }
        };
    }

    // ─── playtest ────────────────────────────────────────────────────────────

    private boolean handlePlaytest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.error("사용법: /tapflag playtest <setup|stop|skipban|jointeam>"));
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "setup" -> {
                Player p = requirePlayer(sender);
                if (p == null) yield true;
                gameManager.startPlaytest(p);
                yield true;
            }
            case "stop" -> {
                gameManager.stopPlaytest();
                sender.sendMessage(MessageUtil.success("플레이테스트 종료."));
                yield true;
            }
            case "skipban" -> {
                Player p = requirePlayer(sender);
                if (p == null) yield true;
                gameManager.removePending(p.getUniqueId());
                sender.sendMessage(MessageUtil.success("대기 차단 해제: " + p.getName()));
                yield true;
            }
            case "jointeam" -> {
                if (!requireArg(sender, args, 3, "/tapflag playtest jointeam <팀id>")) yield true;
                Player p = requirePlayer(sender);
                if (p == null) yield true;
                String err = gameManager.playtestJoinTeam(p, args[2]);
                if (err != null) {
                    sender.sendMessage(MessageUtil.error(err));
                } else {
                    sender.sendMessage(MessageUtil.success("[" + args[2] + "] 팀으로 전환됨."));
                }
                yield true;
            }
            default -> { sender.sendMessage(MessageUtil.error("알 수 없는 playtest 명령: " + args[1])); yield true; }
        };
    }

    // ─── status / reload ─────────────────────────────────────────────────────

    private boolean handleStatus(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("=== TapFlag 게임 상태 ==="));
        sender.sendMessage(ChatColor.YELLOW + "타이머 : " + gameTimer.getStatusDisplay());
        sender.sendMessage(ChatColor.YELLOW + "팀 수  : " + teamManager.getAllTeams().size());
        sender.sendMessage(ChatColor.YELLOW + "방랑자 : " + teamManager.getWanderers().size() + "명");
        sender.sendMessage(ChatColor.YELLOW + "깃발   : " + flagManager.getAllFlags().size() + "/" + 5);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(MessageUtil.success("config.yml 리로드 완료."));
        return true;
    }

    // ─── 도우미 ──────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.info("=== TapFlag 명령어 ==="));
        sender.sendMessage(ChatColor.YELLOW + "/tapflag start <팀수>             " + ChatColor.WHITE + "게임 시작 (최소 2팀)");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag stop                     " + ChatColor.WHITE + "게임 중지");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag team create <id>         " + ChatColor.WHITE + "팀 생성");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag team list                " + ChatColor.WHITE + "팀 목록");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag team disband <id>        " + ChatColor.WHITE + "팀 해체");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag team join <id>           " + ChatColor.WHITE + "팀 합류");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag flag place <id>          " + ChatColor.WHITE + "현위치에 깃발 배치");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag flag list                " + ChatColor.WHITE + "깃발 목록");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag flag hit <id> <damage>   " + ChatColor.WHITE + "깃발 피격 테스트");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag flag capture <id> <team> " + ChatColor.WHITE + "강제 점령");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag flag reset <id>          " + ChatColor.WHITE + "HP 초기화");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag flag remove <id>         " + ChatColor.WHITE + "깃발 제거");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag timer start/stop/status  " + ChatColor.WHITE + "타이머 제어");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag timer capture <on|off>   " + ChatColor.WHITE + "점령 강제 설정");
        sender.sendMessage(ChatColor.YELLOW + "/tapflag status                   " + ChatColor.WHITE + "전체 상태");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage(MessageUtil.error("플레이어만 사용 가능한 명령어입니다."));
        return null;
    }

    private boolean requireArg(CommandSender sender, String[] args, int minLen, String usage) {
        if (args.length >= minLen) return true;
        sender.sendMessage(MessageUtil.error("사용법: " + usage));
        return false;
    }

    private int parseInt(CommandSender sender, String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.error("숫자를 입력하세요: " + s));
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1)
            return filter(List.of("start", "stop", "team", "flag", "timer", "status", "reload", "playtest"), args[0]);
        if (args.length == 2) return switch (args[0].toLowerCase()) {
            case "team"     -> filter(List.of("create","list","disband","join","info","recruit"), args[1]);
            case "flag"     -> filter(List.of("place","list","info","hit","capture","reset","remove"), args[1]);
            case "timer"    -> filter(List.of("start","stop","status","capture"), args[1]);
            case "playtest" -> filter(List.of("setup","stop","skipban","jointeam"), args[1]);
            default -> List.of();
        };
        if (args.length == 3 && args[0].equals("timer") && args[1].equals("capture"))
            return filter(List.of("on","off"), args[2]);
        return List.of();
    }

    private List<String> filter(List<String> opts, String prefix) {
        return opts.stream().filter(s -> s.startsWith(prefix.toLowerCase())).toList();
    }
}
