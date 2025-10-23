package space;

import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;

/**
 * Space Asteroids — J2ME GameCanvas
 * - Астероиды 16/24/32 (12 кадров вращения), замедленные
 * - Взрывы астероидов (10 кадров) + распад (L→2×M, M→2×S)
 * - Корабль 31×48 с анимацией (0-1-2-1)
 * - HP-полоса, i-frames; при 0 HP — взрыв корабля, затем Game Over
 * - Падающие «звёзды» (16×16, 11 кадров): подбор лечит игрока
 */
public class SpaceCanvas extends GameCanvas implements Runnable {

    // ---------- цикл ----------
    private Thread loop;
    private boolean running = false, paused = false, gameOver = false;

    // ---------- экран ----------
    private final int W, H;
    private final int cols = 24;
    private final int cell;
    private final int groundY;

    // ---------- фоновые звёзды ----------
    private static final int STARS = 80;
    private final int[] sx = new int[STARS];
    private final int[] sy = new int[STARS];
    private final int[] sl = new int[STARS]; // 1..3

    // ---------- астероиды ----------
    private static final int MAX_AST = 16;
    private final boolean[] aLive = new boolean[MAX_AST];
    private final int[] ax = new int[MAX_AST];
    private final int[] ay = new int[MAX_AST];
    private final int[] ar = new int[MAX_AST];    // радиус для коллизий
    private final int[] avx = new int[MAX_AST];   // горизонтальная скорость
    private final int[] avy = new int[MAX_AST];   // вертикальная скорость
    private final int[] aKind = new int[MAX_AST]; // 0=S(16),1=M(24),2=L(32)
    private final Sprite[] aSprite = new Sprite[MAX_AST];
    private final int[] aAnimTick = new int[MAX_AST];
    private final int[] aAnimDelay = new int[MAX_AST];

    // ---------- взрывы астероидов ----------
    private Image expS, expM, expL;          // 10 кадров: 16/24/32
    private final Sprite[] eSprite = new Sprite[MAX_AST];
    private final int[] eTick = new int[MAX_AST];
    private final int[] eDelay = new int[MAX_AST];
    private final int eFrames = 10;

    // ---------- powerups: звёзды-хилки ----------
    private static final int MAX_PWR = 4;
    private final boolean[] pLive = new boolean[MAX_PWR];
    private final int[] px = new int[MAX_PWR], py = new int[MAX_PWR], pvy = new int[MAX_PWR], pR = new int[MAX_PWR];
    private final Sprite[] pSprite = new Sprite[MAX_PWR];
    private final int[] pTick = new int[MAX_PWR], pDelay = new int[MAX_PWR];
    private Image starSheet;
    private static final int STAR_W = 16, STAR_H = 16, STAR_FRAMES = 11;

    // ---------- пули ----------
    private static final int MAX_BUL = 10;
    private final boolean[] bLive = new boolean[MAX_BUL];
    private final int[] bx = new int[MAX_BUL];
    private final int[] by = new int[MAX_BUL];
    private final int[] bvy = new int[MAX_BUL];
    private int shootCooldown = 0;

    // ---------- корабль ----------
    private int shipX, shipY;
    private final int shipW = 31, shipH = 48;
    private final int shipSpeed;
    private Sprite ship;
    private int shipAnimTick = 0;
    private final int shipAnimDelay = 5;

    // взрыв корабля
    private boolean shipExploding = false;
    private Sprite shipExp = null;       // используем exp32_sheet
    private int shipExpTick = 0;
    private int shipExpDelay = 2;        // кадр раз в 2 тика
    private final int shipExpFrames = 10;

    // ---------- HP / урон ----------
    private final int hpMax = 8;
    private int hp    = hpMax;
    private int invTicks = 0;       // i-frames
    private final int invDuration = 25;   // ~1 сек при 40мс/тик

    // ---------- игра ----------
    private int score = 0;
    private final int tickDelay = 40;  // ~25 FPS
    private int spawnCounter = 0;

    // ---------- ресурсы ----------
    private Image astS, astM, astL;

    // ---------- LCG ----------
    private int seed = 123456789;
    private int rnd() { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed; }
    private int rndRange(int n) { if (n <= 0) return 0; int r = rnd() % n; return (r < 0) ? -r : r; }

    public SpaceCanvas() {
        super(true); // getKeyStates()
        W = this.getWidth();
        H = this.getHeight();

        cell = Math.max(4, W / cols);
        groundY = H - cell * 2;

        loadSprites();

        shipX = W / 2;
        shipY = groundY - shipH/2 - 2;
        shipSpeed = Math.max(2, cell / 3);
        if (ship != null) ship.setRefPixelPosition(shipX, shipY);

        initStars();
    }

    private void loadSprites() {
        try {
            // корабль (31×48), 3 кадра
            Image shipSheet = Image.createImage("/img/ship48_sheet.png");
            ship = new Sprite(shipSheet, shipW, shipH);
            ship.defineReferencePixel(shipW/2, shipH/2);
            ship.setFrameSequence(new int[]{0,1,2,1});
            ship.setFrame(0);

            // астероиды tight 12 кадров
            astS = Image.createImage("/img/asteroid_small16_sheet.png");        // 16×16×12
            astM = Image.createImage("/img/asteroid_med24_tight_sheet.png");    // 24×24×12
            astL = Image.createImage("/img/asteroid_big32_tight_sheet.png");    // 32×32×12

            // взрывы
            expS = Image.createImage("/img/exp16_sheet.png");
            expM = Image.createImage("/img/exp24_sheet.png");
            expL = Image.createImage("/img/exp32_sheet.png");

            // звезда-хилка
            starSheet = Image.createImage("/img/stars_sprite_16x16.png"); // 11 кадров
        } catch (Exception e) {
            System.err.println("Error loading sprites");
            ship = null;
            astS = astM = astL = null;
            expS = expM = expL = null;
            starSheet = null;
        }
    }

    private void initStars() {
        for (int i = 0; i < STARS; i++) {
            sx[i] = rndRange(W);
            sy[i] = rndRange(H);
            sl[i] = 1 + rndRange(3);
        }
    }

    // ---------- цикл ----------
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
                    updatePowerups();
                    checkCollisions();
                }
                draw();
            }
            int sleep = tickDelay - (int)(System.currentTimeMillis() - t0);
            if (sleep < 5) sleep = 5;
            try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
        }
    }

    // ---------- ввод ----------
    private void input() {
        int ks = getKeyStates();

        if (shipExploding) return; // ждём окончания взрыва

        if (gameOver) {
            if ((ks & FIRE_PRESSED) != 0) resetGame();
            return;
        }

        if ((ks & LEFT_PRESSED)  != 0) shipX -= shipSpeed;
        if ((ks & RIGHT_PRESSED) != 0) shipX += shipSpeed;

        if (shipX < shipW/2) shipX = shipW/2;
        if (shipX > W - shipW/2) shipX = W - shipW/2;
        if (ship != null) ship.setRefPixelPosition(shipX, shipY);

        if (shootCooldown > 0) shootCooldown--;
        if ((ks & FIRE_PRESSED) != 0 && shootCooldown == 0) {
            fireBullet();
            shootCooldown = 6;
        }
    }

    private void fireBullet() {
        for (int i = 0; i < MAX_BUL; i++) if (!bLive[i]) {
            bLive[i] = true;
            bx[i] = shipX;
            by[i] = shipY - shipH/2 - 2;
            bvy[i] = -cell;
            return;
        }
    }

    private void resetGame() {
        for (int i = 0; i < MAX_AST; i++) {
            aLive[i] = false; aSprite[i] = null;
            eSprite[i] = null;
        }
        for (int i = 0; i < MAX_BUL; i++) bLive[i] = false;
        for (int i = 0; i < MAX_PWR; i++) { pLive[i] = false; pSprite[i] = null; }

        // корабль
        shipExploding = false;
        shipExp = null;
        shipX = W / 2;
        shipY = groundY - shipH/2 - 2;
        if (ship != null) {
            ship.setFrame(0);
            ship.setRefPixelPosition(shipX, shipY);
        }

        score = 0; gameOver = false;
        spawnCounter = 0; shootCooldown = 0;
        shipAnimTick = 0;

        hp = hpMax;
        invTicks = 0;
    }

    // ---------- фоновые звёзды ----------
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

    // ---------- пули ----------
    private void updateBullets() {
        for (int i = 0; i < MAX_BUL; i++) if (bLive[i]) {
            by[i] += bvy[i];
            if (by[i] < -6) bLive[i] = false;
        }
    }

    // ---------- астероиды ----------
    private void spawnAsteroids() {
        if (++spawnCounter < 6) return; // частота спавна
        spawnCounter = 0;
        if ((rnd() & 1) == 0) return;   // шанс 1/2

        int slot = -1;
        for (int i = 0; i < MAX_AST; i++) if (!aLive[i] && eSprite[i] == null) { slot = i; break; }
        if (slot < 0) return;

        aLive[slot] = true;

        int kind = rnd() % 3; if (kind < 0) kind = -kind;
        aKind[slot] = kind;

        Image sheet; int fw, fh, frames = 12;
        if (kind == 0) { sheet = astS; fw = 16; fh = 16; }
        else if (kind == 1) { sheet = astM; fw = 24; fh = 24; }
        else { sheet = astL; fw = 32; fh = 32; }

        try {
            aSprite[slot] = new Sprite(sheet, fw, fh);
            aSprite[slot].defineReferencePixel(fw/2, fh/2);
            aSprite[slot].setFrame(rndRange(frames));
        } catch (Exception e) {
            aSprite[slot] = null;
        }

        int span = W - 2*cell; if (span < 1) span = 1;
        ax[slot] = cell + rndRange(span);
        ay[slot] = -(5 + rndRange(60));
        ar[slot] = fw/2;

        // вертикальная скорость помедленнее (~−33%)
        avy[slot] = Math.max(1, (slownessByRadius(ar[slot]) * 2) / 3);

        // лёгкий горизонтальный дрейф
        int drift = rndRange(Math.max(1, cell/3 + 1)) - (cell/6);
        avx[slot] = drift;

        // вращение медленнее
        aAnimDelay[slot] = 4 + rndRange(4); // 4..7 тиков/кадр
        aAnimTick[slot]  = 0;
    }

    private int slownessByRadius(int r) {
        int cellSafe = Math.max(1, cell);
        int ratio = r / cellSafe; if (ratio < 1) ratio = 1;
        int v = (cellSafe * 2) / ratio;  // чуть медленнее базово (2 вместо 3)
        if (v < 1) v = 1;
        if (v > cellSafe) v = cellSafe;
        return v;
    }

    private void updateAsteroids() {
        // движение и анимация астероидов
        for (int i = 0; i < MAX_AST; i++) if (aLive[i]) {
            ay[i] += avy[i];
            ax[i] += avx[i];
            if (ax[i] < ar[i]) { ax[i] = ar[i]; avx[i] = -avx[i]; }
            if (ax[i] > W - ar[i]) { ax[i] = W - ar[i]; avx[i] = -avx[i]; }

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

        // тики спрайтов взрыва астероидов
        for (int i = 0; i < MAX_AST; i++) if (eSprite[i] != null) {
            if (++eTick[i] >= eDelay[i]) {
                eTick[i] = 0;
                int nf = eSprite[i].getFrame() + 1;
                if (nf >= eFrames) {
                    eSprite[i] = null; // доиграл
                } else {
                    eSprite[i].setFrame(nf);
                    eSprite[i].setRefPixelPosition(ax[i], ay[i]);
                }
            }
        }

        // анимация корабля (если не взрывается)
        if (!shipExploding && ship != null && !gameOver) {
            if (++shipAnimTick >= shipAnimDelay) {
                shipAnimTick = 0;
                ship.nextFrame();
            }
        }

        // тики взрыва корабля
        if (shipExploding && shipExp != null) {
            if (++shipExpTick >= shipExpDelay) {
                shipExpTick = 0;
                int nf = shipExp.getFrame() + 1;
                if (nf >= shipExpFrames) {
                    shipExploding = false;
                    shipExp = null;
                    gameOver = true;
                } else {
                    shipExp.setFrame(nf);
                    shipExp.setRefPixelPosition(shipX, shipY);
                }
            }
        }

        // тик неуязвимости
        if (invTicks > 0) invTicks--;
    }

    // запуск взрыва астероида + шанс дропа звезды
    private void explodeAsteroid(int idx) {
        Image esheet; int fw, fh;
        if (aKind[idx] == 2)      { esheet = expL; fw = 32; fh = 32; }
        else if (aKind[idx] == 1) { esheet = expM; fw = 24; fh = 24; }
        else                      { esheet = expS; fw = 16; fh = 16; }

        try {
            eSprite[idx] = new Sprite(esheet, fw, fh);
            eSprite[idx].defineReferencePixel(fw/2, fh/2);
            eSprite[idx].setRefPixelPosition(ax[idx], ay[idx]);
            eSprite[idx].setFrame(0);
            eTick[idx] = 0;
            eDelay[idx] = 2; // взрыв медленнее
        } catch (Exception ignored) { eSprite[idx] = null; }

        // отключаем сам астероид
        aLive[idx] = false;
        aSprite[idx] = null;

        // распад
        int k = aKind[idx];
        if (k == 2) { // large -> 2 medium
            spawnChildAsteroid(ax[idx] - ar[idx]/2, ay[idx], 1, -Math.max(1, cell/4));
            spawnChildAsteroid(ax[idx] + ar[idx]/2, ay[idx], 1,  Math.max(1, cell/4));
        } else if (k == 1) { // medium -> 2 small
            spawnChildAsteroid(ax[idx] - ar[idx]/3, ay[idx], 0, -Math.max(1, cell/5));
            spawnChildAsteroid(ax[idx] + ar[idx]/3, ay[idx], 0,  Math.max(1, cell/5));
        }

        // шанс 1/5 уронить «звезду»-хилку
        if ((rnd() % 5) == 0) dropStarAt(ax[idx], ay[idx]);
    }

    // взрыв корабля
    private void explodeShip() {
        shipExploding = true;
        invTicks = 0;
        try {
            shipExp = new Sprite(expL, 32, 32);
            shipExp.defineReferencePixel(16, 16);
            shipExp.setRefPixelPosition(shipX, shipY);
            shipExp.setFrame(0);
            shipExpTick = 0;
            shipExpDelay = 2;
        } catch (Exception ignored) {
            shipExp = null;
            shipExploding = false;
            gameOver = true;
        }
    }

    private void spawnChildAsteroid(int x, int y, int kind, int vx) {
        int slot = -1;
        for (int i = 0; i < MAX_AST; i++) if (!aLive[i] && eSprite[i] == null) { slot = i; break; }
        if (slot < 0) return;

        Image sheet; int fw, fh, frames = 12;
        if (kind == 0) { sheet = astS; fw = 16; fh = 16; }
        else if (kind == 1) { sheet = astM; fw = 24; fh = 24; }
        else { sheet = astL; fw = 32; fh = 32; }

        try {
            aSprite[slot] = new Sprite(sheet, fw, fh);
            aSprite[slot].defineReferencePixel(fw/2, fh/2);
            aSprite[slot].setFrame(rndRange(frames));
        } catch (Exception e) { aSprite[slot] = null; }

        aKind[slot] = kind;
        aLive[slot] = true;
        ax[slot] = x;
        ay[slot] = y;
        ar[slot] = fw/2;
        avx[slot] = vx;
        avy[slot] = Math.max(1, slownessByRadius(ar[slot]));
        aAnimDelay[slot] = 4 + rndRange(4);
        aAnimTick[slot]  = 0;
    }

    // ---------- powerups ----------
    private void dropStarAt(int x, int y) {
        int slot = -1;
        for (int i = 0; i < MAX_PWR; i++) if (!pLive[i]) { slot = i; break; }
        if (slot < 0 || starSheet == null) return;

        try {
            pSprite[slot] = new Sprite(starSheet, STAR_W, STAR_H);
            pSprite[slot].defineReferencePixel(STAR_W/2, STAR_H/2);
            pSprite[slot].setFrame(rndRange(STAR_FRAMES));
            pSprite[slot].setRefPixelPosition(x, y);
        } catch (Exception e) { pSprite[slot] = null; }

        pLive[slot] = true;
        px[slot] = x;  py[slot] = y;
        pR[slot] = 7;                          // радиус подбора
        pvy[slot] = Math.max(1, cell/3);       // медленно падает
        pDelay[slot] = 5 + rndRange(3);        // 5..7 тиков/кадр
        pTick[slot] = 0;
    }

    private void updatePowerups() {
        for (int i = 0; i < MAX_PWR; i++) if (pLive[i]) {
            py[i] += pvy[i];
            if (pSprite[i] != null) {
                pSprite[i].setRefPixelPosition(px[i], py[i]);
                if (++pTick[i] >= pDelay[i]) {
                    pTick[i] = 0;
                    int nf = pSprite[i].getFrame() + 1;
                    if (nf >= STAR_FRAMES) nf = 0;
                    pSprite[i].setFrame(nf);
                }
            }
            if (py[i] - pR[i] > H) { pLive[i] = false; pSprite[i] = null; }
        }
    }

    private int damageByKind(int kind) {
        switch (kind) {
            case 0: return 1; // small
            case 1: return 2; // medium
            default: return 3; // large
        }
    }

    // ---------- коллизии ----------
    private void checkCollisions() {
        if (shipExploding) return;

        // пули ↔ астероиды
        for (int b = 0; b < MAX_BUL; b++) if (bLive[b]) {
            int px0 = bx[b], py0 = by[b];
            for (int a = 0; a < MAX_AST; a++) if (aLive[a]) {
                int dx = px0 - ax[a];
                int dy = py0 - ay[a];
                if (dx*dx + dy*dy <= ar[a]*aR(a)) {
                    bLive[b] = false;
                    score += 10;
                    explodeAsteroid(a);
                    break;
                }
            }
        }

        // прямоугольник корабля
        int rx = shipX - shipW/2, ry = shipY - shipH/2;

        // подбор звезды → лечим
        for (int i = 0; i < MAX_PWR; i++) if (pLive[i]) {
            if (circleIntersectsRect(px[i], py[i], pR[i], rx, ry, shipW, shipH)) {
                hp = Math.min(hpMax, hp + 2);   // +2 HP
                pLive[i] = false; pSprite[i] = null;
            }
        }

        // астероид ↔ корабль
        for (int a = 0; a < MAX_AST; a++) if (aLive[a]) {
            if (circleIntersectsRect(ax[a], ay[a], ar[a], rx, ry, shipW, shipH)) {

                if (invTicks == 0) {
                    hp -= damageByKind(aKind[a]);
                    if (hp <= 0) { explodeShip(); return; }
                    invTicks = invDuration;
                }

                // астероид взрываем/раскалываем
                explodeAsteroid(a);
            }
        }
    }

    private int aR(int a) { return ar[a]; }

    private boolean circleIntersectsRect(int cx, int cy, int r, int rx, int ry, int rw, int rh) {
        int nx = (cx < rx) ? rx : (cx > rx+rw ? rx+rw : cx);
        int ny = (cy < ry) ? ry : (cy > ry+rh ? ry+rh : cy);
        int dx = cx - nx, dy = cy - ny;
        return dx*dx + dy*dy <= r*r;
    }

    // ---------- отрисовка ----------
    private void draw() {
        Graphics g = getGraphics();

        // фон
        g.setColor(0x000000); g.fillRect(0, 0, W, H);

        // звёзды фона
        for (int i = 0; i < STARS; i++) {
            int c = (sl[i] == 1) ? 0x666666 : (sl[i] == 2 ? 0xAAAAAA : 0xFFFFFF);
            g.setColor(c); g.fillRect(sx[i], sy[i], 1, 1);
        }

        // рамка
        g.setColor(0x444444); g.drawRect(0, 0, W-1, H-1);

        // астероиды
        for (int i = 0; i < MAX_AST; i++) if (aLive[i]) {
            if (aSprite[i] != null) aSprite[i].paint(g);
            else {
                g.setColor(0xBBBBBB);
                int r = ar[i]; g.fillArc(ax[i]-r, ay[i]-r, r*2, r*2, 0, 360);
            }
        }

        // взрывы астероидов
        for (int i = 0; i < MAX_AST; i++) if (eSprite[i] != null) eSprite[i].paint(g);

        // powerups (звезды)
        for (int i = 0; i < MAX_PWR; i++) if (pLive[i] && pSprite[i] != null) pSprite[i].paint(g);

        // пули
        g.setColor(0xFFFF66);
        for (int i = 0; i < MAX_BUL; i++) if (bLive[i]) g.fillRect(bx[i]-1, by[i]-4, 2, 6);

        // корабль / взрыв корабля
        if (shipExploding) {
            if (shipExp != null) shipExp.paint(g);
        } else {
            if (ship != null) {
                if ((invTicks & 1) == 0) ship.paint(g); // мигаем при i-frames
            } else {
                g.setColor(0x66FF66);
                g.fillRect(shipX - shipW/2, shipY - shipH/2, shipW, shipH);
            }
        }

        // HUD: счёт
        g.setColor(0xFFFFFF);
        g.drawString("Score: " + score, 2, 2, Graphics.TOP | Graphics.LEFT);

        // HUD: полоска HP
        int y0 = 2 + g.getFont().getHeight() + 2;
        int barW = Math.min(W - 8, 84);
        int barH = 6;
        int x0 = 2;
        g.setColor(0x222222); g.fillRect(x0, y0, barW, barH);
        g.setColor(0x555555); g.drawRect(x0, y0, barW, barH);
        int fillW = (hp * barW) / Math.max(1, hpMax);
        int col = (hp * 3 <= hpMax) ? 0xFF4444 : ((hp * 2 <= hpMax) ? 0xFFAA00 : 0x33CC33);
        g.setColor(col);
        if (fillW > 2) g.fillRect(x0+1, y0+1, fillW-2, barH-2);

        // Game Over (после взрыва корабля)
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
