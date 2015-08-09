package com.bb.client;

import com.bb.common.data.*;
import com.bb.common.net.DataAccumulator;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.List;

/**
 * Created by jake on 7/25/15.
 */
public class DemoClient extends JPanel implements KeyListener, MouseListener, MouseMotionListener {
    private PlayerStats myStats;
    private GameWorld gameWorld;
    private List<ShotFired> shots;
    private Map<String, PlayerStats> gameState;
    private ClientKeyEvent current;
    private ClientShotAttempt currentMouse;
    private Point aimPt;
    private boolean mouseDown;
    private long lastShotAttempt;

    public static void main(String[] args) throws IOException {
        JFrame host = new JFrame();
        host.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        DemoClient client = new DemoClient();
        host.add(client);
        host.pack();
        host.setVisible(true);
        client.startBackgroundThreads(SocketChannel.open(new InetSocketAddress(8080)));
    }

    public DemoClient() {
        gameState = new HashMap<>();
        shots = new ArrayList<>();

        Dimension dim = new Dimension(800, 600);
        setSize(dim);
        setMinimumSize(dim);
        setPreferredSize(dim);

        addKeyListener(this);
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public void startBackgroundThreads(SocketChannel sc) {
        new NetworkingThread(sc).start();
        new RepaintThread().start();
    }

    private class NetworkingThread extends Thread {
        private SocketChannel sc;

        private NetworkingThread(SocketChannel sc) {
            this.sc = sc;
        }

        @Override
        public void run() {
            try {
                System.out.println("CLIENT is now connected to the server");
                sc.configureBlocking(false);
                DataAccumulator dac = new DataAccumulator();
                ByteBuffer buffer = ByteBuffer.allocate(1024);

                while (true) {
                    Thread.sleep(5);

                    readUpdatesFromServer(dac, buffer);

                    processUpdatesFromServer(dac);
                    pruneDeadShots();

                    sendInputUpdatesToServer();

                    if (Math.random() < .0001) {
                        initiateTimingPacket();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        private void pruneDeadShots() {
            synchronized (shots) {
                Iterator<ShotFired> iter = shots.iterator();
                while (iter.hasNext()) {
                    ShotFired sf = iter.next();
                    if (sf.expired()) {
                        iter.remove();
                    }
                }
            }
        }

        private void sendInputUpdatesToServer() throws IOException {
            ClientKeyEvent keyToSend = current;
            if (keyToSend != null) {
                sc.write(ByteBuffer.wrap((keyToSend.toString() + DataAccumulator.DELIMITER).getBytes()));
                current = null;
            }

            long now = System.currentTimeMillis();
            if (mouseDown && now - lastShotAttempt > 50 && aimPt != null) {
                ClientShotAttempt shotAttempt = new ClientShotAttempt(aimPt);
                sc.write(ByteBuffer.wrap((shotAttempt.toString() + DataAccumulator.DELIMITER).getBytes()));
                lastShotAttempt = now;
            }
        }

        private void initiateTimingPacket() throws IOException {
            sc.write(ByteBuffer.wrap((new TimingPacket() + DataAccumulator.DELIMITER).getBytes()));
        }

        private void readUpdatesFromServer(DataAccumulator dac, ByteBuffer buffer) throws IOException {
            int bytesRead = sc.read(buffer);
            if (bytesRead > 0) {
                dac.accumulate(buffer.array(), bytesRead);
                buffer.clear();
            }
        }

        private void processUpdatesFromServer(DataAccumulator dac) {
            while (dac.hasData()) {
                Object obj = dac.getData();
                if (obj instanceof GameWorld) {
                    gameWorld = (GameWorld)obj;
                } else if (obj instanceof PlayerStats) {
                    PlayerStats stats = (PlayerStats)obj;

                    if (myStats == null) {
                        // The first player stats sent will always be for ME
                        myStats = stats;
                    }

                    synchronized (gameState) {
                        gameState.put(stats.getPlayerId(), stats);
                    }
                } else if (obj instanceof ShotFired) {
                    shots.add((ShotFired)obj);
                } else if (obj instanceof TimingPacket) {
                    TimingPacket tp = (TimingPacket)obj;
                    tp.recordReturnTime();
                    System.out.println("Round trip time is " + (tp.getReturnTime() - tp.getInitiatedTime()) + " ms.");
                }
            }
        }
    }

    private class RepaintThread extends Thread {
        private BufferedImage backBuf;

        public void run() {
            BufferedImage floorImg;
            BufferedImage wallImg;
            BufferedImage p1Img;
            BufferedImage p2Img;

            try {
                p1Img = ImageIO.read(getClass().getClassLoader().getResourceAsStream("textures/player1.png"));
                p2Img = ImageIO.read(getClass().getClassLoader().getResourceAsStream("textures/player2.png"));
                floorImg = ImageIO.read(getClass().getClassLoader().getResourceAsStream("textures/floor.png"));
                wallImg = ImageIO.read(getClass().getClassLoader().getResourceAsStream("textures/wall.png"));
            } catch (IOException ex) {
                ex.printStackTrace();
                return;
            }

            while (true) {
                try { Thread.sleep(5); } catch(InterruptedException intex) {}

                if ((backBuf == null) || backBuf.getWidth() != getWidth() || backBuf.getHeight() != getHeight()) {
                    backBuf = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
                }

                Graphics g = backBuf.getGraphics();

                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());

                java.util.List<PlayerStats> positions = new ArrayList<>();
                synchronized (gameState) {
                    positions.addAll(gameState.values());
                }

                if (gameWorld != null) {
                    for (int xx = 0; xx < gameWorld.getSize(); xx++) {
                        for (int yy = 0; yy < gameWorld.getSize(); yy++) {
                            if (gameWorld.get(xx, yy) == TerrainType.OPEN) {
                                g.setColor(Color.LIGHT_GRAY);
                                g.drawImage(floorImg, xx * GameWorld.CELL_SIZE, yy * GameWorld.CELL_SIZE, GameWorld.CELL_SIZE, GameWorld.CELL_SIZE, null);
                            } else {
                                g.drawImage(wallImg, xx * GameWorld.CELL_SIZE, yy * GameWorld.CELL_SIZE, GameWorld.CELL_SIZE, GameWorld.CELL_SIZE, null);
                            }
                        }
                    }
                }

                List<ShotFired> shotsToDraw = new ArrayList<>();
                synchronized (shots) {
                    shotsToDraw.addAll(shots);
                }

                for (ShotFired sf : shotsToDraw) {
                    if (!sf.expired()) {
                        float age = sf.age();
                        float ageRel = age / ShotFired.MAX_RENDER_AGE;
                        float col = 1 - ageRel;
                        if (col >= 0 && col <= 1) {
                            g.setColor(new Color(col, 0, 0));
                            g.drawLine(sf.getOrigin().x, sf.getOrigin().y, sf.getTarget().x, sf.getTarget().y);
                        }
                    }
                }

                for (PlayerStats player : positions) {
                    Image pimg;
                    if (player.getPlayerId().equals(myStats.getPlayerId())) {
                        pimg = p1Img;
                    } else {
                        pimg = p2Img;
                    }

                    // TODO: Keep track of player facing (?) and rotate the players image
                    g.drawImage(pimg, (int) (player.getX()) - 5, (int) (player.getY() - 5), null);

                    // TODO: Draw a health bar per player?
                }

                g.setColor(Color.BLACK);
                g.drawString("Health: " + myStats.getHealth(), 15, 15);

                if (getGraphics() != null) {
                    getGraphics().drawImage(backBuf, 0, 0, null);
                }
            }
        }
    }

    public void keyPressed(KeyEvent e) { current = new ClientKeyEvent(e.getKeyCode(), true); }
    public void keyReleased(KeyEvent e) {
        current = new ClientKeyEvent(e.getKeyCode(), false);
    }
    public void mousePressed(MouseEvent e) {
        mouseDown = true;
    }
    public void mouseReleased(MouseEvent e) { mouseDown = false; }
    public void mouseDragged(MouseEvent e) { aimPt = e.getPoint(); }
    public void mouseMoved(MouseEvent e) { aimPt = e.getPoint(); }

    // Currently unused
    public void keyTyped(KeyEvent e) {}
    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
}
