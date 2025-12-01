package com.Color_craze.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.Color_craze.board.services.GameStateSnapshotService;
import com.Color_craze.board.repositories.GameRepository;

@RestController
@RequestMapping("/admin")
public class SnapshotAdminController {
    private final GameStateSnapshotService snapshots;
    private final GameRepository repo;

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
}