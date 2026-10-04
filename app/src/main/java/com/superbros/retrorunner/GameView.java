package com.superbros.retrorunner;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GameView extends View {
    private static final float W = 960f, H = 540f;
    private static final float PW = 36f, PH = 54f;
    private static final float GRAVITY = 1500f, RUN = 245f, JUMP = 610f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> solids = new ArrayList<>();
    private final List<Coin> coins = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<PowerUp> powerUps = new ArrayList<>();

    private float px = 90, py = 360, vx, vy, cameraX;
    private boolean left, right, jumpHeld, paused, gameOver, levelClear;
    private boolean wasGrounded, jumpRequested;
    private int lives = 3, score, coinCount;
    private long lastNanos;
    private float levelTime = 300f;

    public GameView(Context context) {
        super(context);
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        setFocusable(true);
        buildLevel();
    }

    private void buildLevel() {
        solids.clear(); coins.clear(); enemies.clear(); powerUps.clear();

        addSolid(0, 450, 1150, 90);
        addSolid(1300, 450, 850, 90);
        addSolid(2300, 450, 1050, 90);
        addSolid(3500, 450, 900, 90);
        addSolid(4550, 450, 1050, 90);
        addSolid(5750, 450, 1500, 90);

        addSolid(360, 350, 190, 30); addSolid(720, 295, 180, 30);
        addSolid(1420, 350, 210, 30); addSolid(1720, 285, 190, 30);
        addSolid(2470, 350, 190, 30); addSolid(2800, 285, 210, 30);
        addSolid(3700, 320, 190, 30); addSolid(4050, 260, 190, 30);
        addSolid(4800, 350, 190, 30); addSolid(5200, 290, 220, 30);
        addSolid(6100, 350, 190, 30); addSolid(6500, 290, 210, 30);

        float[][] c = {
            {410,315},{465,315},{770,260},{825,260},{1450,315},{1510,315},
            {1750,250},{1810,250},{2500,315},{2560,315},{2840,250},{2900,250},
            {3730,285},{3790,285},{4080,225},{4140,225},{4830,315},{4890,315},
            {5240,255},{5300,255},{6130,315},{6190,315},{6530,255},{6590,255},
            {6850,405}
        };
        for (float[] q : c) coins.add(new Coin(q[0], q[1]));

        addEnemy(600, 418, 500, 1050);
        addEnemy(1450, 318, 1370, 1630);
        addEnemy(1900, 418, 1750, 2100);
        addEnemy(2500, 318, 2350, 3250);
        addEnemy(2920, 253, 2800, 3000);
        addEnemy(3650, 418, 3520, 4350);
        addEnemy(4700, 418, 4580, 5550);
        addEnemy(6100, 318, 5950, 6300);
        addEnemy(6650, 418, 6500, 7000);

        powerUps.add(new PowerUp(800, 255));
    }

    private void addSolid(float x, float y, float w, float h) {
        solids.add(new RectF(x, y, x + w, y + h));
    }

    private void addEnemy(float x, float y, float min, float max) {
        enemies.add(new Enemy(x, y, min, max));
    }

    public boolean isFinished() { return gameOver || levelClear; }
    public boolean isPlaying() { return !paused && !isFinished(); }

    public void togglePause() { paused = !paused; lastNanos = System.nanoTime(); }
    public void setPaused(boolean value) { paused = value; lastNanos = System.nanoTime(); }

    public void restart() {
        lives = 3; score = 0; coinCount = 0; px = 90; py = 390;
        vx = vy = cameraX = 0; paused = false; gameOver = false; levelClear = false;
        levelTime = 300f; left = right = jumpHeld = false;
        buildLevel();
        lastNanos = System.nanoTime();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / W, getHeight() / H);
        float ox = (getWidth() - W * scale) / 2f;
        float oy = (getHeight() - H * scale) / 2f;
        canvas.save();
        canvas.translate(ox, oy);
        canvas.scale(scale, scale);

        drawWorld(canvas);
        drawHud(canvas);

        if (isPlaying()) {
            updateFrame();
        } else {
            drawOverlay(canvas);
        }
        canvas.restore();

        postInvalidateOnAnimation();
    }

    private void updateFrame() {
        long now = System.nanoTime();
        if (lastNanos == 0) lastNanos = now;
        float dt = Math.min(0.025f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        if (dt <= 0) return;

        levelTime -= dt;
        if (levelTime <= 0) { levelTime = 0; loseLife(); return; }

        float oldY = py;
        boolean grounded = isGrounded();

        if (left && !right) {
            vx = moveToward(vx, -RUN, 1500f * dt);
        } else if (right && !left) {
            vx = moveToward(vx, RUN, 1500f * dt);
        } else {
            vx = moveToward(vx, 0, 1900f * dt);
        }

        if (jumpRequested && grounded) { vy = -JUMP; jumpRequested = false; }
        wasGrounded = grounded;

        vy += GRAVITY * dt;
        moveAndCollide(dt, oldY);
        updateEnemies(dt);
        updateCoins();
        updatePowerUps();
        cameraX = clamp(px - 300, 0, 6750);

        if (py > 650) { loseLife(); return; }
        if (px >= 7050) levelClear = true;
    }

    private float moveToward(float value, float target, float amount) {
        if (value < target) return Math.min(target, value + amount);
        return Math.max(target, value - amount);
    }

    private void moveAndCollide(float dt, float oldY) {
        px += vx * dt;
        px = clamp(px, 0, 7120);

        float nextY = py + vy * dt;
        if (vy >= 0) {
            RectF next = playerRect(px, nextY);
            for (RectF r : solids) {
                if (next.right > r.left && next.left < r.right &&
                        playerRect(px, oldY).bottom <= r.top && next.bottom >= r.top) {
                    py = r.top - PH; vy = 0; return;
                }
            }
        } else {
            RectF next = playerRect(px, nextY);
            for (RectF r : solids) {
                if (next.right > r.left && next.left < r.right &&
                        playerRect(px, oldY).top >= r.bottom && next.top <= r.bottom) {
                    py = r.bottom; vy = 0; return;
                }
            }
        }
        py = nextY;
    }

    private boolean isGrounded() {
        RectF feet = new RectF(px + 4, py + PH, px + PW - 4, py + PH + 5);
        for (RectF r : solids) {
            if (feet.right > r.left && feet.left < r.right &&
                    feet.bottom >= r.top && feet.top <= r.top + 5) return true;
        }
        return false;
    }

    private void updateEnemies(float dt) {
        RectF player = playerRect(px, py);
        for (Enemy e : enemies) {
            if (!e.alive) continue;
            e.x += e.dir * 72f * dt;
            if (e.x <= e.min) { e.x = e.min; e.dir = 1; }
            if (e.x >= e.max) { e.x = e.max; e.dir = -1; }

            RectF er = new RectF(e.x, e.y, e.x + 40, e.y + 32);
            if (RectF.intersects(player, er)) {
                if (vy > 80 && player.bottom - er.top < 24) {
                    e.alive = false;
                    score += 100;
                    vy = -380;
                } else {
                    loseLife();
                    return;
                }
            }
        }
    }

    private void updateCoins() {
        RectF player = playerRect(px, py);
        for (Coin c : coins) {
            if (!c.taken && RectF.intersects(player, c.rect())) {
                c.taken = true; coinCount++; score += 50;
            }
        }
    }

    private void updatePowerUps() {
        RectF player = playerRect(px, py);
        Iterator<PowerUp> it = powerUps.iterator();
        while (it.hasNext()) {
            PowerUp u = it.next();
            if (!u.collected && RectF.intersects(player, u.rect())) {
                u.collected = true; score += 500; it.remove();
            }
        }
    }

    private void loseLife() {
        if (lives <= 1) { lives = 0; gameOver = true; return; }
        lives--;
        px = 90; py = 390; vx = vy = 0; cameraX = 0; levelTime = 300;
        for (Enemy e : enemies) e.alive = true;
        for (Coin c : coins) c.taken = false;
        powerUps.clear(); powerUps.add(new PowerUp(800, 255));
        lastNanos = System.nanoTime();
    }

    private RectF playerRect(float x, float y) {
        return new RectF(x + 4, y, x + PW - 4, y + PH);
    }

    private void drawWorld(Canvas c) {
        p.setColor(Color.rgb(112, 196, 250)); c.drawRect(0, 0, W, H, p);

        // Parallax hills and clouds.
        p.setColor(Color.rgb(75, 185, 105));
        for (int i = -2; i < 9; i++) {
            float x = i * 190 - (cameraX * .16f % 190);
            Path hill = new Path();
            hill.moveTo(x, 450); hill.lineTo(x + 95, 345); hill.lineTo(x + 190, 450);
            hill.close(); c.drawPath(hill, p);
        }
        p.setColor(Color.WHITE);
        for (int i = 0; i < 8; i++) {
            float x = i * 260 + 80 - (cameraX * .08f % 260);
            c.drawOval(x, 85 + (i % 3) * 35, x + 85, 125 + (i % 3) * 35, p);
            c.drawOval(x + 35, 65 + (i % 3) * 35, x + 120, 125 + (i % 3) * 35, p);
        }

        c.save();
        c.translate(-cameraX, 0);

        for (RectF r : solids) {
            p.setColor(r.top >= 440 ? Color.rgb(145, 91, 49) : Color.rgb(204, 136, 55));
            c.drawRect(r, p);
            p.setColor(r.top >= 440 ? Color.rgb(67, 160, 73) : Color.rgb(236, 177, 75));
            c.drawRect(r.left, r.top, r.right, r.top + 7, p);
        }

        for (Coin coin : coins) if (!coin.taken) {
            p.setColor(Color.rgb(255, 214, 40));
            c.drawOval(coin.rect(), p);
            p.setColor(Color.rgb(255, 238, 110));
            c.drawOval(coin.x - 3, coin.y - 10, coin.x + 2, coin.y + 2, p);
        }

        for (PowerUp u : powerUps) {
            p.setColor(Color.rgb(238, 70, 80));
            c.drawCircle(u.x + 14, u.y + 14, 14, p);
            p.setColor(Color.WHITE);
            c.drawCircle(u.x + 9, u.y + 10, 3, p);
            c.drawCircle(u.x + 19, u.y + 10, 3, p);
        }

        for (Enemy e : enemies) if (e.alive) drawEnemy(c, e);
        drawPlayer(c);

        // Finish flag.
        p.setColor(Color.DKGRAY); c.drawRect(7050, 175, 7058, 450, p);
        p.setColor(Color.rgb(240, 70, 75));
        Path flag = new Path(); flag.moveTo(7058, 180); flag.lineTo(7140, 205); flag.lineTo(7058, 230);
        flag.close(); c.drawPath(flag, p);
        c.restore();
    }

    private void drawPlayer(Canvas c) {
        p.setColor(Color.rgb(240, 145, 55)); c.drawRect(px + 7, py, px + 33, py + 16, p);
        p.setColor(Color.rgb(30, 100, 140)); c.drawRect(px + 5, py + 15, px + 35, py + 42, p);
        p.setColor(Color.rgb(248, 210, 165)); c.drawCircle(px + 20, py + 15, 11, p);
        p.setColor(Color.DKGRAY); c.drawRect(px + 3, py + 41, px + 17, py + PH, p);
        c.drawRect(px + 23, py + 41, px + 38, py + PH, p);
    }

    private void drawEnemy(Canvas c, Enemy e) {
        p.setColor(Color.rgb(135, 78, 48)); c.drawRoundRect(e.x, e.y, e.x + 40, e.y + 32, 9, 9, p);
        p.setColor(Color.WHITE); c.drawCircle(e.x + 11, e.y + 12, 5, p); c.drawCircle(e.x + 29, e.y + 12, 5, p);
        p.setColor(Color.DKGRAY); c.drawCircle(e.x + 11, e.y + 12, 2, p); c.drawCircle(e.x + 29, e.y + 12, 2, p);
    }

    private void drawHud(Canvas c) {
        p.setColor(Color.argb(160, 0, 0, 0)); c.drawRoundRect(14, 14, 610, 62, 12, 12, p);
        text.setColor(Color.WHITE); text.setTextSize(20);
        c.drawText(String.format("SCORE %06d   COINS %02d   LIVES %d   TIME %03d",
                score, coinCount, lives, (int) levelTime), 28, 45, text);

        // Touch controls.
        p.setColor(Color.argb(105, 0, 0, 0));
        c.drawCircle(72, H - 70, 48, p); c.drawCircle(178, H - 70, 48, p);
        c.drawCircle(W - 78, H - 72, 58, p);
        text.setColor(Color.WHITE); text.setTextSize(34);
        c.drawText("◀", 55, H - 58, text); c.drawText("▶", 160, H - 58, text);
        c.drawText("▲", W - 98, H - 59, text);
        c.drawText("Ⅱ", W - 160, 47, text);
    }

    private void drawOverlay(Canvas c) {
        p.setColor(Color.argb(205, 0, 0, 0)); c.drawRect(0, 0, W, H, p);
        text.setTextAlign(Paint.Align.CENTER); text.setColor(Color.WHITE); text.setTextSize(46);
        c.drawText(levelClear ? "LEVEL CLEAR!" : gameOver ? "GAME OVER" : "PAUSED", W / 2, H / 2 - 20, text);
        text.setTextSize(20); c.drawText(gameOver || levelClear ? "TAP TO PLAY AGAIN" : "TAP TO RESUME", W / 2, H / 2 + 30, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float scale = Math.min(getWidth() / W, getHeight() / H);
        float ox = (getWidth() - W * scale) / 2f, oy = (getHeight() - H * scale) / 2f;
        float x = (e.getX() - ox) / scale, y = (e.getY() - oy) / scale;

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            if (isFinished()) { restart(); return true; }
            if (x > W - 200 && x < W - 115 && y < 90) { togglePause(); return true; }
        }

        if (paused) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) togglePause();
            return true;
        }

        boolean down = e.getAction() == MotionEvent.ACTION_DOWN ||
                e.getAction() == MotionEvent.ACTION_MOVE;
        if (down) {
            left = x < 125 && y > H - 150;
            right = x >= 125 && x < 245 && y > H - 150;
            if (x > W - 160 && y > H - 165) { jumpHeld = true; jumpRequested = true; }
        } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
            left = right = jumpHeld = jumpRequested = false;
        }
        return true;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static class Coin {
        float x, y; boolean taken;
        Coin(float x, float y) { this.x = x; this.y = y; }
        RectF rect() { return new RectF(x - 9, y - 14, x + 9, y + 14); }
    }

    private static class Enemy {
        float x, y, min, max; int dir = 1; boolean alive = true;
        Enemy(float x, float y, float min, float max) { this.x=x; this.y=y; this.min=min; this.max=max; }
    }

    private static class PowerUp {
        float x, y; boolean collected;
        PowerUp(float x, float y) { this.x=x; this.y=y; }
        RectF rect() { return new RectF(x, y, x + 28, y + 28); }
    }
}
