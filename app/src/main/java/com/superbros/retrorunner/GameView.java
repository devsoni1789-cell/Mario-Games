package com.superbros.retrorunner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Retro Runner - an original side-scrolling platformer.
 * Hero: "Bolt". Enemies: "Slimes". Pickups: coins and power gems.
 * Levels are generated procedurally and get harder each time.
 */
public class GameView extends View {

    static final int ROWS = 14;
    static final float STEP = 1f / 60f;
    static final float GRAVITY = 55f, MAX_FALL = 28f, RUN = 7f, JUMP = 19f;
    static final int TITLE = 0, PLAY = 1, DYING = 2, CLEAR = 3, OVER = 4, PAUSED = 5;

    static class Body {
        float x, y, w, h, vx, vy;
        boolean ground, wall;
        int headTx = -1, headTy = -1;
    }

    static class Enemy {
        Body b = new Body();
        int dir = -1;
        boolean alive = true;
        float squash = 0;
    }

    static class PowerUp {
        Body b = new Body();
        int dir = 1;
    }

    static class Particle {
        float x, y, vx, vy, life, size;
        int color;
    }

    static class Bump {
        int tx, ty;
        float t;
    }

    char[][] map = new char[ROWS][1];
    int cols = 1;
    final Body p = new Body();
    final List<Enemy> enemies = new ArrayList<>();
    final List<PowerUp> powerUps = new ArrayList<>();
    final List<Particle> particles = new ArrayList<>();
    final List<Bump> bumps = new ArrayList<>();
    final Random rnd = new Random();

    int state = TITLE;
    float stateTime = 0, time = 0, acc = 0;
    long last = 0;
    private GameLoop gameLoop;
    private final SaveManager saveManager;
    private final SoundManager soundManager;
    private final TouchController touchController;
    int level = 1, lives = 3, coins = 0, score = 0, best = 0;
    boolean big = false;
    float invuln = 0, coyote = 0, jumpBuffer = 0, walkPhase = 0, camX = 0;
    int facing = 1;
    boolean left, right, jumpHeld, jumpQueued;
    boolean touchActive;
    boolean pendingGrow = false;
    boolean muted = false;
    int stars = 0;
    int combo = 0;
    float comboTimer = 0;
    float shake = 0;
    final List<Platform> platforms = new ArrayList<>();
    final List<Spike> spikes = new ArrayList<>();
    final List<Spring> springs = new ArrayList<>();
    final List<Checkpoint> checkpoints = new ArrayList<>();
    boolean checkpointReached = false;
    int checkpointX = 2;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rect = new RectF();
    final Path path = new Path();
    Shader sky;
    float T = 50f;
    int W = 1, H = 1;
    final SharedPreferences prefs;

    public GameView(Context ctx) {
        super(ctx);
        setFocusable(true);
        prefs = ctx.getSharedPreferences("retrorunner", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        saveManager = new SaveManager(ctx);
        soundManager = new SoundManager();
        touchController = new TouchController();
        muted = saveManager.isMuted();
        stars = saveManager.getStars();
        gameLoop = new GameLoop(this);
        startLevel(1);
        state = TITLE;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w;
        H = h;
        T = h / (float) ROWS;
        sky = new LinearGradient(0, 0, 0, h, Color.rgb(90, 170, 255), Color.rgb(200, 235, 255),
                Shader.TileMode.CLAMP);
    }

    void buildLevel(int lvl) {
        Random r = new Random(1234L + lvl * 7919L);
        cols = Math.min(90 + lvl * 15, 220);
        map = new char[ROWS][cols];
        for (char[] row : map) Arrays.fill(row, ' ');
        enemies.clear();
        powerUps.clear();
        bumps.clear();
        platforms.clear();
        spikes.clear();
        springs.clear();
        checkpoints.clear();
        checkpointReached = false;

        boolean[] gap = new boolean[cols];
        // Every gap is deliberately bounded to the tested jump envelope.
        // Longer gaps are possible only when a landing platform is inserted.
        int gapChance = Math.min(12 + lvl * 2, 28);
        int x = 14;
        while (x < cols - 16) {
            if (r.nextInt(100) < gapChance) {
                int w = 1 + r.nextInt(3); // never blindly create an impossible 4+ tile void
                for (int i = 0; i < w && x + i < cols; i++) gap[x + i] = true;
                x += w + 6 + r.nextInt(5);
            } else {
                x += 3;
            }
        }
        for (int c = 0; c < cols; c++) {
            if (!gap[c]) {
                map[12][c] = '#';
                map[13][c] = '#';
            }
        }

        // Add deterministic hazards/platforms only where a ground route remains.
        for (int c = 18; c < cols - 12; c += 13 + r.nextInt(9)) {
            if (!gap[c] && !gap[Math.min(cols - 1, c + 1)] && lvl >= 2) {
                spikes.add(new Spike(c + 0.18f, 11.55f, 0.64f, 0.45f));
            }
        }
        if (lvl >= 2) {
            for (int c = 25; c < cols - 18; c += 28 + r.nextInt(12)) {
                if (gap[c]) continue;
                Platform pl = new Platform();
                pl.x = c; pl.y = 9.5f; pl.w = 2.4f; pl.h = 0.35f;
                pl.baseX = pl.x; pl.range = 1.5f; pl.speed = 0.9f + lvl * 0.03f;
                platforms.add(pl);
            }
        }
        if (lvl >= 3) {
            for (int c = 35; c < cols - 10; c += 37) {
                if (!gap[c]) springs.add(new Spring(c + 0.18f, 11.35f));
            }
        }
        if (lvl % 3 == 0) {
            int cx = Math.min(cols - 14, 50 + lvl * 4);
            if (!gap[cx]) checkpoints.add(new Checkpoint(cx + 0.25f, 10.2f));
        }

        int enemyChance = Math.min(2 + lvl, 4);
        x = 9;
        while (x < cols - 18) {
            int kind = r.nextInt(7);
            if (kind == 0) {
                int len = 3 + r.nextInt(3);
                for (int i = 0; i < len && x + i < cols; i++)
                    map[9][x + i] = (i % 2 == 1) ? '?' : 'B';
            } else if (kind == 1) {
                for (int i = 0; i < 5 && x + i < cols; i++) {
                    int cy = (i == 0 || i == 4) ? 10 : (i == 2 ? 8 : 9);
                    map[cy][x + i] = 'o';
                }
            } else if (kind == 2) {
                int h = 2 + r.nextInt(2);
                boolean ok = true;
                for (int i = 0; i < h && x + i < cols; i++) if (gap[x + i]) ok = false;
                if (ok) {
                    for (int i = 0; i < h && x + i < cols; i++)
                        for (int j = 0; j <= i; j++) map[11 - j][x + i] = '#';
                }
            } else if (kind == 3) {
                if (x + 2 < cols) {
                    map[8][x] = '?';
                    map[8][x + 1] = 'B';
                    map[8][x + 2] = '?';
                }
            }
            if (r.nextInt(6) < enemyChance + 1) {
                int ex = x + 3 + r.nextInt(3);
                if (ex + 1 < cols && ex > 5 && !gap[ex] && !gap[ex + 1]
                        && map[11][ex] == ' ' && !nearHazard(ex)) addEnemy(ex);
            }
            x += 7 + r.nextInt(5);
        }
    }

    boolean nearHazard(int tx) {
        for (Spike sp : spikes) if (Math.abs(sp.x - tx) < 1.8f) return true;
        return false;
    }

    void addEnemy(int tx) {
        Enemy e = new Enemy();
        e.b.w = 0.8f;
        e.b.h = 0.8f;
        e.b.x = tx + 0.1f;
        e.b.y = 12 - e.b.h;
        e.dir = rnd.nextBoolean() ? 1 : -1;
        enemies.add(e);
    }

    void newGame() {
        lives = 3;
        coins = 0;
        score = 0;
        big = false;
        clearInput();
        startLevel(1);
    }

    void startLevel(int lvl) {
        level = lvl;
        buildLevel(lvl);
        particles.clear();
        p.w = 0.7f;
        p.h = big ? 1.7f : 0.9f;
        p.x = 2;
        p.y = 12 - p.h;
        p.vx = 0;
        p.vy = 0;
        pendingGrow = false;
        combo = 0;
        comboTimer = 0;
        camX = 0;
        if (checkpointReached && checkpointX > 2) p.x = checkpointX;
        invuln = 0;
        facing = 1;
        state = PLAY;
        stateTime = 0;
        resetFrameClock();
    }

    boolean solid(int tx, int ty) {
        if (tx < 0 || tx >= cols) return true;
        if (ty < 0 || ty >= ROWS) return false;
        char c = map[ty][tx];
        return c == '#' || c == 'B' || c == '?' || c == 'U';
    }

    void physics(Body b, float dt) {
        b.wall = false;
        b.headTx = -1;
        b.vy = Math.min(b.vy + GRAVITY * dt, MAX_FALL);

        b.x += b.vx * dt;
        int top = (int) Math.floor(b.y + 0.05f);
        int bot = (int) Math.floor(b.y + b.h - 0.05f);
        if (b.vx > 0) {
            int tx = (int) Math.floor(b.x + b.w);
            for (int ty = top; ty <= bot; ty++) {
                if (solid(tx, ty)) {
                    b.x = tx - b.w - 0.001f;
                    b.vx = 0;
                    b.wall = true;
                    break;
                }
            }
        } else if (b.vx < 0) {
            int tx = (int) Math.floor(b.x);
            for (int ty = top; ty <= bot; ty++) {
                if (solid(tx, ty)) {
                    b.x = tx + 1 + 0.001f;
                    b.vx = 0;
                    b.wall = true;
                    break;
                }
            }
        }

        b.y += b.vy * dt;
        b.ground = false;
        int l = (int) Math.floor(b.x + 0.02f);
        int rr = (int) Math.floor(b.x + b.w - 0.02f);
        if (b.vy > 0) {
            int ty = (int) Math.floor(b.y + b.h);
            for (int tx = l; tx <= rr; tx++) {
                if (solid(tx, ty)) {
                    b.y = ty - b.h;
                    b.vy = 0;
                    b.ground = true;
                    break;
                }
            }
        } else if (b.vy < 0) {
            int ty = (int) Math.floor(b.y);
            int cx = (int) Math.floor(b.x + b.w / 2);
            int hit = -1;
            for (int tx = l; tx <= rr; tx++) {
                if (solid(tx, ty) && (hit == -1 || tx == cx)) hit = tx;
            }
            if (hit != -1) {
                b.y = ty + 1 + 0.001f;
                b.vy = 0;
                b.headTx = hit;
                b.headTy = ty;
            }
        }
    }

    void update(float dt) {
        time += dt;
        stateTime += dt;
        updateParticles(dt);
        for (Iterator<Bump> it = bumps.iterator(); it.hasNext(); ) {
            Bump b = it.next();
            b.t += dt * 6f;
            if (b.t >= 1f) it.remove();
        }

        if (comboTimer > 0) comboTimer -= dt;
        else combo = 0;
        shake = Math.max(0, shake - dt * 5f);

        if (state == PLAY) {
            updatePlay(dt);
        } else if (state == DYING) {
            p.vy += GRAVITY * dt;
            p.y += p.vy * dt;
            if (stateTime > 1.6f) {
                if (lives <= 0) {
                    state = OVER;
                    stateTime = 0;
                    if (score > best) {
                        best = score;
                        prefs.edit().putInt("best", best).apply();
                    }
                } else {
                    big = false;
                    startLevel(level);
                }
            }
        } else if (state == CLEAR) {
            if (stateTime > 2.2f) startLevel(level + 1);
        }
    }

    void updatePlay(float dt) {
        float target = (right ? 1 : 0) - (left ? 1 : 0);
        if (target != 0) facing = (int) target;
        float accel = p.ground ? 14f : 7f;
        p.vx += (target * RUN - p.vx) * Math.min(1f, accel * dt);

        if (p.ground) coyote = 0.1f;
        else coyote -= dt;
        jumpBuffer -= dt;
        if (jumpQueued) {
            jumpBuffer = 0.12f;
            jumpQueued = false;
        }
        if (jumpBuffer > 0 && coyote > 0) {
            p.vy = -JUMP;
            p.ground = false;
            coyote = 0;
            jumpBuffer = 0;
            burst(p.x + p.w / 2, p.y + p.h, 5, Color.rgb(230, 230, 230));
        }
        if (!jumpHeld && p.vy < -7f) p.vy = -7f;

        if (pendingGrow) tryApplyGrowth();
        physics(p, dt);
        if (p.headTx >= 0) hitBlock(p.headTx, p.headTy);
        updatePlatforms(dt);
        updateSpringsAndCheckpoints();
        checkSpikes();

        if (Math.abs(p.vx) > 0.5f && p.ground) walkPhase += Math.abs(p.vx) * dt * 1.6f;
        if (p.y > ROWS + 1) {
            die();
            return;
        }
        if (invuln > 0) invuln -= dt;

        collectCoins();
        updateEnemies(dt);
        updatePowerUps(dt);

        if (state == PLAY && p.x + p.w > cols - 5) {
            state = CLEAR;
            stateTime = 0;
            score += 1000;
            p.vx = 0;
        }

        float viewT = W / T;
        float targetCam = p.x - viewT * 0.4f;
        targetCam = Math.max(0, Math.min(targetCam, cols - viewT));
        camX += (targetCam - camX) * Math.min(1f, 8f * dt);
    }

    void updateEnemies(float dt) {
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext(); ) {
            Enemy e = it.next();
            if (!e.alive) {
                e.squash += dt;
                if (e.squash > 0.5f) it.remove();
                continue;
            }
            e.b.vx = e.dir * (1.8f + Math.min(0.8f, level * 0.04f));
            physics(e.b, dt);
            if (e.b.wall) e.dir = -e.dir;
            if (e.b.y > ROWS + 2) {
                it.remove();
                continue;
            }
            if (state != PLAY) continue;
            boolean overlap = p.x < e.b.x + e.b.w && p.x + p.w > e.b.x
                    && p.y < e.b.y + e.b.h && p.y + p.h > e.b.y;
            if (!overlap) continue;
            float playerBottom = p.y + p.h;
            float previousBottom = playerBottom - p.vy * STEP;
            boolean crossedEnemyTop = previousBottom <= e.b.y + 0.12f && playerBottom >= e.b.y;
            if (p.vy > 0 && crossedEnemyTop && p.x + p.w > e.b.x + 0.12f
                    && p.x < e.b.x + e.b.w - 0.12f) {
                e.alive = false;
                e.squash = 0;
                p.vy = jumpHeld ? -15f : -10f;
                combo = Math.min(combo + 1, 8);
                comboTimer = 2f;
                score += 200 * combo;
                shake = 0.08f;
                burst(e.b.x + e.b.w / 2, e.b.y + e.b.h / 2, 8, Color.rgb(170, 90, 220));
            } else if (invuln <= 0) {
                hurt();
            }
        }
    }

    void updatePowerUps(float dt) {
        for (Iterator<PowerUp> it = powerUps.iterator(); it.hasNext(); ) {
            PowerUp u = it.next();
            u.b.vx = u.dir * 2.5f;
            physics(u.b, dt);
            if (u.b.wall) u.dir = -u.dir;
            if (u.b.y > ROWS + 2) {
                it.remove();
                continue;
            }
            boolean overlap = p.x < u.b.x + u.b.w && p.x + p.w > u.b.x
                    && p.y < u.b.y + u.b.h && p.y + p.h > u.b.y;
            if (overlap) {
                grow();
                burst(u.b.x + 0.4f, u.b.y + 0.4f, 12, Color.rgb(255, 80, 220));
                it.remove();
            }
        }
    }

    void collectCoins() {
        int x0 = (int) Math.floor(p.x), x1 = (int) Math.floor(p.x + p.w);
        int y0 = (int) Math.floor(p.y), y1 = (int) Math.floor(p.y + p.h);
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                if (tx >= 0 && tx < cols && ty >= 0 && ty < ROWS && map[ty][tx] == 'o') {
                    map[ty][tx] = ' ';
                    addCoin();
                    burst(tx + 0.5f, ty + 0.5f, 5, Color.rgb(255, 215, 40));
                }
            }
        }
    }

    void addCoin() {
        coins++;
        score += 100;
        if (coins % 50 == 0) lives++;
    }

    void hitBlock(int tx, int ty) {
        if (tx < 0 || tx >= cols || ty < 0 || ty >= ROWS) return;
        char c = map[ty][tx];
        if (c == '?') {
            map[ty][tx] = 'U';
            addBump(tx, ty);
            if (rnd.nextInt(4) == 0) {
                PowerUp u = new PowerUp();
                u.b.w = 0.8f;
                u.b.h = 0.8f;
                u.b.x = tx + 0.1f;
                u.b.y = ty - 0.85f;
                u.b.vy = -6f;
                powerUps.add(u);
            } else {
                addCoin();
                burst(tx + 0.5f, ty - 0.2f, 6, Color.rgb(255, 215, 40));
            }
        } else if (c == 'B') {
            if (big) {
                map[ty][tx] = ' ';
                score += 50;
                burst(tx + 0.5f, ty + 0.5f, 10, Color.rgb(210, 110, 50));
            } else {
                addBump(tx, ty);
            }
        }
    }

    void addBump(int tx, int ty) {
        Bump b = new Bump();
        b.tx = tx;
        b.ty = ty;
        bumps.add(b);
    }

    boolean overlapsSolid(float x, float y, float w, float h) {
        int l = (int)Math.floor(x + 0.02f), rr = (int)Math.floor(x + w - 0.02f);
        int top = (int)Math.floor(y + 0.02f), bot = (int)Math.floor(y + h - 0.02f);
        for (int ty = top; ty <= bot; ty++)
            for (int tx = l; tx <= rr; tx++)
                if (solid(tx, ty)) return true;
        return false;
    }

    void tryApplyGrowth() {
        if (big) { pendingGrow = false; return; }
        float bottom = p.y + p.h;
        float nh = 1.7f;
        float ny = bottom - nh;
        if (!overlapsSolid(p.x, ny, p.w, nh)) {
            p.y = ny;
            p.h = nh;
            big = true;
            pendingGrow = false;
            score += 500;
            shake = 0.1f;
            soundManager.play(SoundManager.GROW, muted);
        }
    }

    void grow() {
        if (!big) {
            pendingGrow = true;
            tryApplyGrowth();
        } else {
            score += 250;
        }
    }

    void hurt() {
        if (big) {
            float bottom = p.y + p.h;
            float nh = 0.9f;
            p.y = bottom - nh;
            p.h = nh;
            big = false;
            pendingGrow = false;
            invuln = 1.5f;
            p.vy = -8f;
            shake = 0.12f;
            soundManager.play(SoundManager.HURT, muted);
        } else {
            die();
        }
    }

    void die() {
        state = DYING;
        stateTime = 0;
        lives--;
        p.vy = -14f;
        shake = 0.2f;
        soundManager.play(SoundManager.DIE, muted);
        p.vx = 0;
        clearInput();
        burst(p.x + p.w / 2, p.y + p.h / 2, 14, Color.WHITE);
    }

    void burst(float x, float y, int n, int color) {
        if (particles.size() > 220) particles.subList(0, Math.min(60, particles.size())).clear();
        for (int i = 0; i < n && particles.size() < 260; i++) {
            Particle q = new Particle();
            q.x = x;
            q.y = y;
            q.vx = (rnd.nextFloat() - 0.5f) * 8f;
            q.vy = -rnd.nextFloat() * 7f;
            q.life = 0.5f + rnd.nextFloat() * 0.4f;
            q.size = 0.12f + rnd.nextFloat() * 0.12f;
            q.color = color;
            particles.add(q);
        }
    }

    void updateParticles(float dt) {
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle q = it.next();
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            q.vy += 30f * dt;
            q.life -= dt;
            if (q.life <= 0) it.remove();
        }
    }

    void resetFrameClock() { last = System.nanoTime(); acc = 0; }

    void onFrame(long nowNanos) {
        if (last == 0) last = nowNanos;
        float dt = (nowNanos - last) / 1e9f;
        last = nowNanos;
        dt = Math.min(dt, 0.05f);
        acc += dt;
        while (acc >= STEP) {
            update(STEP);
            acc -= STEP;
        }
        invalidate();
    }

    void startLoop() { if (gameLoop != null) gameLoop.start(); }
    void stopLoop() { if (gameLoop != null) gameLoop.stop(); }

    boolean isPlaying() { return state == PLAY; }
    boolean isPaused() { return state == PAUSED; }

    void pauseGame() {
        if (state == PLAY) {
            state = PAUSED;
            clearInput();
            stopLoop();
        }
    }

    void resumeGame() {
        if (state == PAUSED) {
            state = PLAY;
            resetFrameClock();
            startLoop();
        }
    }

    void lifecyclePause() {
        clearInput();
        stopLoop();
    }

    void lifecycleResume() {
        resetFrameClock();
        if (state == PLAY) startLoop();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startLoop();
    }

    @Override protected void onDetachedFromWindow() {
        stopLoop();
        soundManager.release();
        super.onDetachedFromWindow();
    }

    void clearInput() {
        left = right = jumpHeld = false;
        jumpQueued = false;
        touchActive = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) {
            if (state == TITLE) { newGame(); return true; }
            if (state == OVER && stateTime > 0.8f) { state = TITLE; clearInput(); return true; }
            if (state == PAUSED) { resumeGame(); return true; }
        }
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            clearInput(); return true;
        }
        if (state != PLAY) return true;

        boolean l=false,r=false,j=false;
        int skip = act == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1;
        for(int i=0;i<e.getPointerCount();i++){
            if(i==skip) continue;
            TouchController.Action a=touchController.map(e.getX(i),e.getY(i),W,H,T);
            if(a==TouchController.Action.LEFT) l=true;
            if(a==TouchController.Action.RIGHT) r=true;
            if(a==TouchController.Action.JUMP) j=true;
            if(a==TouchController.Action.PAUSE){ pauseGame(); return true; }
        }
        if(j && !jumpHeld) jumpQueued=true;
        left=l; right=r; jumpHeld=j;
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        render(c);
    }

    void render(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        if (sky == null) sky = new LinearGradient(0, 0, 0, H, Color.rgb(90,170,255), Color.rgb(200,235,255), Shader.TileMode.CLAMP);
        paint.setShader(sky);
        c.save();
        if (shake > 0) c.translate((rnd.nextFloat()-0.5f)*T*shake*2f, (rnd.nextFloat()-0.5f)*T*shake*2f);
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);

        paint.setColor(Color.rgb(120, 200, 130));
        float period = T * 11;
        float off = camX * T * 0.3f;
        for (int i = -1; i < W / period + 2; i++) {
            float cx = i * period - (off % period);
            rect.set(cx - T * 5, 12 * T - T * 3, cx + T * 5, 12 * T + T * 4);
            c.drawOval(rect, paint);
        }

        paint.setColor(Color.argb(230, 255, 255, 255));
        float cp = T * 9;
        float coff = camX * T * 0.5f;
        for (int i = -1; i < W / cp + 2; i++) {
            float cx = i * cp - (coff % cp);
            float cy = T * (1.5f + ((i & 1) * 1.8f));
            rect.set(cx, cy, cx + T * 2.4f, cy + T * 0.9f);
            c.drawOval(rect, paint);
            rect.set(cx + T * 0.5f, cy - T * 0.4f, cx + T * 1.8f, cy + T * 0.6f);
            c.drawOval(rect, paint);
        }

        int c0 = Math.max(0, (int) camX - 1);
        int c1 = Math.min(cols - 1, (int) (camX + W / T) + 1);
        for (int ty = 0; ty < ROWS; ty++) {
            for (int tx = c0; tx <= c1; tx++) {
                char t = map[ty][tx];
                if (t == ' ') continue;
                float sx = (tx - camX) * T;
                float sy = ty * T;
                for (int i = 0; i < bumps.size(); i++) {
                    Bump b = bumps.get(i);
                    if (b.tx == tx && b.ty == ty) sy -= (float) Math.sin(b.t * Math.PI) * T * 0.3f;
                }
                boolean topOpen = ty == 0 || !solid(tx, ty - 1);
                drawTile(c, t, sx, sy, topOpen);
            }
        }

        for (Platform pl : platforms) drawPlatform(c, pl);
        for (Spike sp : spikes) drawSpike(c, sp);
        for (Spring sp : springs) drawSpring(c, sp);
        for (Checkpoint checkpoint : checkpoints) drawCheckpoint(c, checkpoint);
        drawGoal(c);
        for (PowerUp u : powerUps) drawGem(c, u);
        for (Enemy e : enemies) drawEnemy(c, e);
        if (state != TITLE) drawPlayer(c);

        for (Particle q : particles) {
            paint.setColor(q.color);
            float s = q.size * T;
            c.drawRect((q.x - camX) * T - s, q.y * T - s, (q.x - camX) * T + s, q.y * T + s, paint);
        }

        c.restore();
        drawHud(c);
        if (state == PLAY) drawControls(c);
        drawOverlays(c);
    }

    void drawTile(Canvas c, char t, float x, float y, boolean topOpen) {
        switch (t) {
            case '#':
                paint.setColor(Color.rgb(150, 95, 50));
                c.drawRect(x, y, x + T + 1, y + T + 1, paint);
                paint.setColor(Color.rgb(125, 75, 38));
                c.drawRect(x + T * 0.2f, y + T * 0.55f, x + T * 0.4f, y + T * 0.7f, paint);
                c.drawRect(x + T * 0.65f, y + T * 0.75f, x + T * 0.85f, y + T * 0.9f, paint);
                if (topOpen) {
                    paint.setColor(Color.rgb(80, 180, 70));
                    c.drawRect(x, y, x + T + 1, y + T * 0.28f, paint);
                }
                break;
            case 'B':
                paint.setColor(Color.rgb(210, 110, 50));
                c.drawRect(x, y, x + T + 1, y + T + 1, paint);
                paint.setColor(Color.rgb(120, 55, 25));
                c.drawRect(x, y + T * 0.48f, x + T + 1, y + T * 0.52f, paint);
                c.drawRect(x + T * 0.48f, y, x + T * 0.52f, y + T * 0.48f, paint);
                c.drawRect(x + T * 0.23f, y + T * 0.52f, x + T * 0.27f, y + T, paint);
                c.drawRect(x, y, x + T + 1, y + T * 0.05f, paint);
                break;
            case '?':
                paint.setColor(Color.rgb(250, 200, 40));
                c.drawRect(x, y, x + T + 1, y + T + 1, paint);
                paint.setColor(Color.rgb(160, 100, 10));
                c.drawRect(x, y, x + T + 1, y + T * 0.07f, paint);
                c.drawRect(x, y + T * 0.93f, x + T + 1, y + T + 1, paint);
                c.drawRect(x, y, x + T * 0.07f, y + T + 1, paint);
                c.drawRect(x + T * 0.93f, y, x + T + 1, y + T + 1, paint);
                text(c, "?", x + T / 2, y + T * 0.72f, T * 0.7f, Paint.Align.CENTER,
                        Color.rgb(140, 80, 0), false);
                break;
            case 'U':
                paint.setColor(Color.rgb(130, 100, 70));
                c.drawRect(x, y, x + T + 1, y + T + 1, paint);
                paint.setColor(Color.rgb(90, 65, 45));
                c.drawRect(x, y, x + T + 1, y + T * 0.07f, paint);
                c.drawRect(x, y + T * 0.93f, x + T + 1, y + T + 1, paint);
                break;
            case 'o': {
                float w = (0.25f + 0.25f * Math.abs((float) Math.cos(time * 4 + x * 0.01f))) * T;
                paint.setColor(Color.rgb(255, 215, 40));
                rect.set(x + T / 2 - w, y + T * 0.2f, x + T / 2 + w, y + T * 0.8f);
                c.drawOval(rect, paint);
                paint.setColor(Color.rgb(200, 150, 10));
                rect.set(x + T / 2 - w * 0.5f, y + T * 0.32f, x + T / 2 + w * 0.5f, y + T * 0.68f);
                c.drawOval(rect, paint);
                break;
            }
            default:
                break;
        }
    }

    void drawGoal(Canvas c) {
        float x = (cols - 5 - camX) * T;
        if (x < -T * 3 || x > W + T) return;
        paint.setColor(Color.rgb(230, 230, 230));
        c.drawRect(x + T * 0.4f, 5 * T, x + T * 0.55f, 12 * T, paint);
        paint.setColor(Color.rgb(255, 215, 40));
        c.drawCircle(x + T * 0.47f, 5 * T, T * 0.25f, paint);
        paint.setColor(Color.rgb(230, 50, 80));
        path.reset();
        float wave = (float) Math.sin(time * 6) * T * 0.1f;
        path.moveTo(x + T * 0.55f, 5.2f * T);
        path.lineTo(x + T * 2.1f, 5.8f * T + wave);
        path.lineTo(x + T * 0.55f, 6.5f * T);
        path.close();
        c.drawPath(path, paint);
    }

    void drawEnemy(Canvas c, Enemy e) {
        float x = (e.b.x - camX) * T, y = e.b.y * T, w = e.b.w * T, h = e.b.h * T;
        if (x < -T * 2 || x > W + T) return;
        paint.setColor(Color.rgb(150, 70, 200));
        if (!e.alive) {
            rect.set(x, y + h * 0.72f, x + w, y + h);
            c.drawRoundRect(rect, w * 0.3f, w * 0.3f, paint);
            return;
        }
        rect.set(x, y + h * 0.1f, x + w, y + h);
        c.drawRoundRect(rect, w * 0.45f, w * 0.45f, paint);
        paint.setColor(Color.WHITE);
        c.drawCircle(x + w * 0.3f, y + h * 0.42f, w * 0.15f, paint);
        c.drawCircle(x + w * 0.7f, y + h * 0.42f, w * 0.15f, paint);
        paint.setColor(Color.BLACK);
        float po = e.dir * w * 0.05f;
        c.drawCircle(x + w * 0.3f + po, y + h * 0.44f, w * 0.07f, paint);
        c.drawCircle(x + w * 0.7f + po, y + h * 0.44f, w * 0.07f, paint);
    }

    void drawGem(Canvas c, PowerUp u) {
        float x = (u.b.x - camX) * T, y = u.b.y * T, w = u.b.w * T, h = u.b.h * T;
        float pulse = 1f + 0.08f * (float) Math.sin(time * 10);
        paint.setColor(Color.rgb(255, 80, 220));
        path.reset();
        path.moveTo(x + w / 2, y);
        path.lineTo(x + w * (0.5f + 0.5f * pulse), y + h / 2);
        path.lineTo(x + w / 2, y + h);
        path.lineTo(x + w * (0.5f - 0.5f * pulse), y + h / 2);
        path.close();
        c.drawPath(path, paint);
        paint.setColor(Color.argb(200, 255, 255, 255));
        c.drawCircle(x + w * 0.4f, y + h * 0.35f, w * 0.1f, paint);
    }

    void drawPlayer(Canvas c) {
        if (invuln > 0 && ((int) (time * 20) & 1) == 0) return;
        float x = (p.x - camX) * T, y = p.y * T, w = p.w * T, h = p.h * T;
        float f = facing;
        float wave = (float) Math.sin(time * 14) * h * 0.06f;
        float sy = y + h * 0.38f;
        paint.setColor(Color.rgb(230, 50, 60));
        if (f > 0) rect.set(x - w * 0.55f, sy + wave, x + w * 0.2f, sy + h * 0.1f + wave);
        else rect.set(x + w * 0.8f, sy + wave, x + w * 1.55f, sy + h * 0.1f + wave);
        c.drawRect(rect, paint);
        float sw = p.ground ? (float) Math.sin(walkPhase * 2) * w * 0.25f : w * 0.2f;
        paint.setColor(Color.rgb(25, 50, 120));
        rect.set(x + w * 0.1f + sw, y + h * 0.72f, x + w * 0.45f + sw, y + h);
        c.drawRect(rect, paint);
        rect.set(x + w * 0.55f - sw, y + h * 0.72f, x + w * 0.9f - sw, y + h);
        c.drawRect(rect, paint);
        paint.setColor(Color.rgb(40, 110, 230));
        rect.set(x, y + h * 0.4f, x + w, y + h * 0.8f);
        c.drawRoundRect(rect, w * 0.2f, w * 0.2f, paint);
        paint.setColor(Color.rgb(255, 140, 0));
        rect.set(x - w * 0.05f, y, x + w * 1.05f, y + h * 0.45f);
        c.drawRoundRect(rect, w * 0.4f, w * 0.4f, paint);
        paint.setColor(Color.rgb(255, 220, 180));
        if (f > 0) rect.set(x + w * 0.4f, y + h * 0.15f, x + w * 1.0f, y + h * 0.38f);
        else rect.set(x, y + h * 0.15f, x + w * 0.6f, y + h * 0.38f);
        c.drawRect(rect, paint);
        paint.setColor(Color.BLACK);
        c.drawCircle(f > 0 ? x + w * 0.78f : x + w * 0.22f, y + h * 0.25f, w * 0.08f, paint);
    }

    void drawHud(Canvas c) {
        float s = T * 0.6f;
        text(c, "COINS " + coins, T * 0.5f, T * 0.9f, s, Paint.Align.LEFT, Color.WHITE, true);
        text(c, "SCORE " + score, W / 2f, T * 0.9f, s, Paint.Align.CENTER, Color.WHITE, true);
        text(c, "LV " + level + "  LIVES " + Math.max(lives, 0), W - T * 0.5f, T * 0.9f, s,
                Paint.Align.RIGHT, Color.WHITE, true);
        if (combo > 1 && comboTimer > 0)
            text(c, "COMBO x" + combo, W / 2f, T * 1.65f, T * 0.55f, Paint.Align.CENTER,
                    Color.rgb(255,215,40), true);
    }

    void drawControls(Canvas c) {
        float cy = H - T * 2.0f;
        drawButton(c, T * 2.1f, cy, T * 1.4f, left, 0);
        drawButton(c, T * 6.2f, cy, T * 1.4f, right, 1);
        drawButton(c, W - T * 2.6f, cy - T * 0.2f, T * 1.7f, jumpHeld, 2);
        drawButton(c, W - T * 0.65f, T * 0.75f, T * 0.48f, false, 3);
    }

    void drawButton(Canvas c, float cx, float cy, float r, boolean pressed, int kind) {
        paint.setColor(Color.argb(pressed ? 130 : 70, 255, 255, 255));
        c.drawCircle(cx, cy, r, paint);
        paint.setColor(Color.argb(pressed ? 230 : 150, 255, 255, 255));
        path.reset();
        float a = r * 0.45f;
        if (kind == 0) {
            path.moveTo(cx - a, cy);
            path.lineTo(cx + a * 0.6f, cy - a);
            path.lineTo(cx + a * 0.6f, cy + a);
        } else if (kind == 1) {
            path.moveTo(cx + a, cy);
            path.lineTo(cx - a * 0.6f, cy - a);
            path.lineTo(cx - a * 0.6f, cy + a);
        } else if (kind == 3) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, r * 0.18f));
            c.drawLine(cx-a*0.35f, cy-a, cx-a*0.35f, cy+a, paint);
            c.drawLine(cx+a*0.35f, cy-a, cx+a*0.35f, cy+a, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        } else {
            path.moveTo(cx, cy - a);
            path.lineTo(cx - a, cy + a * 0.6f);
            path.lineTo(cx + a, cy + a * 0.6f);
        }
        path.close();
        c.drawPath(path, paint);
    }

    void drawOverlays(Canvas c) {
        if (state == TITLE) {
            paint.setColor(Color.argb(120, 0, 0, 0));
            c.drawRect(0, 0, W, H, paint);
            text(c, "RETRO RUNNER", W / 2f, H * 0.38f, T * 1.8f, Paint.Align.CENTER,
                    Color.rgb(255, 215, 40), true);
            text(c, "TAP TO START", W / 2f, H * 0.58f, T * 0.8f, Paint.Align.CENTER,
                    Color.WHITE, true);
            text(c, "BEST " + best, W / 2f, H * 0.72f, T * 0.6f, Paint.Align.CENTER,
                    Color.WHITE, true);
        } else if (state == CLEAR) {
            text(c, "LEVEL " + level + " CLEAR!", W / 2f, H * 0.4f, T * 1.3f, Paint.Align.CENTER,
                    Color.rgb(255, 215, 40), true);
        } else if (state == PAUSED) {
            paint.setColor(Color.argb(155, 0, 0, 0));
            c.drawRect(0, 0, W, H, paint);
            text(c, "PAUSED", W / 2f, H * 0.42f, T * 1.6f, Paint.Align.CENTER,
                    Color.rgb(255, 215, 40), true);
            text(c, "TAP TO RESUME", W / 2f, H * 0.60f, T * 0.7f, Paint.Align.CENTER,
                    Color.WHITE, true);
        } else if (state == OVER) {
            paint.setColor(Color.argb(150, 0, 0, 0));
            c.drawRect(0, 0, W, H, paint);
            text(c, "GAME OVER", W / 2f, H * 0.42f, T * 1.8f, Paint.Align.CENTER,
                    Color.rgb(255, 90, 90), true);
            text(c, "SCORE " + score + "   BEST " + best, W / 2f, H * 0.58f, T * 0.7f,
                    Paint.Align.CENTER, Color.WHITE, true);
            text(c, "TAP TO CONTINUE", W / 2f, H * 0.72f, T * 0.6f, Paint.Align.CENTER,
                    Color.WHITE, true);
        }
    }

    void updatePlatforms(float dt) {
        for (Platform pl : platforms) {
            pl.phase += dt * pl.speed;
            pl.x = pl.baseX + (float)Math.sin(pl.phase) * pl.range;
        }
    }

    void updateSpringsAndCheckpoints() {
        for (Spring sp : springs) {
            if (p.x+p.w > sp.x && p.x < sp.x+0.64f && p.y+p.h > sp.y && p.y+p.h < sp.y+0.7f && p.vy >= 0) {
                p.y = sp.y - p.h;
                p.vy = -24f;
                score += 50;
                shake = 0.08f;
                soundManager.play(SoundManager.JUMP, muted);
            }
        }
        for (Checkpoint cp : checkpoints) {
            if (!cp.reached && p.x+p.w > cp.x && p.x < cp.x+0.6f) {
                cp.reached = true;
                checkpointReached = true;
                checkpointX = (int)cp.x;
                lives = Math.max(lives, 2);
                score += 300;
                soundManager.play(SoundManager.COIN, muted);
            }
        }
    }

    void checkSpikes() {
        for (Spike sp : spikes) {
            if (p.x+p.w > sp.x+0.08f && p.x < sp.x+sp.w-0.08f
                    && p.y+p.h > sp.y+0.05f && p.y < sp.y+sp.h) {
                if (invuln <= 0) hurt();
                return;
            }
        }
    }

    void drawPlatform(Canvas c, Platform pl) {
        float x=(pl.x-camX)*T,y=pl.y*T;
        paint.setColor(Color.rgb(70,130,180));
        c.drawRoundRect(x,y,x+pl.w*T,y+pl.h*T,T*0.12f,T*0.12f,paint);
        paint.setColor(Color.rgb(130,210,230));
        c.drawRect(x,y,x+pl.w*T,y+pl.h*T*0.25f,paint);
    }

    void drawSpike(Canvas c, Spike sp) {
        float x=(sp.x-camX)*T,y=sp.y*T,w=sp.w*T,h=sp.h*T;
        paint.setColor(Color.rgb(220,220,225));
        path.reset();
        path.moveTo(x,y+h); path.lineTo(x+w*0.5f,y); path.lineTo(x+w,y+h); path.close();
        c.drawPath(path,paint);
    }

    void drawSpring(Canvas c, Spring sp) {
        float x=(sp.x-camX)*T,y=sp.y*T;
        paint.setColor(Color.rgb(70,220,90));
        c.drawRect(x,y,x+0.64f*T,y+0.22f*T,paint);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(T*0.06f);
        c.drawArc(x+T*0.08f,y+T*0.05f,x+T*0.56f,y+T*0.45f,0,180,false,paint);
        paint.setStyle(Paint.Style.FILL);
    }

    void drawCheckpoint(Canvas c, Checkpoint cp) {
        float x=(cp.x-camX)*T,y=cp.y*T;
        paint.setColor(cp.reached?Color.rgb(80,230,100):Color.rgb(230,230,230));
        c.drawRect(x,y,x+T*0.12f,y+T*1.8f,paint);
        paint.setColor(cp.reached?Color.rgb(80,230,100):Color.rgb(255,210,50));
        path.reset(); path.moveTo(x+T*0.12f,y); path.lineTo(x+T*0.85f,y+T*0.28f);
        path.lineTo(x+T*0.12f,y+T*0.58f); path.close(); c.drawPath(path,paint);
    }

    void text(Canvas c, String s, float x, float y, float size, Paint.Align align, int color,
              boolean shadow) {
        paint.setTextSize(size);
        paint.setTextAlign(align);
        if (shadow) {
            paint.setColor(Color.argb(180, 0, 0, 0));
            c.drawText(s, x + size * 0.06f, y + size * 0.06f, paint);
        }
        paint.setColor(color);
        c.drawText(s, x, y, paint);
    }
    static class Platform { float x,y,w,h,baseX,range,speed,phase; }
    static class Spike { float x,y,w,h; Spike(float x,float y,float w,float h){this.x=x;this.y=y;this.w=w;this.h=h;} }
    static class Spring { float x,y; Spring(float x,float y){this.x=x;this.y=y;} }
    static class Checkpoint { float x,y; boolean reached; Checkpoint(float x,float y){this.x=x;this.y=y;} }

}
