package com.Color_craze.board.services;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.Color_craze.board.models.GameSession;
import com.Color_craze.board.services.BoardService;
import com.Color_craze.board.dtos.Responses.MoveResult;
import com.Color_craze.utils.enums.PlayerMove;
import com.Color_craze.board.models.GameSession.PlayerEntry;
import com.Color_craze.board.repositories.GameRepository;
import com.Color_craze.board.dtos.CreateGameResponse;
import com.Color_craze.board.dtos.GameInfoResponse;
import com.Color_craze.board.dtos.JoinGameRequest;
import com.Color_craze.utils.enums.ColorStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final BoardService boardService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new SecureRandom();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public CreateGameResponse createGame() {
        String code = generateUniqueCode();
        GameSession gs = new GameSession();
        gs.setCode(code);
        gs.setStatus("WAITING");
        gs.setCreatedAt(Instant.now());
        gameRepository.save(gs);
        return new CreateGameResponse(code);
    }

    public Optional<GameInfoResponse> getGame(String code) {
        return gameRepository.findByCode(code).map(gs -> toDto(gs));
    }

    public GameInfoResponse toDto(GameSession gs) {
        List<GameInfoResponse.PlayerInfo> players = gs.getPlayers().stream()
            .map(p -> new GameInfoResponse.PlayerInfo(p.playerId, p.nickname, p.color.name(), p.score))
            .collect(Collectors.toList());
        return new GameInfoResponse(gs.getCode(), gs.getStatus(), players);
    }

    public MoveResult handlePlayerMove(String code, String playerId, com.Color_craze.utils.enums.PlayerMove direction) {
        GameSession gs = gameRepository.findByCode(code).orElseThrow(() -> new IllegalArgumentException("Game not found"));
        // validate player exists in session
        GameSession.PlayerEntry pe = gs.getPlayers().stream().filter(p -> p.playerId.equals(playerId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Player not in game"));

        // ensure board has player with assigned color
        boardService.ensurePlayerOnBoard(code, playerId, pe.color);

        // delegate to board
    MoveResult result = boardService.movePlayer(code, playerId, direction);

        // update session scores from affected players
        result.affectedPlayers().forEach(up -> {
            String uid = up.playerId().toString();
            gs.getPlayers().stream().filter(p -> p.playerId.equals(uid)).findFirst().ifPresent(p -> p.score = up.newScore());
        });
    // persist platform state snapshot
    gs.setPlatforms(boardService.exportPlatformStates(code));
    gameRepository.save(gs);

        return result;
    }

    public void joinGame(String code, JoinGameRequest req) {
        GameSession gs = gameRepository.findByCode(code).orElseThrow(() -> new IllegalArgumentException("Game not found"));
        if (gs.getPlayers().size() >= 4) throw new IllegalStateException("Room full");
        // assign color
        ColorStatus color = pickColor(gs);
        PlayerEntry pe = new PlayerEntry(req.playerId(), req.nickname(), color);
        gs.getPlayers().add(pe);
        gameRepository.save(gs);
        // auto-start if 2-4 players
        if (gs.getPlayers().size() >= 2) {
            startGame(gs);
        }
    }

    private ColorStatus pickColor(GameSession gs) {
        for (ColorStatus c : ColorStatus.values()) {
            boolean used = gs.getPlayers().stream().anyMatch(p -> p.color == c);
            if (!used) return c;
        }
        // fallback
        return ColorStatus.PINK;
    }

    private void startGame(GameSession gs) {
        if ("PLAYING".equals(gs.getStatus())) return;
        gs.setStatus("PLAYING");
        gs.setStartedAt(Instant.now());
        gameRepository.save(gs);

        // ensure board exists and players are present
        String code = gs.getCode();
        boardService.getOrCreateBoard(code);
        // apply saved platform states
        if (gs.getPlatforms() != null && !gs.getPlatforms().isEmpty()) {
            boardService.applyPlatformStates(code, gs.getPlatforms());
        }
        for (PlayerEntry p : gs.getPlayers()) {
            boardService.ensurePlayerOnBoard(code, p.playerId, p.color);
        }

        // publish initial state to room
        Map<String, Object> state = Map.of(
            "code", code,
            "status", "PLAYING",
            "players", gs.getPlayers().stream().map(p -> Map.of(
                "playerId", p.playerId,
                "nickname", p.nickname,
                "color", p.color.name(),
                "score", p.score
            )).collect(Collectors.toList())
        );
        messagingTemplate.convertAndSend(String.format("/topic/board/%s/state", code), state);

        // schedule end in 45 seconds
        scheduler.schedule(() -> endGame(gs.getCode()), 45, TimeUnit.SECONDS);
    }

    private void endGame(String code) {
        Optional<GameSession> opt = gameRepository.findByCode(code);
        if (opt.isEmpty()) return;
        GameSession gs = opt.get();
        gs.setStatus("FINISHED");
        gs.setFinishedAt(Instant.now());
        // calculate results (already in scores) - no-op here
        gameRepository.save(gs);
        // publish final standings
        var standings = gs.getPlayers().stream()
            .sorted(Comparator.comparingInt(p -> -p.score))
            .map(p -> Map.of("playerId", p.playerId, "nickname", p.nickname, "score", p.score))
            .collect(Collectors.toList());
        messagingTemplate.convertAndSend(String.format("/topic/board/%s/end", code), Map.of("code", code, "standings", standings));
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
