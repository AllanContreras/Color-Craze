package com.Color_craze.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.stream.Collectors;

import com.Color_craze.board.services.GameStateSnapshotService;
import com.Color_craze.board.repositories.GameRepository;

@RestController
@RequestMapping("/admin")
public class SnapshotAdminController {
    private final GameStateSnapshotService snapshots;
    private final GameRepository repo;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public SnapshotAdminController(GameStateSnapshotService snapshots, GameRepository repo) {
        this.snapshots = snapshots;
        this.repo = repo;
    }

    @GetMapping("/snapshot/{code}")
    public ResponseEntity<String> getSnapshot(@PathVariable String code) {
        String json = snapshots.load(code);
        if (json == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(json);
    }

    @PostMapping("/restore/{code}")
    public ResponseEntity<Void> restoreSnapshot(@PathVariable String code) {
        try {
            String json = snapshots.load(code);
            if (json == null) return ResponseEntity.notFound().build();
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var gs = mapper.readValue(json, com.Color_craze.board.models.GameSession.class);
            repo.save(gs);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/snapshot/board/{code}")
    public ResponseEntity<String> getBoardSnapshot(@PathVariable String code) {
        String json = snapshots.loadBoard(code);
        if (json == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(json);
    }

    @DeleteMapping("/snapshot/board/{code}")
    public ResponseEntity<Void> deleteBoardSnapshot(@PathVariable String code) {
        snapshots.deleteBoard(code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/score/repair/{code}")
    public ResponseEntity<Map<String,Object>> repairScores(@PathVariable String code) {
        var opt = repo.findByCode(code);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        var gs = opt.get();
        if (gs.getPlatforms() == null || gs.getPlatforms().isEmpty()) {
            return ResponseEntity.status(409).body(Map.of("message", "No hay plataformas registradas para recalcular"));
        }
        // Recalcular: contar plataformas por color
        var counts = gs.getPlatforms().stream()
            .collect(Collectors.groupingBy(p -> p.color, Collectors.counting()));
        gs.getPlayers().forEach(p -> p.score = counts.getOrDefault(p.color, 0L).intValue());
        repo.save(gs);
        try { if (meterRegistry != null) meterRegistry.counter("game.admin.score.repair.invocations").increment(); } catch (Exception ignored) {}
        var scoreboard = gs.getPlayers().stream()
            .sorted((a,b) -> Integer.compare(b.score, a.score))
            .map(p -> Map.of("playerId", p.playerId, "nickname", p.nickname, "color", p.color.name(), "score", p.score))
            .collect(Collectors.toList());
        java.util.Map<String,Object> out = new java.util.HashMap<>();
        out.put("code", gs.getCode());
        out.put("status", gs.getStatus()); // puede ser null
        out.put("scoreboard", scoreboard);
        return ResponseEntity.ok(out);
    }
}