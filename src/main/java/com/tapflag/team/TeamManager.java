package com.tapflag.team;

import com.tapflag.TapFlagPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {

    private final TapFlagPlugin plugin;
    private final Map<String, Team> teams = new HashMap<>();
    private final Set<UUID> wanderers = new HashSet<>();
    /** 방랑자 전환된 플레이어의 이전 팀 ID */
    private final Map<UUID, String> prevTeamMap = new HashMap<>();

    private final File teamsFile;
    private FileConfiguration teamsConfig;

    public TeamManager(TapFlagPlugin plugin) {
        this.plugin = plugin;
        this.teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        this.teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
        load();
    }

    // ─── 팀 생성 / 해체 ──────────────────────────────────────────────────────

    /** @return 생성된 Team, 이미 id 존재 시 null */
    public Team createTeam(String id, UUID leader) {
        if (teams.containsKey(id)) return null;
        Team team = new Team(id, leader);
        teams.put(id, team);
        wanderers.remove(leader);
        return team;
    }

    /** 팀 해체 — 멤버 전체를 방랑자로 전환하고 이전 팀 기록 */
    public boolean disbandTeam(String teamId) {
        Team team = teams.remove(teamId);
        if (team == null) return false;
        team.setState(TeamState.DISBANDED);
        for (UUID member : team.getMembers()) {
            prevTeamMap.put(member, teamId);
        }
        wanderers.addAll(team.getMembers());
        plugin.getLogger().info("Team [" + teamId + "] disbanded. " + team.getMembers().size() + " wanderers added.");
        // teams.yml 즉시 저장 (재접속 시 구 팀 데이터 로드 방지)
        save();
        // HUD 즉시 갱신 (방랑자 전환 표시)
        var hud = plugin.getHudManager();
        if (hud != null) hud.refreshAll();
        return true;
    }

    // ─── 조회 ────────────────────────────────────────────────────────────────

    public Team getTeam(String teamId)             { return teams.get(teamId); }
    public Collection<Team> getAllTeams()           { return teams.values(); }
    public Set<UUID> getWanderers()                { return wanderers; }
    public boolean isWanderer(UUID uuid)           { return wanderers.contains(uuid); }
    public String getPreviousTeam(UUID uuid)       { return prevTeamMap.get(uuid); }

    public Team getTeamByPlayer(UUID uuid) {
        for (Team t : teams.values()) {
            if (t.containsPlayer(uuid)) return t;
        }
        return null;
    }

    // ─── 멤버 관리 ───────────────────────────────────────────────────────────

    public void addToTeam(String teamId, UUID uuid) {
        Team team = teams.get(teamId);
        if (team == null) return;
        team.addMember(uuid);
        wanderers.remove(uuid);
        prevTeamMap.remove(uuid); // 새 팀 합류 시 이전 팀 기록 초기화
    }

    /** 플레이어를 방랑자로 전환 (팀에서 제거) */
    public void addToWanderer(UUID uuid) {
        for (Team team : teams.values()) {
            team.getMembers().remove(uuid);
        }
        wanderers.add(uuid);
    }

    public void removeFromTeam(String teamId, UUID uuid) {
        Team team = teams.get(teamId);
        if (team == null) return;
        team.getMembers().remove(uuid);
        wanderers.add(uuid);
    }

    // ─── 깃발 점령 후처리 ────────────────────────────────────────────────────

    /**
     * 깃발 점령 시 호출.
     * 이전 소유팀에서 깃발을 제거하고, 깃발이 0개가 되면 해당 팀을 해체.
     */
    public void onFlagCaptured(int flagId, String capturingTeamId) {
        var gameTimer = plugin.getGameTimer();

        // 이전 소유팀 처리
        for (Team team : new ArrayList<>(teams.values())) {
            if (!team.getId().equals(capturingTeamId) && team.ownsFlag(flagId)) {
                team.removeFlag(flagId);
                if (gameTimer != null && gameTimer.isCapturePhase()) {
                    team.markFlagChanged(flagId); // 점령 단계 중 뺏긴 깃발
                }
                if (team.getFlagCount() == 0) {
                    plugin.getServer().broadcastMessage(
                        ChatColor.RED + "팀 [" + team.getId() + "] 이(가) 모든 깃발을 잃어 해체되었습니다!"
                    );
                    disbandTeam(team.getId());
                }
                break;
            }
        }
        // 새 소유팀에 깃발 추가
        Team capturingTeam = teams.get(capturingTeamId);
        if (capturingTeam != null) {
            capturingTeam.addFlag(flagId);
            if (gameTimer != null && gameTimer.isCapturePhase()) {
                capturingTeam.markFlagChanged(flagId); // 점령 단계 중 새로 점령한 깃발
            }
        }
    }

    // ─── 영속성 ──────────────────────────────────────────────────────────────

    public void save() {
        teamsConfig = new YamlConfiguration();

        for (Team team : teams.values()) {
            String path = "teams." + team.getId();
            teamsConfig.set(path + ".leader", team.getLeader().toString());
            List<String> memberList = team.getMembers().stream().map(UUID::toString).toList();
            teamsConfig.set(path + ".members", memberList);
            teamsConfig.set(path + ".state", team.getState().name());
            teamsConfig.set(path + ".flags", new ArrayList<>(team.getOwnedFlagIds()));
            teamsConfig.set(path + ".point", team.getPoint());
            teamsConfig.set(path + ".flagpoint", team.getFlagpoint());
        }

        List<String> wandererList = wanderers.stream().map(UUID::toString).toList();
        teamsConfig.set("wanderers", wandererList);

        // 이전 팀 기록 저장
        prevTeamMap.forEach((uuid, tid) ->
            teamsConfig.set("prevTeams." + uuid.toString(), tid));

        try {
            teamsConfig.save(teamsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("teams.yml 저장 실패: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void load() {
        if (!teamsFile.exists()) return;
        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);

        if (teamsConfig.isConfigurationSection("teams")) {
            for (String teamId : teamsConfig.getConfigurationSection("teams").getKeys(false)) {
                String path = "teams." + teamId;
                UUID leader = UUID.fromString(Objects.requireNonNull(teamsConfig.getString(path + ".leader")));
                Team team = new Team(teamId, leader);

                for (String s : teamsConfig.getStringList(path + ".members")) {
                    team.addMember(UUID.fromString(s));
                }

                String stateStr = teamsConfig.getString(path + ".state", "ACTIVE");
                team.setState(TeamState.valueOf(stateStr));

                List<?> rawFlags = teamsConfig.getList(path + ".flags", List.of());
                for (Object o : rawFlags) {
                    if (o instanceof Integer i) team.addFlag(i);
                }

                team.addPoint(teamsConfig.getInt(path + ".point", 0));
                team.addFlagpoint(teamsConfig.getInt(path + ".flagpoint", 0));

                teams.put(teamId, team);
            }
        }

        for (String s : teamsConfig.getStringList("wanderers")) {
            wanderers.add(UUID.fromString(s));
        }

        // 이전 팀 기록 로드
        if (teamsConfig.isConfigurationSection("prevTeams")) {
            for (String uuidStr : teamsConfig.getConfigurationSection("prevTeams").getKeys(false)) {
                String tid = teamsConfig.getString("prevTeams." + uuidStr);
                if (tid != null) prevTeamMap.put(UUID.fromString(uuidStr), tid);
            }
        }
    }
}
