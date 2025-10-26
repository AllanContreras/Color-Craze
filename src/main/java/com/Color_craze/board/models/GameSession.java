package com.Color_craze.board.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.Color_craze.auth.dtos.UserDetailsResponse;
import com.Color_craze.utils.enums.ColorStatus;

@Document(collection = "gamesessions")
public class GameSession {

    @Id
    private String id;
    private String code;
    private String status; // WAITING, PLAYING, FINISHED
    private Instant createdAt;
    private Instant joinDeadline; // createdAt + 20s ventana de unión
    private Instant startedAt;
    private Instant finishedAt;
    private List<PlayerEntry> players = new ArrayList<>();
    private List<PlatformState> platforms = new ArrayList<>();

    public static class PlayerEntry {
        public String playerId;
        public String nickname;
        public ColorStatus color;
        public String avatar; // optional small character identifier
        public int score;
        public PlayerEntry() {}
        public PlayerEntry(String playerId, String nickname, ColorStatus color) {
            this.playerId = playerId;
            this.nickname = nickname;
            this.color = color;
            this.avatar = null;
            this.score = 0;
        }
        public PlayerEntry(String playerId, String nickname, ColorStatus color, String avatar) {
            this.playerId = playerId;
            this.nickname = nickname;
            this.color = color;
            this.avatar = avatar;
            this.score = 0;
        }
    }

    public static class PlatformState {
        public int row;
        public int col;
        public com.Color_craze.utils.enums.ColorStatus color;
        public PlatformState() {}
        public PlatformState(int row, int col, com.Color_craze.utils.enums.ColorStatus color) {
            this.row = row; this.col = col; this.color = color;
        }
    }

    public GameSession() {}

    // getters/setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getJoinDeadline() { return joinDeadline; }
    public void setJoinDeadline(Instant joinDeadline) { this.joinDeadline = joinDeadline; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public List<PlayerEntry> getPlayers() { return players; }
    public void setPlayers(List<PlayerEntry> players) { this.players = players; }
    public List<PlatformState> getPlatforms() { return platforms; }
    public void setPlatforms(List<PlatformState> platforms) { this.platforms = platforms; }
}
