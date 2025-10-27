package com.Color_craze.board.services;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.Color_craze.board.models.GameSession;
import com.Color_craze.board.dtos.Responses.MoveResult;
import com.Color_craze.utils.enums.PlayerMove;
import com.Color_craze.board.models.GameSession.PlayerEntry;
import com.Color_craze.board.repositories.GameRepository;
import com.Color_craze.board.dtos.CreateGameResponse;
import com.Color_craze.board.dtos.GameInfoResponse;
import com.Color_craze.board.dtos.JoinGameRequest;
import com.Color_craze.board.dtos.UpdatePlayerRequest;
import com.Color_craze.utils.enums.ColorStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import com.Color_craze.board.arena.services.ArenaService;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final BoardService boardService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new SecureRandom();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ArenaService arenaService;

    // Configurable timings (in seconds)
    private static final long JOIN_WINDOW_SECONDS = 60; // tiempo para unirse
    private static final long GAME_DURATION_SECONDS = 60; // duración de la partida

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public CreateGameResponse createGame() {
        String code = generateUniqueCode();
        GameSession gs = new GameSession();
        gs.setCode(code);
        gs.setStatus("WAITING");
        Instant now = Instant.now();
        gs.setCreatedAt(now);
        gs.setJoinDeadline(now.plusSeconds(JOIN_WINDOW_SECONDS));
        gameRepository.save(gs);
        // Publicar estado inicial de WAITING (para countdown en clientes)
        Map<String, Object> waiting = Map.of(
            "code", code,
            "status", "WAITING",
            "joinDeadlineMs", gs.getJoinDeadline() != null ? gs.getJoinDeadline().toEpochMilli() : null,
            "players", List.of()
        );
        messagingTemplate.convertAndSend(String.format("/topic/board/%s/state", code), waiting);
        // Programar inicio automático a los 40s
        scheduler.schedule(() -> {
            try {
                // Double-check from DB for current state
                gameRepository.findByCode(code).ifPresent(current -> {
                    if ("WAITING".equals(current.getStatus())) {
                        startGame(current);
                    }
                });
            } catch (Exception ignored) {}
        }, JOIN_WINDOW_SECONDS, TimeUnit.SECONDS);
        return new CreateGameResponse(code);
    }

    public Optional<GameInfoResponse> getGame(String code) {
        return gameRepository.findByCode(code).map(gs -> {
            // Fallback: si la ventana de unión ya expiró y aún está en WAITING, iniciar la partida
            try {
                if ("WAITING".equals(gs.getStatus()) && gs.getJoinDeadline() != null && Instant.now().isAfter(gs.getJoinDeadline())) {
                    startGame(gs);
                    // recargar para DTO actualizado
                    gs = gameRepository.findByCode(code).orElse(gs);
                }
            } catch (Exception ignored) {}
            return toDto(gs);
        });
    }

    public GameInfoResponse toDto(GameSession gs) {
        List<GameInfoResponse.PlayerInfo> players = gs.getPlayers().stream()
            .map(p -> new GameInfoResponse.PlayerInfo(p.playerId, p.nickname, p.color.name(), p.avatar, p.score))
            .collect(Collectors.toList());
        Long joinDeadlineMs = gs.getJoinDeadline() != null ? gs.getJoinDeadline().toEpochMilli() : null;
        Long startedAtMs = gs.getStartedAt() != null ? gs.getStartedAt().toEpochMilli() : null;
    Long durationMs = "PLAYING".equals(gs.getStatus()) ? GAME_DURATION_SECONDS * 1000L : null;
        List<GameInfoResponse.PlayerPos> positions = null;
        GameInfoResponse.ArenaConfig arenaCfg = null;
        if ("PLAYING".equals(gs.getStatus())) {
            var board = boardService.getOrCreateBoard(gs.getCode());
            positions = board.getPlayers().entrySet().stream()
                .map(e -> new GameInfoResponse.PlayerPos(
                    e.getKey().toString(),
                    e.getValue().getRow(),
                    e.getValue().getCol(),
                    e.getValue().getColor().name()
                ))
                .collect(Collectors.toList());
            // Try include arena config if available
            try {
                var st = arenaService.getState(gs.getCode());
                if (st != null) {
                    var plats = st.platforms.stream()
                        .map(pl -> new GameInfoResponse.ArenaPlatform(pl.x(), pl.y(), pl.width(), pl.height(), pl.cells()))
                        .collect(Collectors.toList());
                    arenaCfg = new GameInfoResponse.ArenaConfig(st.width, st.height, plats);
                }
            } catch (Exception ignored) {}
        }
        return new GameInfoResponse(gs.getCode(), gs.getStatus(), joinDeadlineMs, players, startedAtMs, durationMs, positions, arenaCfg);
    }

    public Map<String, Object> handlePlayerMove(String code, String playerId, PlayerMove direction) {
        GameSession gs = gameRepository.findByCode(code).orElseThrow(() -> new IllegalArgumentException("Game not found"));
        if (!"PLAYING".equals(gs.getStatus())) {
            // Ignore moves unless the game is in PLAYING
            return null;
        }
        // validate player exists in session
        GameSession.PlayerEntry pe = gs.getPlayers().stream().filter(p -> p.playerId.equals(playerId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Player not in game"));

        // ensure board has player with assigned color
        boardService.ensurePlayerOnBoard(code, playerId, pe.color);

        // delegate to board
        MoveResult result = boardService.movePlayer(code, playerId, direction);

        // update session scores from affected players (map by color to original playerId)
        result.affectedPlayers().forEach(up -> {
            gs.getPlayers().stream()
                .filter(p -> p.color == up.color())
                .findFirst()
                .ifPresent(p -> p.score = up.newScore());
        });
    // persist platform state snapshot
    gs.setPlatforms(boardService.exportPlatformStates(code));
    gameRepository.save(gs);

        // Build a client-friendly payload with original playerIds as strings
        java.util.List<Map<String, Object>> platforms = new java.util.ArrayList<>();
        for (var pu : result.platforms()){
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("row", pu.row());
            m.put("col", pu.col());
            m.put("color", pu.color().name());
            platforms.add(m);
        }

        java.util.List<Map<String, Object>> affected = new java.util.ArrayList<>();
        for (var up : result.affectedPlayers()){
            String origId = gs.getPlayers().stream()
                .filter(pp -> pp.color == up.color())
                .map(pp -> pp.playerId)
                .findFirst()
                .orElse(up.playerId().toString());
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("playerId", origId);
            m.put("color", up.color().name());
            m.put("newScore", up.newScore());
            affected.add(m);
        }

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("playerId", playerId);
        payload.put("newRow", result.newRow());
        payload.put("newCol", result.newCol());
        payload.put("platforms", platforms);
        payload.put("affectedPlayers", affected);
        payload.put("success", result.success());
        return payload;
    }

    public void joinGame(String code, JoinGameRequest req) {
        GameSession gs = gameRepository.findByCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        if (gs.getPlayers().size() >= 4) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room full");

        // Color is always auto-assigned randomly from available player colors
        ColorStatus color = pickColor(gs);

    PlayerEntry pe = new PlayerEntry(req.playerId(), req.nickname(), color, req.avatar());
        try { System.out.println("[Join] Assigned color=" + color + " to player=" + req.playerId()); } catch (Exception ignored) {}
        gs.getPlayers().add(pe);
        gameRepository.save(gs);
        // Si ya expiró la ventana de unión, iniciar inmediatamente
        if (gs.getJoinDeadline() != null && Instant.now().isAfter(gs.getJoinDeadline()) && "WAITING".equals(gs.getStatus())) {
            startGame(gs);
            return;
        }
        // Notificar estado de WAITING actualizado (colores tomados, countdown)
        Map<String, Object> waiting = Map.of(
            "code", gs.getCode(),
            "status", "WAITING",
            "joinDeadlineMs", gs.getJoinDeadline() != null ? gs.getJoinDeadline().toEpochMilli() : null,
            "players", gs.getPlayers().stream().map(p -> Map.of(
                "playerId", p.playerId,
                "nickname", p.nickname,
                "color", p.color.name(),
                "avatar", p.avatar,
                "score", p.score
            )).collect(Collectors.toList())
        );
        messagingTemplate.convertAndSend(String.format("/topic/board/%s/state", gs.getCode()), waiting);
        // No auto-start on player count; countdown/explicit trigger governs start
    }

    private ColorStatus pickColor(GameSession gs) {
        // Allowed player colors (skip WHITE)
        ColorStatus[] choices = new ColorStatus[]{ColorStatus.YELLOW, ColorStatus.PINK, ColorStatus.PURPLE, ColorStatus.GREEN};
        java.util.List<ColorStatus> available = new java.util.ArrayList<>();
        for (ColorStatus c : choices) {
            boolean used = gs.getPlayers().stream().anyMatch(p -> p.color == c);
            if (!used) available.add(c);
        }
        if (!available.isEmpty()) {
            return available.get(random.nextInt(available.size()));
        }
        // fallback: all taken (shouldn't happen with max 4 players)
        return choices[random.nextInt(choices.length)];
    }

    // kept for potential future validations
    // private boolean isAllowedPlayerColor(ColorStatus c) {
    //     return c == ColorStatus.YELLOW || c == ColorStatus.PINK || c == ColorStatus.PURPLE || c == ColorStatus.GREEN;
    // }

    private void startGame(GameSession gs) {
        if ("PLAYING".equals(gs.getStatus())) return;
        // ensure board exists and players are present BEFORE flipping status to avoid race with GET
        String code = gs.getCode();
        boardService.getOrCreateBoard(code);
        if (gs.getPlatforms() != null && !gs.getPlatforms().isEmpty()) {
            boardService.applyPlatformStates(code, gs.getPlatforms());
        }
        for (PlayerEntry p : gs.getPlayers()) {
            boardService.ensurePlayerOnBoard(code, p.playerId, p.color);
        }

        // Now flip status to PLAYING and set start time, then persist
        gs.setStatus("PLAYING");
        gs.setStartedAt(Instant.now());
        gameRepository.save(gs);

        // publish initial state with positions
        var board = boardService.getOrCreateBoard(code);
        var positions = board.getPlayers().entrySet().stream()
            .map(e -> Map.of(
                "playerId", e.getKey().toString(),
                "row", e.getValue().getRow(),
                "col", e.getValue().getCol(),
                "color", e.getValue().getColor().name()
            ))
            .collect(Collectors.toList());

        Map<String, Object> state = Map.of(
            "code", code,
            "status", "PLAYING",
            "startTimestamp", gs.getStartedAt() != null ? gs.getStartedAt().toEpochMilli() : Instant.now().toEpochMilli(),
            "duration", (int)(GAME_DURATION_SECONDS * 1000),
            "playerPositions", positions,
            "players", gs.getPlayers().stream().map(p -> Map.of(
                "playerId", p.playerId,
                "nickname", p.nickname,
                "color", p.color.name(),
                "avatar", p.avatar,
                "score", p.score
            )).collect(Collectors.toList())
        );
        // Initialize 2D arena and append its config to the state
        try {
            var st = arenaService.initGame(code, gs.getPlayers());
            Map<String, Object> arena = Map.of(
                "width", st.width,
                "height", st.height,
                "platforms", st.platforms.stream().map(pl -> Map.of(
                    "x", pl.x(), "y", pl.y(), "width", pl.width(), "height", pl.height(), "cells", pl.cells()
                )).collect(Collectors.toList())
            );
            java.util.HashMap<String, Object> mutable = new java.util.HashMap<>(state);
            mutable.put("arena", arena);
            messagingTemplate.convertAndSend(String.format("/topic/board/%s/state", code), mutable);
        } catch (Exception ex) {
            messagingTemplate.convertAndSend(String.format("/topic/board/%s/state", code), state);
        }

        // schedule end in 40 seconds
        scheduler.schedule(() -> endGame(gs.getCode()), GAME_DURATION_SECONDS, TimeUnit.SECONDS);
    }

    public void updatePlayer(String code, UpdatePlayerRequest req) {
        GameSession gs = gameRepository.findByCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
        if (!"WAITING".equals(gs.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede cambiar color/personaje durante la partida");
        }
        if (req.playerId() == null || req.playerId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "playerId requerido");
        }
        GameSession.PlayerEntry pe = gs.getPlayers().stream().filter(p -> p.playerId.equals(req.playerId())).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jugador no está en la sala"));

        // Disallow manual color changes; colors are assigned automatically
        if (req.color() != null && !req.color().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El color se asigna automáticamente y no se puede cambiar");
        }

        // Avatar update (optional)
        if (req.avatar() != null) {
            pe.avatar = req.avatar();
        }

        gameRepository.save(gs);

        // Notify WAITING state with updated players
        Map<String, Object> waiting = Map.of(
            "code", gs.getCode(),
            "status", "WAITING",
            "joinDeadlineMs", gs.getJoinDeadline() != null ? gs.getJoinDeadline().toEpochMilli() : null,
            "players", gs.getPlayers().stream().map(p -> Map.of(
                "playerId", p.playerId,
                "nickname", p.nickname,
                "color", p.color.name(),
                "avatar", p.avatar,
                "score", p.score
            )).collect(Collectors.toList())
        );
        messagingTemplate.convertAndSend(String.format("/topic/board/%s/state", gs.getCode()), waiting);
    }

    private void endGame(String code) {
        Optional<GameSession> opt = gameRepository.findByCode(code);
        if (opt.isEmpty()) return;
        GameSession gs = opt.get();
        // Sync arena scores back to game session before finishing
        try {
            var st = arenaService.getState(code);
            if (st != null && st.players != null) {
                for (var p : gs.getPlayers()) {
                    var pl2d = st.players.get(p.playerId);
                    if (pl2d != null) p.score = pl2d.score;
                }
            }
        } catch (Exception ignored) {}
        gs.setStatus("FINISHED");
        gs.setFinishedAt(Instant.now());
        // calculate results (already in scores) - no-op here
        gameRepository.save(gs);
        // publish final standings
        var standings = gs.getPlayers().stream()
            .sorted(Comparator.comparingInt(p -> -p.score))
            .map(p -> Map.of("playerId", p.playerId, "nickname", p.nickname, "avatar", p.avatar, "score", p.score))
            .collect(Collectors.toList());
        messagingTemplate.convertAndSend(String.format("/topic/board/%s/end", code), Map.of("code", code, "standings", standings));
        try { arenaService.stopGame(code); } catch (Exception ignored) {}
    }

    public void restartGame(String code) {
        GameSession gs = gameRepository.findByCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));
    // Reset state to WAITING with a new join window
        gs.setStatus("WAITING");
        gs.setStartedAt(null);
        gs.setFinishedAt(null);
        gs.setJoinDeadline(Instant.now().plusSeconds(JOIN_WINDOW_SECONDS));
        // Reset scores
        for (var p : gs.getPlayers()) p.score = 0;
        // Clear saved platforms snapshot
        gs.getPlatforms().clear();
        gameRepository.save(gs);

        // Reset the in-memory board
        boardService.resetBoard(code);

        // Broadcast WAITING state with new countdown
        Map<String, Object> waiting = Map.of(
            "code", gs.getCode(),
            "status", "WAITING",
            "joinDeadlineMs", gs.getJoinDeadline() != null ? gs.getJoinDeadline().toEpochMilli() : null,
            "players", gs.getPlayers().stream().map(p -> Map.of(
                "playerId", p.playerId,
                "nickname", p.nickname,
                "color", p.color.name(),
                "avatar", p.avatar,
                "score", p.score
            )).collect(Collectors.toList())
        );
        messagingTemplate.convertAndSend(String.format("/topic/board/%s/state", gs.getCode()), waiting);

        // Schedule auto-start when join window expires
        scheduler.schedule(() -> {
            try {
                gameRepository.findByCode(code).ifPresent(current -> {
                    if ("WAITING".equals(current.getStatus())) {
                        startGame(current);
                    }
                });
            } catch (Exception ignored) {}
        }, JOIN_WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = randomCode(6);
            if (!gameRepository.existsByCode(code)) return code;
        }
        throw new IllegalStateException("Unable to generate unique code");
    }

    private String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(ALPHANUM.charAt(random.nextInt(ALPHANUM.length())));
        return sb.toString();
    }
}
