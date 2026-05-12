import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class AIShooterGame extends JFrame {
    public AIShooterGame() {
        setTitle("AI Shooter - 60FPS 完全體 (含常駐排行榜)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        add(new GamePanel());
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AIShooterGame().setVisible(true));
    }
}

class GamePanel extends JPanel implements ActionListener, KeyListener {
    private final int TILE_SIZE = 30;
    private final int GRID_SIZE = 20;
    private javax.swing.Timer timer;
    private int tick = 0; 

    private enum State { MENU, PLAYING, PAUSED, GAMEOVER, LEADERBOARD }
    private State gameState = State.MENU;

    private Player player;
    private List<Enemy> enemies;
    private List<Bullet> bullets;
    private int[][] map; 
    private int score = 0;

    private final int MAX_HP = 100;
    private int playerHp = MAX_HP;

    private boolean[] keys = new boolean[256];
    private int shootCooldown = 0;

    private List<ScoreRecord> highScores = new ArrayList<>();
    private final String SCORE_FILE = "highscores.txt";

    private final int MAX_ENEMIES = 40;
    private final int MAX_BULLETS = 150;

    private int scrollOffset = 0;
    private final int SCROLL_SPEED = 2; 

    private List<Star> stars;

    public GamePanel() {
        setPreferredSize(new Dimension(TILE_SIZE * GRID_SIZE, TILE_SIZE * GRID_SIZE));
        setBackground(Color.BLACK);
        setFocusable(true);
        requestFocusInWindow();
        addKeyListener(this);

        initGame();
        loadScores(); 
        
        timer = new javax.swing.Timer(16, this); // 60 FPS
        timer.start();
    }

    private void initGame() {
        map = new int[GRID_SIZE][GRID_SIZE];
        bullets = new ArrayList<>();
        enemies = new ArrayList<>();
        stars = new ArrayList<>();
        
        for (int i = 0; i < 60; i++) {
            stars.add(new Star(
                (int)(Math.random() * TILE_SIZE * GRID_SIZE),
                (int)(Math.random() * TILE_SIZE * GRID_SIZE),
                (int)(Math.random() * 3) + 1, 
                (int)(Math.random() * 3) + 1  
            ));
        }

        player = new Player(GRID_SIZE / 2, GRID_SIZE - 2);
        score = 0;
        playerHp = MAX_HP; 
        tick = 0;
        scrollOffset = 0;
        Arrays.fill(keys, false);
    }

    private void loadScores() {
        highScores.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(SCORE_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) highScores.add(new ScoreRecord(parts[0], Integer.parseInt(parts[1])));
            }
        } catch (Exception e) {}
        Collections.sort(highScores); 
    }

    private void saveScore(String name, int newScore) {
        loadScores();
        highScores.add(new ScoreRecord(name, newScore));
        Collections.sort(highScores);
        if (highScores.size() > 5) highScores = highScores.subList(0, 5);
        try (PrintWriter pw = new PrintWriter(new FileWriter(SCORE_FILE))) {
            for (ScoreRecord r : highScores) pw.println(r.name + "," + r.score);
        } catch (Exception e) {}
    }

    private void drawShadowText(Graphics g, String text, int x, int y, Color textColor) {
        g.setColor(Color.DARK_GRAY);
        g.drawString(text, x + 2, y + 2); 
        g.setColor(textColor);
        g.drawString(text, x, y);         
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // 星空背景
        for (Star s : stars) {
            if (s.speed == 3) g.setColor(Color.WHITE);
            else if (s.speed == 2) g.setColor(Color.LIGHT_GRAY);
            else g.setColor(Color.DARK_GRAY);
            g.fillOval(s.x, s.y, s.size, s.size);
        }

        if (gameState == State.MENU) {
            g.setFont(new Font("Impact", Font.ITALIC, 55));
            drawShadowText(g, "AI SHOOTER", 150, 150, Color.CYAN);
            g.setFont(new Font("Impact", Font.PLAIN, 40));
            drawShadowText(g, "60 FPS EDITION", 180, 210, Color.GREEN);
            g.setFont(new Font("Arial", Font.BOLD, 16));
            g.setColor(Color.LIGHT_GRAY);
            g.drawString("▶ WASD : Move Spaceship", 180, 290);
            g.drawString("▶ SPACE : Fire Missiles", 180, 330);
            g.drawString("▶ P / R : Pause / Restart", 180, 370);
            if (tick % 40 < 20) { 
                g.setFont(new Font("Arial", Font.BOLD, 24));
                g.setColor(Color.YELLOW);
                g.drawString("- PRESS SPACE TO START -", 150, 500);
            }
        } else if (gameState == State.LEADERBOARD) {
            // 這個是大畫面的排行榜 (按L鍵觸發)
            g.setFont(new Font("Impact", Font.BOLD, 50));
            drawShadowText(g, "TOP 5 PILOTS", 160, 130, Color.YELLOW);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            int startY = 220;
            if (highScores.isEmpty()) {
                g.setColor(Color.GRAY);
                g.drawString("Currently no records.", 190, startY);
            } else {
                for (int i = 0; i < highScores.size(); i++) {
                    ScoreRecord r = highScores.get(i);
                    if (i == 0) g.setColor(new Color(255, 215, 0)); 
                    else if (i == 1) g.setColor(new Color(192, 192, 192)); 
                    else g.setColor(Color.WHITE);
                    g.drawString("RANK " + (i + 1) + " :   " + r.name + "   -   " + r.score, 140, startY + (i * 50));
                }
            }
        } else if (gameState == State.PLAYING || gameState == State.GAMEOVER || gameState == State.PAUSED) {
            
            // 畫隕石
            for (int y = 0; y < GRID_SIZE; y++) {
                for (int x = 0; x < GRID_SIZE; x++) {
                    if (map[y][x] == 1) {
                        int pixelY = y * TILE_SIZE + scrollOffset;
                        g.setColor(Color.DARK_GRAY);
                        g.fillRect(x * TILE_SIZE + 2, pixelY + 2, TILE_SIZE - 4, TILE_SIZE - 4);
                        g.setColor(Color.GRAY);
                        g.fillRect(x * TILE_SIZE + 4, pixelY + 4, TILE_SIZE - 8, TILE_SIZE - 8);
                    }
                }
            }

            for (Bullet b : bullets) {
                if (b.isEnemy) {
                    g.setColor(Color.MAGENTA); 
                    g.fillOval(b.x * TILE_SIZE + 8, b.y * TILE_SIZE + 8, 14, 14);
                } else {
                    g.setColor(Color.CYAN); 
                    g.fillOval(b.x * TILE_SIZE + 8, b.y * TILE_SIZE + 8, 14, 14);
                }
            }

            for (Enemy e : enemies) {
                int ex = e.x * TILE_SIZE;
                int ey = e.y * TILE_SIZE;
                g.setColor(new Color(220, 20, 60)); 
                g.fillOval(ex + 2, ey + 12, 26, 12);
                g.setColor(new Color(255, 182, 193)); 
                g.fillOval(ex + 8, ey + 6, 14, 12);
                if (tick % 8 < 4) g.setColor(Color.YELLOW);
                else g.setColor(Color.RED);
                g.fillOval(ex + 13, ey + 2, 4, 4);
            }

            if (playerHp > 0) {
                int px = player.x * TILE_SIZE;
                int py = player.y * TILE_SIZE;
                if (playerHp <= 30 && tick % 8 < 4) g.setColor(Color.ORANGE);
                else g.setColor(Color.GREEN);
                int[] xPoints = { px + 15, px + 2, px + 15, px + 28 };
                int[] yPoints = { py + 2, py + 26, py + 20, py + 26 };
                g.fillPolygon(xPoints, yPoints, 4);
                g.setColor(Color.CYAN);
                g.fillOval(px + 12, py + 12, 6, 8);
                g.setColor(Color.ORANGE);
                if (tick % 4 < 2) {
                    g.fillPolygon(new int[]{px + 10, px + 15, px + 20}, new int[]{py + 22, py + 30, py + 22}, 3);
                }
            }

            g.setColor(Color.CYAN);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.drawString("Score: " + score, 10, 25);
            g.setColor(Color.WHITE);
            g.drawRect(10, 35, 200, 15);
            if (playerHp > 50) g.setColor(Color.GREEN);
            else if (playerHp > 20) g.setColor(Color.YELLOW);
            else g.setColor(Color.RED);
            int hpWidth = (int) ((playerHp / (double) MAX_HP) * 200);
            if (hpWidth > 0) g.fillRect(11, 36, hpWidth - 1, 14);

            if (gameState == State.PAUSED) {
                g.setColor(new Color(0, 0, 0, 150)); 
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setFont(new Font("Impact", Font.BOLD, 60));
                drawShadowText(g, "PAUSED", 200, 300, Color.YELLOW);
            }

            if (gameState == State.GAMEOVER) {
                g.setColor(new Color(150, 0, 0, 100)); 
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setFont(new Font("Impact", Font.BOLD, 60));
                drawShadowText(g, "GAME OVER", 140, 250, Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 30));
                drawShadowText(g, "Final Score: " + score, 190, 320, Color.YELLOW);
            }
        }

        // ==========================================
        // 🚨 補回：畫在最上層的常駐 HUD 排行榜
        // ==========================================
        if (gameState != State.LEADERBOARD) { // 大畫面排行榜時就不重複畫了
            int boardX = getWidth() - 170; 
            int boardY = 10;
            g.setColor(new Color(0, 0, 0, 150)); 
            g.fillRect(boardX, boardY, 160, 130);
            g.setColor(Color.WHITE);
            g.drawRect(boardX, boardY, 160, 130);

            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.setColor(Color.YELLOW);
            g.drawString("TOP 5 PILOTS", boardX + 30, boardY + 20);

            g.setFont(new Font("Arial", Font.BOLD, 12));
            if (highScores.isEmpty()) {
                g.setColor(Color.GRAY);
                g.drawString("No Records", boardX + 45, boardY + 60);
            } else {
                for (int i = 0; i < highScores.size(); i++) {
                    ScoreRecord r = highScores.get(i);
                    if (i == 0) g.setColor(new Color(255, 215, 0)); 
                    else if (i == 1) g.setColor(new Color(192, 192, 192)); 
                    else if (i == 2) g.setColor(new Color(205, 127, 50)); 
                    else g.setColor(Color.WHITE); 
                    
                    g.drawString((i + 1) + ". " + r.name, boardX + 10, boardY + 45 + (i * 18));
                    g.drawString(String.valueOf(r.score), boardX + 110, boardY + 45 + (i * 18));
                }
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        tick++;
        for (Star s : stars) s.update(getHeight());

        if (gameState != State.PLAYING) {
            repaint();
            return;
        }

        scrollOffset += SCROLL_SPEED;
        if (scrollOffset >= TILE_SIZE) {
            scrollOffset = 0; 
            for (int y = GRID_SIZE - 1; y > 0; y--) {
                System.arraycopy(map[y - 1], 0, map[y], 0, GRID_SIZE);
            }
            for (int x = 0; x < GRID_SIZE; x++) {
                map[0][x] = (Math.random() < 0.10) ? 1 : 0;
            }
        }

        if (tick % 3 == 0) {
            if (keys[KeyEvent.VK_W] && player.y > 10 && map[player.y - 1][player.x] == 0) player.y--;
            if (keys[KeyEvent.VK_S] && player.y < GRID_SIZE - 1 && map[player.y + 1][player.x] == 0) player.y++;
            if (keys[KeyEvent.VK_A] && player.x > 0 && map[player.y][player.x - 1] == 0) player.x--;
            if (keys[KeyEvent.VK_D] && player.x < GRID_SIZE - 1 && map[player.y][player.x + 1] == 0) player.x++;
        }

        if (shootCooldown > 0) shootCooldown--;
        if (keys[KeyEvent.VK_SPACE] && shootCooldown == 0 && bullets.size() < MAX_BULLETS) {
            bullets.add(new Bullet(player.x, player.y - 1, false)); 
            if (player.x > 0) bullets.add(new Bullet(player.x - 1, player.y - 1, false)); 
            if (player.x < GRID_SIZE - 1) bullets.add(new Bullet(player.x + 1, player.y - 1, false)); 
            shootCooldown = 4; 
        }

        if (tick % 1 == 0) {
            Iterator<Bullet> bit = bullets.iterator();
            while (bit.hasNext()) {
                Bullet b = bit.next();
                if (b.isEnemy) {
                    b.y++; 
                } else {
                    if (!enemies.isEmpty()) {
                        Enemy target = null;
                        for (Enemy en : enemies) {
                            if (en.y <= b.y) { target = en; break; }
                        }
                        if (target != null) {
                            if (b.x < target.x) b.x++;
                            else if (b.x > target.x) b.x--;
                        }
                    }
                    b.y--; 
                }
                
                if (b.x < 0 || b.x >= GRID_SIZE || b.y < 0 || b.y >= GRID_SIZE || map[b.y][b.x] == 1) { 
                    bit.remove();
                    continue; 
                }

                if (b.isEnemy) {
                    if (b.x == player.x && b.y == player.y) {
                        playerHp -= 10;
                        bit.remove();
                    }
                } else {
                    boolean hit = false;
                    for (int j = enemies.size() - 1; j >= 0; j--) {
                        Enemy en = enemies.get(j);
                        if (b.x == en.x && b.y == en.y) {
                            score += 100;
                            bit.remove();
                            enemies.remove(j);
                            hit = true;
                            break;
                        }
                    }
                }
            }
        }

        if (tick % 25 == 0 && enemies.size() < MAX_ENEMIES) {
            int spawnCount = (int)(Math.random() * 3) + 1;
            for (int i = 0; i < spawnCount; i++) {
                int spawnX = (int) (Math.random() * GRID_SIZE);
                if (map[0][spawnX] == 0) enemies.add(new Enemy(spawnX, 0));
            }
        }

        if (tick % 12 == 0) {
            Iterator<Enemy> eit = enemies.iterator();
            while (eit.hasNext()) {
                Enemy en = eit.next();
                en.move(player.x, player.y, map); 
                if (Math.random() < 0.10 && en.y + 1 < GRID_SIZE && map[en.y + 1][en.x] == 0) {
                    bullets.add(new Bullet(en.x, en.y + 1, true)); 
                }
                if (en.y >= GRID_SIZE) eit.remove(); 
            }
        }

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy en = enemies.get(i);
            if (en.x == player.x && en.y == player.y) {
                playerHp -= 20;
                enemies.remove(i); 
            }
        }

        if (map[player.y][player.x] == 1) {
            playerHp -= 15;
            map[player.y][player.x] = 0; 
        }

        if (playerHp <= 0 && gameState == State.PLAYING) {
            playerHp = 0; 
            timer.stop(); 
            String name = JOptionPane.showInputDialog(this, "最終得分: " + score + "\n請輸入大名：", "GAME OVER", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.trim().isEmpty()) name = "Unknown";
            else if (name.length() > 8) name = name.substring(0, 8); 
            saveScore(name.trim().toUpperCase(), score); 
            gameState = State.GAMEOVER; 
            timer.start(); 
        }
        repaint();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key < 256) keys[key] = true;

        if (gameState == State.MENU) {
            if (key == KeyEvent.VK_SPACE) gameState = State.PLAYING; 
            if (key == KeyEvent.VK_L) { loadScores(); gameState = State.LEADERBOARD; }
        } 
        else if (gameState == State.LEADERBOARD) {
            if (key == KeyEvent.VK_SPACE) gameState = State.MENU;
        }
        else {
            if (key == KeyEvent.VK_P) {
                if (gameState == State.PLAYING) gameState = State.PAUSED;
                else if (gameState == State.PAUSED) gameState = State.PLAYING;
            }
            if (key == KeyEvent.VK_R) { initGame(); gameState = State.PLAYING; }
        }
    }
    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key < 256) keys[key] = false;
    }
    public void keyTyped(KeyEvent e) {}
}

class Star {
    int x, y, speed, size;
    Star(int x, int y, int speed, int size) {
        this.x = x; this.y = y; this.speed = speed; this.size = size;
    }
    void update(int height) {
        y += speed;
        if (y > height) { 
            y = 0;
            x = (int)(Math.random() * 600);
        }
    }
}

class ScoreRecord implements Comparable<ScoreRecord> {
    String name; int score;
    ScoreRecord(String name, int score) { this.name = name; this.score = score; }
    @Override
    public int compareTo(ScoreRecord other) { return Integer.compare(other.score, this.score); }
}
class Bullet {
    int x, y; boolean isEnemy;
    Bullet(int x, int y, boolean isEnemy) { this.x = x; this.y = y; this.isEnemy = isEnemy; }
}
class Player {
    int x, y; Player(int x, int y) { this.x = x; this.y = y; }
}
class Enemy {
    int x, y; Enemy(int x, int y) { this.x = x; this.y = y; }
    public void move(int tx, int ty, int[][] map) {
        int rows = map.length, cols = map[0].length;
        Queue<Node> queue = new LinkedList<>();
        boolean[][] visited = new boolean[rows][cols];
        queue.add(new Node(x, y, null));
        visited[y][x] = true;
        Node targetNode = null;
        int[][] dirs = { {0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1} };

        while (!queue.isEmpty()) {
            Node curr = queue.poll();
            if (curr.x == tx && curr.y == ty) { targetNode = curr; break; }
            for (int[] d : dirs) {
                int nx = curr.x + d[0], ny = curr.y + d[1];
                if (nx >= 0 && nx < cols && ny >= 0 && ny < rows && map[ny][nx] == 0 && !visited[ny][nx]) {
                    visited[ny][nx] = true;
                    queue.add(new Node(nx, ny, curr));
                }
            }
        }
        if (targetNode != null && targetNode.parent != null) {
            Node step = targetNode;
            while (step.parent != null && step.parent.parent != null) step = step.parent;
            this.x = step.x; this.y = step.y;
        } else {
            if (this.y < rows - 1 && map[this.y + 1][this.x] == 0) this.y++;
        }
    }
    class Node {
        int x, y; Node parent;
        Node(int x, int y, Node p) { this.x = x; this.y = y; this.parent = p; }
    }
}