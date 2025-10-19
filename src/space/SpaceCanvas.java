package space;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;

public class SpaceCanvas extends GameCanvas implements Runnable {
    private Thread loop;
    private boolean running = false, paused = false, gameOver = false;
    private int W, H;
    private int cols = 24;
    private int cell;
    private int groundY;
    private static final int STARS = 80;
    private int[] sx = new int[STARS];
    private int[] sy = new int[STARS];
    private int[] sl = new int[STARS];
    private static final int MAX_AST = 16;
    private boolean[] aLive = new boolean[MAX_AST];
    private int[] ax = new int[MAX_AST], ay = new int[MAX_AST], ar = new int[MAX_AST], avy = new int[MAX_AST];
    private Sprite[] aSprite = new Sprite[MAX_AST];
    private int[] aAnimTick = new int[MAX_AST];
    private int[] aAnimDelay = new int[MAX_AST];
    private static final int MAX_BUL = 10;
    private boolean[] bLive = new boolean[MAX_BUL];
    private int[] bx = new int[MAX_BUL], by = new int[MAX_BUL], bvy = new int[MAX_BUL];
    private int shootCooldown = 0;
    private int shipX;
    private int shipY;
    private int shipW, shipH;
    private int shipSpeed;
    private Sprite ship;
    private int shipAnimTick = 0;
    private int shipAnimDelay = 5;
    private int score = 0;
    private int tickDelay = 40;
    private int spawnCounter = 0;
    private Image shipSheet, astS, astM, astL;
    private static final int SHIP_W = 31, SHIP_H = 48, SHIP_FRAMES = 3;
    private static final int ASTS_W = 16, ASTS_H = 16, ASTS_FRAMES = 12;
    private static final int ASTM_W = 24, ASTM_H = 24, ASTM_FRAMES = 12;
    private static final int ASTL_W = 32, ASTL_H = 32, ASTL_FRAMES = 12;
    private int seed = 123456789;

    private int rnd() { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed; }

    private int rndRange(int n) {
        if (n <= 0) return 0;
        int r = rnd() % n;
        return (r < 0) ? -r : r;
    }

    public SpaceCanvas() {
        super(true);
        W = getWidth();
        H = getHeight();

        cell = Math.max(4, W / cols);
        groundY = H - (cell * 2);

        loadSprites();

        shipW = SHIP_W;
        shipH = SHIP_H;

        shipX = W / 2;
        shipY = groundY - shipH/2 - 2;
        shipSpeed = Math.max(2, cell / 3);

        if (ship != null) ship.setRefPixelPosition(shipX, shipY);

        initStars();
    }

    private void loadSprites() {
        try {
            shipSheet = Image.createImage("/img/ship48_sheet.png");
            ship = new Sprite(shipSheet, SHIP_W, SHIP_H);
            ship.defineReferencePixel(SHIP_W/2, SHIP_H/2);
            ship.setFrameSequence(new int[]{0,1,2,1});
            ship.setFrame(0);
            astS = Image.createImage("/img/asteroid_small16_sheet.png");
            astM = Image.createImage("/img/asteroid_med24_tight_sheet.png");
            astL = Image.createImage("/img/asteroid_big32_tight_sheet.png");
        } catch (Exception e) {
            System.err.println("Error loading Sprites");
        }
    }

    private void initStars() {
        for (int i = 0; i < STARS; i++) {
            sx[i] = rndRange(W);
            sy[i] = rndRange(H);
            sl[i] = 1 + (rndRange(3));
        }
    }

    public void start() {
        if (loop == null) {
            running = true; paused = false;
            loop = new Thread(this);
            loop.start();
        } else paused = false;
    }
    public void pause() { paused = true; }
    public void stop()  { running = false; loop = null; }

    public void run() {
        while (running) {
            long t0 = System.currentTimeMillis();
            if (!paused) {
                input();
                if (!gameOver) {
                    updateStars();
                    updateBullets();
                    spawnAsteroids();
                    updateAsteroids();
                    checkCollisions();
                }
                draw();
            }
            int sleep = tickDelay - (int)(System.currentTimeMillis() - t0);
            if (sleep < 5) sleep = 5;
            try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
        }
    }

    private void input() {
        int ks = getKeyStates();
        if (gameOver) {
            if ((ks & FIRE_PRESSED) != 0) resetGame();
            return;
        }
        if ((ks & LEFT_PRESSED) != 0)  shipX -= shipSpeed;
        if ((ks & RIGHT_PRESSED) != 0) shipX += shipSpeed;
        if (shipX < shipW/2) shipX = shipW/2;
        if (shipX > W - shipW/2) shipX = W - shipW/2;
        if (ship != null) ship.setRefPixelPosition(shipX, shipY);

        if (shootCooldown > 0) shootCooldown--;
        if ((ks & FIRE_PRESSED) != 0 && shootCooldown == 0 && !gameOver) {
            fireBullet();
            shootCooldown = 6;
        }

        if (gameOver && (ks & FIRE_PRESSED) != 0) resetGame();
    }

    private void fireBullet() {
        for (int i = 0; i < MAX_BUL; i++) if (!bLive[i]) {
            bLive[i] = true;
            bx[i] = shipX;
            by[i] = shipY - shipH/2 - 2;
            bvy[i] = - (cell);
            return;
        }
    }

    private void resetGame() {
        for (int i = 0; i < MAX_AST; i++) { aLive[i] = false; aSprite[i] = null; }
        for (int i = 0; i < MAX_BUL; i++) bLive[i] = false;
        score = 0; gameOver = false;
        spawnCounter = 0; shootCooldown = 0;
        shipAnimTick = 0;
        if (ship != null) ship.setFrame(0);
    }

    private void updateStars() {
        for (int i = 0; i < STARS; i++) {
            sy[i] += sl[i];
            if (sy[i] >= H) {
                sy[i] = -rndRange(20);
                sx[i] = rndRange(W);
                sl[i] = 1 + rndRange(3);
            }
        }
    }

    private void updateBullets() {
        for (int i = 0; i < MAX_BUL; i++) if (bLive[i]) {
            by[i] += bvy[i];
            if (by[i] < -6) bLive[i] = false;
        }
    }

    private void spawnAsteroids() {
        if (++spawnCounter < 6) return;
        spawnCounter = 0;

        if ((rnd() & 1) == 0) return;

        int slot = -1;
        for (int i = 0; i < MAX_AST; i++) if (!aLive[i]) { slot = i; break; }
        if (slot < 0) return;

        aLive[slot] = true;

        int kind = rnd() % 3; if (kind < 0) kind = -kind;
        Image sheet; int fw, fh, frames;
        if (kind == 0) { sheet = astS; fw = ASTS_W; fh = ASTS_H; frames = ASTS_FRAMES; }
        else if (kind == 1) { sheet = astM; fw = ASTM_W; fh = ASTM_H; frames = ASTM_FRAMES; }
        else { sheet = astL; fw = ASTL_W; fh = ASTL_H; frames = ASTL_FRAMES; }

        try {
            aSprite[slot] = new Sprite(sheet, fw, fh);
            aSprite[slot].defineReferencePixel(fw/2, fh/2);
            aSprite[slot].setFrame(rndRange(frames));
        } catch (Exception e) {
            aSprite[slot] = null;
        }

        int span = W - 2*cell;
        if (span < 1) span = 1;
        ax[slot] = cell + rndRange(span);
        ay[slot] = - (5 + rndRange(60));
        ar[slot] = fw/2;
        avy[slot] = Math.max(1, slownessByRadius(ar[slot]));

        aAnimDelay[slot] = 2 + rndRange(4);
        aAnimTick[slot]  = 0;
    }

    private int slownessByRadius(int r) {
        int cellSafe = Math.max(1, cell);
        int ratio = r / cellSafe;
        if (ratio < 1) ratio = 1;
        int v = (cellSafe * 3) / ratio;
        if (v < 1) v = 1;
        if (v > cellSafe) v = cellSafe;
        return v;
    }

    private void updateAsteroids() {
        for (int i = 0; i < MAX_AST; i++) if (aLive[i]) {
            ay[i] += avy[i];
            if (aSprite[i] != null) {
                aSprite[i].setRefPixelPosition(ax[i], ay[i]);
                if (++aAnimTick[i] >= aAnimDelay[i]) {
                    aAnimTick[i] = 0;
                    int next = aSprite[i].getFrame() + 1;
                    if (next >= aSprite[i].getFrameSequenceLength()) next = 0;
                    aSprite[i].setFrame(next);
                }
            }
            if (ay[i] - ar[i] > H) aLive[i] = false;
        }

        if (ship != null && !gameOver) {
            if (++shipAnimTick >= shipAnimDelay) {
                shipAnimTick = 0;
                ship.nextFrame();
            }
        }
    }

    private void checkCollisions() {
        // пуля ↔ астероид
        for (int b = 0; b < MAX_BUL; b++) if (bLive[b]) {
            int px = bx[b], py = by[b];
            for (int a = 0; a < MAX_AST; a++) if (aLive[a]) {
                int dx = px - ax[a];
                int dy = py - ay[a];
                if (dx*dx + dy*dy <= ar[a]*aR(a)) {
                    bLive[b] = false;
                    aLive[a] = false;
                    aSprite[a] = null;
                    score += 10;
                    break;
                }
            }
        }
        int rx = shipX - shipW/2, ry = shipY - shipH/2, rw = shipW, rh = shipH;
        for (int a = 0; a < MAX_AST; a++) if (aLive[a]) {
            if (circleIntersectsRect(ax[a], ay[a], ar[a], rx, ry, rw, rh)) {
                gameOver = true;
                return;
            }
        }
    }

    private int aR(int a) { return ar[a]; }

    private boolean circleIntersectsRect(int cx, int cy, int r, int rx, int ry, int rw, int rh) {
        int nx = (cx < rx) ? rx : (Math.min(cx, rx + rw));
        int ny = (cy < ry) ? ry : (Math.min(cy, ry + rh));
        int dx = cx - nx, dy = cy - ny;
        return dx*dx + dy*dy <= r*r;
    }

    private void draw() {
        Graphics g = getGraphics();
        g.setColor(0x000000); g.fillRect(0, 0, W, H);
        for (int i = 0; i < STARS; i++) {
            int c = (sl[i] == 1) ? 0x666666 : (sl[i] == 2 ? 0xAAAAAA : 0xFFFFFF);
            g.setColor(c);
            g.fillRect(sx[i], sy[i], 1, 1);
        }
        g.setColor(0x444444);
        g.drawRect(0, 0, W-1, H-1);
        for (int i = 0; i < MAX_AST; i++) if (aLive[i]) {
            if (aSprite[i] != null) {
                aSprite[i].paint(g);
            } else {
                g.setColor(0xBBBBBB);
                int r = ar[i];
                g.fillArc(ax[i]-r, ay[i]-r, r*2, r*2, 0, 360);
            }
        }
        g.setColor(0xFFFF66);
        for (int i = 0; i < MAX_BUL; i++) if (bLive[i]) {
            g.fillRect(bx[i]-1, by[i]-4, 2, 6);
        }
        if (ship != null) {
            ship.paint(g);
        } else {
            g.setColor(0x66FF66);
            g.fillRect(shipX - shipW/2, shipY - shipH/2, shipW, shipH);
            g.setColor(0x99FF99);
            g.fillRect(shipX - (shipW/4), shipY - shipH, shipW/2, shipH/2);
        }
        g.setColor(0xFFFFFF);
        g.drawString("Score: " + score, 2, 2, Graphics.TOP | Graphics.LEFT);

        if (gameOver) {
            String a = "GAME OVER";
            String b = "FIRE: restart";
            int lh = g.getFont().getHeight();
            int w1 = g.getFont().stringWidth(a);
            int w2 = g.getFont().stringWidth(b);
            g.drawString(a, W/2 - w1/2, H/2 - lh, Graphics.TOP | Graphics.LEFT);
            g.drawString(b, W/2 - w2/2, H/2 + 2, Graphics.TOP | Graphics.LEFT);
        }

        flushGraphics();
    }
}
