package com.Color_craze.board.arena.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.Color_craze.board.arena.dtos.ArenaFrame;
import com.Color_craze.board.arena.dtos.ArenaInput;
import com.Color_craze.board.arena.models.ArenaState;
import com.Color_craze.board.arena.models.Platform2D;
import com.Color_craze.board.arena.models.Player2D;
import com.Color_craze.board.models.GameSession.PlayerEntry;
// import com.Color_craze.utils.enums.ColorStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArenaService {
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, ArenaState> arenas = new ConcurrentHashMap<>();
    private final Map<String, InputState> inputs = new ConcurrentHashMap<>(); // key: code|playerId
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    // Track scheduled tasks per game to allow proper cancellation and avoid leaks
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> tickTasks = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> broadcastPosTasks = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> broadcastPaintTasks = new ConcurrentHashMap<>();
    // Simple bot AI state: direction and next decision timestamp per bot (key: code|playerId)
    private final Map<String, Integer> botDir = new ConcurrentHashMap<>(); // -1 left, +1 right
    private final Map<String, Long> botNextDecisionMs = new ConcurrentHashMap<>();

    private static class InputState { volatile boolean left, right, jump; }

    public ArenaState initGame(String code, List<PlayerEntry> players){
        ArenaState st = new ArenaState(980, 540); // a bit wider to match the vibe of the reference
    // Ground (normalize to ~5 px per cell like other platforms)
    // st.width ~ 980 => 196 cells keeps ~5 px per cell for consistent scoring across platforms
    addPlatform(st, 0, 510, st.width, 30, 196);

        // Lower side ledges (approximate angled ramps with rectangles)
        addPlatform(st, 140, 440, 180, 22, 36); // left lower
        addPlatform(st, st.width - 140 - 180, 440, 180, 22, 36); // right lower

    // Mid center platform (restored)
    addPlatform(st, (st.width-320)/2, 360, 320, 24, 64); // main mid

        // Upper side ledges
        addPlatform(st, 120, 250, 200, 22, 40); // left upper
        addPlatform(st, st.width - 120 - 200, 250, 200, 22, 40); // right upper

        // Small top center platform (requested)
        addPlatform(st, (st.width-160)/2, 160, 160, 20, 32); // top center small

    // Removed narrow mid connectors to open space

        // Spawn players on upper side ledges; distribute if more players
        double[][] spawnSpots = new double[][]{
            {140+20, 250-32},                          // left upper ledge
            {st.width - 120 - 200 + 200 - 44, 250-32}, // right upper ledge
            {(st.width-320)/2 + 10, 360-32},           // mid
            {(st.width-320)/2 + 320 - 34, 360-32}      // mid right edge
        };
        for (int i=0;i<players.size();i++){
            var p = players.get(i);
            double sx = spawnSpots[Math.min(i, spawnSpots.length-1)][0];
            double sy = spawnSpots[Math.min(i, spawnSpots.length-1)][1];
            Player2D pl = new Player2D(p.playerId, sx, sy, p.color);
            st.players.put(p.playerId, pl);
        }
    arenas.put(code, st);
    try { System.out.println("[Arena] platforms=" + st.platforms.size()); } catch (Exception ignore) {}
        // Cancel any previous tasks for this code (defensive)
        var prevTick = tickTasks.remove(code);
        if (prevTick != null) { try { prevTick.cancel(true); } catch (Exception ignore) {} }
        var prevPos = broadcastPosTasks.remove(code);
        if (prevPos != null) { try { prevPos.cancel(true); } catch (Exception ignore) {} }
        var prevPaint = broadcastPaintTasks.remove(code);
        if (prevPaint != null) { try { prevPaint.cancel(true); } catch (Exception ignore) {} }

        // Start physics loop (~120 Hz/8ms) for smooth feel.
        var tickF = scheduler.scheduleAtFixedRate(() -> tick(code), 0, 8, TimeUnit.MILLISECONDS);
        // Broadcast positions/scores at ~30 Hz and paint at ~6-7 Hz to reduce payload size.
    var posF = scheduler.scheduleAtFixedRate(() -> broadcast(code, false), 0, 22, TimeUnit.MILLISECONDS);
        var paintF = scheduler.scheduleAtFixedRate(() -> broadcast(code, true), 0, 150, TimeUnit.MILLISECONDS);
        tickTasks.put(code, tickF);
        broadcastPosTasks.put(code, posF);
        broadcastPaintTasks.put(code, paintF);
        return st;
    }

    private void addPlatform(ArenaState st, double x, double y, double w, double h, int cells){
        int idx = st.platforms.size();
        st.platforms.add(new Platform2D(x,y,w,h,cells));
        st.ensurePaintArray(idx, cells);
    }

    public void stopGame(String code){
        arenas.remove(code);
        // Cancel and remove scheduled tasks for this game
        var t = tickTasks.remove(code);
        if (t != null) { try { t.cancel(true); } catch (Exception ignore) {} }
        var bp = broadcastPosTasks.remove(code);
        if (bp != null) { try { bp.cancel(true); } catch (Exception ignore) {} }
        var bq = broadcastPaintTasks.remove(code);
        if (bq != null) { try { bq.cancel(true); } catch (Exception ignore) {} }
        // Clean up inputs for this game to free memory
        String prefix = code + "|";
        inputs.keySet().removeIf(k -> k.startsWith(prefix));
    }

    // includePaint=false => send only players+scores; true => include paint array as well
    private void broadcast(String code, boolean includePaint){
        ArenaState st = arenas.get(code);
        if (st == null) return;
        var players = st.players.values().stream().map(p -> new com.Color_craze.board.arena.dtos.ArenaFrame.PlayerPose(p.playerId, p.x, p.y, p.onGround)).collect(java.util.stream.Collectors.toList());
        java.util.Map<String, Integer> scores = new java.util.HashMap<>();
        for (var e : st.players.entrySet()) scores.put(e.getKey(), e.getValue().score);
        var frame = new com.Color_craze.board.arena.dtos.ArenaFrame(code, players, includePaint ? st.paint : null, scores);
        messagingTemplate.convertAndSend(String.format("/topic/board/%s/arena", code), frame);
    }

    public ArenaState getState(String code){
        return arenas.get(code);
    }

    public void updateInput(ArenaInput input){
        if (input == null || input.code() == null || input.playerId() == null) return;
        String key = input.code()+"|"+input.playerId();
        InputState st = inputs.computeIfAbsent(key, k -> new InputState());
        st.left = input.left();
        st.right = input.right();
        if (input.jump()) st.jump = true; // edge-trigger consumption in tick
    }

    private void tick(String code){
        ArenaState st = arenas.get(code);
        if (st == null) return;
        // Update bot inputs before integrating physics
        try { updateBots(code, st); } catch (Exception ignore) {}
    final double dt = 0.008; // ~120 Hz
    final double gravity = 2400.0;
    final double maxSpeed = 300.0;
    final double accel = 3200.0;
    final double jumpVy = -820.0;
    List<Player2D> plist = new ArrayList<>(st.players.values());
    final long nowMs = System.currentTimeMillis();
    final long awardIntervalMs = 150; // ~6-7 points per second maximum
        // Track which cells have already caused a decrement this tick to avoid mutual double-decrement
        java.util.Set<Long> decrementedCellsThisTick = new java.util.HashSet<>();
        for (Player2D p : plist){
            InputState in = inputs.get(code+"|"+p.playerId);
            double ax = 0;
            if (in != null){
                if (in.left && !in.right) ax = -accel;
                else if (in.right && !in.left) ax = accel;
                if (in.jump && p.onGround){
                    p.vy = jumpVy;
                }
                in.jump = false; // consume
            }
            // integrate
            p.vx += ax * dt;
            // clamp vx with friction if no input
            if (ax == 0){
                p.vx *= 0.90;
                if (Math.abs(p.vx) < 10) p.vx = 0;
            }
            if (p.vx > maxSpeed) p.vx = maxSpeed;
            if (p.vx < -maxSpeed) p.vx = -maxSpeed;

            p.vy += gravity * dt;

            // horizontal move and collide
            double newX = p.x + p.vx * dt;
            double newY = p.y;
            // collide sides
            for (int i=0;i<st.platforms.size();i++){
                Platform2D pl = st.platforms.get(i);
                if (pl.intersects(newX, newY, Player2D.WIDTH, Player2D.HEIGHT)){
                    if (p.vx > 0) newX = pl.x() - Player2D.WIDTH; else if (p.vx < 0) newX = pl.x() + pl.width();
                    p.vx = 0;
                }
            }
            p.x = clamp(newX, 0, st.width - Player2D.WIDTH);

            // vertical move and collide
            newY = p.y + p.vy * dt;
            Platform2D groundPl = null;
            for (int i=0;i<st.platforms.size();i++){
                Platform2D pl = st.platforms.get(i);
                if (pl.intersects(p.x, newY, Player2D.WIDTH, Player2D.HEIGHT)){
                    if (p.vy > 0){ // falling onto top
                        newY = pl.y() - Player2D.HEIGHT;
                        groundPl = pl;
                    } else if (p.vy < 0){ // hitting bottom
                        newY = pl.y() + pl.height();
                    }
                    p.vy = 0;
                }
            }
            p.y = clamp(newY, 0, st.height - Player2D.HEIGHT);
            // Determine if the player is standing on a platform top this frame (even without collision)
            Platform2D underTouch = groundPl;
            if (underTouch == null){
                for (int i=0;i<st.platforms.size();i++){
                    Platform2D pl = st.platforms.get(i);
                    boolean horizontallyOver = (p.x + Player2D.WIDTH) > pl.x() && p.x < (pl.x() + pl.width());
                    if (horizontallyOver && Math.abs((p.y + Player2D.HEIGHT) - pl.y()) <= 3.0){
                        underTouch = pl;
                        break;
                    }
                }
            }
            boolean onFloor = p.y >= st.height - Player2D.HEIGHT - 0.5;
            p.onGround = (underTouch != null) || onFloor;

            // painting if on ground (paint all cells under player's footprint width)
            if (p.onGround){
                Platform2D under = underTouch;
                if (under != null){
                    int idx = st.platforms.indexOf(under);
                    st.ensurePaintArray(idx, under.cells());
                    String[] arr = st.paint.get(idx);
                    double cellW = under.width() / under.cells();
                    double leftRel = (p.x - under.x());
                    double rightRel = (p.x + Player2D.WIDTH - under.x());
                    int cStart = (int)Math.floor(leftRel / cellW);
                    int cEnd = (int)Math.floor(rightRel / cellW);
                    if (cStart < 0) cStart = 0;
                    if (cEnd >= under.cells()) cEnd = under.cells()-1;
                    boolean awarded = false;
                    boolean decremented = false;
                    boolean canAward = (nowMs - p.lastAwardMs) >= awardIntervalMs;
                    for (int ci = cStart; ci <= cEnd; ci++){
                        String prev = arr[ci];
                        String newCol = p.color.name();
                        if (newCol.equals(prev)) continue; // no-op on same color
                        // Award paint score only once per unique cell for this player
                        long key = com.Color_craze.board.arena.models.ArenaState.cellKey(idx, ci);
                        java.util.Set<Long> credited = st.creditedByPlayer.computeIfAbsent(p.playerId, k -> new java.util.HashSet<>());
                        if (canAward && !credited.contains(key) && !awarded){
                            p.score += 1;
                            credited.add(key);
                            p.lastAwardMs = nowMs;
                            awarded = true; // limit to +1 per tick even if footprint spans multiple new cells
                        }
                        // If there was a previous owner (other color), decrement their score and remove their credit
                        // Extra rule: only allow decrement when the painting player is actually moving horizontally
                        // to avoid a stationary player "fighting back" every frame and draining the passer's score.
                        boolean isMovingHorizontally = Math.abs(p.vx) > 1.0;
                        if (!decremented && prev != null && !prev.equals(newCol) && !decrementedCellsThisTick.contains(key) && isMovingHorizontally){
                            Player2D prevOwner = null;
                            for (Player2D tp : st.players.values()){
                                if (tp.color.name().equals(prev)) { prevOwner = tp; break; }
                            }
                            if (prevOwner != null && prevOwner.score > 0){
                                prevOwner.score -= 1;
                                java.util.Set<Long> prevSet = st.creditedByPlayer.get(prevOwner.playerId);
                                if (prevSet != null) prevSet.remove(key);
                                decrementedCellsThisTick.add(key); // ensure only one decrement for this cell in this tick
                                decremented = true; // limit to -1 per tick to balance the +1 cap
                            }
                        }
                        // Finally, paint the cell
                        arr[ci] = newCol;
                        // don't break; paint entire footprint so the stripe isn't too thin
                    }
                }
            }
        }
    }

    private void updateBots(String code, ArenaState st){
        final long now = System.currentTimeMillis();
        for (Player2D p : new java.util.ArrayList<>(st.players.values())){
            if (p.playerId == null || !p.playerId.startsWith("bot_")) continue;
            String key = code+"|"+p.playerId;
            InputState in = inputs.computeIfAbsent(key, k -> new InputState());
            // Determine platform underfoot similar to collision check
            com.Color_craze.board.arena.models.Platform2D under = null;
            for (var pl : st.platforms){
                boolean over = (p.x + Player2D.WIDTH) > pl.x() && p.x < (pl.x() + pl.width());
                if (over && Math.abs((p.y + Player2D.HEIGHT) - pl.y()) <= 3.0){ under = pl; break; }
            }
            int dir = botDir.getOrDefault(key, (Math.random() < 0.5 ? -1 : 1));
            long nextDec = botNextDecisionMs.getOrDefault(key, 0L);
            // If at edge of current platform, reverse
            if (under != null){
                double leftEdge = under.x();
                double rightEdge = under.x() + under.width() - Player2D.WIDTH;
                if (p.x <= leftEdge + 8) dir = 1;
                else if (p.x >= rightEdge - 8) dir = -1;
            }
            // Periodic decision: every 700-1200ms randomly adjust direction or jump
            if (now >= nextDec){
                botNextDecisionMs.put(key, now + 700 + (long)(Math.random()*500));
                double r = Math.random();
                if (r < 0.20) dir = -dir; // 20% flip
                if (p.onGround && r > 0.70) in.jump = true; // 30% chance to hop when grounded
            }
            botDir.put(key, dir);
            in.left = dir < 0; in.right = dir > 0;
        }
    }

    private double clamp(double v, double lo, double hi){
        return Math.max(lo, Math.min(hi, v));
    }

    // kept for reference; now unused
}
