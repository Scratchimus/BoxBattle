package com.bb.server;

import com.bb.common.data.*;
import com.bb.common.net.DataAccumulator;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.List;

import static com.bb.common.data.GameWorld.CELL_SIZE;

public class DemoServer {
    public static void main(String[] args) throws IOException {
        new DemoServer(8080).go();
    }

    private int port;
    private GameWorld world;
    private Map<String, PlayerStats> gameState;
    private List<ShotFired> shots;
    private Map<String, List<ShotFired>> shotsToTransmit;
    private List<Point> obstacles;

    public DemoServer(int port) {
        this.port = port;
        world = new GameWorld(30);
        world.populateRandomWalls();
        populateObstacles();
        gameState = new HashMap<>();
        shots = new ArrayList<>();
        shotsToTransmit = new HashMap<>();
    }

    private void populateObstacles() {
        synchronized (world) {
            obstacles = new ArrayList<>();
            for (int ii = 0; ii < world.getSize(); ii++) {
                for (int jj = 0; jj < world.getSize(); jj++) {
                    if (world.get(ii, jj) == TerrainType.WALL) {
                        obstacles.add(new Point(ii, jj));
                    }
                }
            }
        }
    }

    public void go() throws IOException {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.bind(new InetSocketAddress(port));

        addBot(200, 200);

        new GameManagerThread().start();

        System.out.println("Server ready");
        while (true) {
            SocketChannel sc = ssc.accept();
            System.out.println("Client connected");
            new HandlerThread(sc).start();
        }
    }

    private void addBot(int x, int y) {
        PlayerStats bot = new PlayerStats(generateRandomPlayerId(), x, y, 100, true);
        synchronized (gameState) {
            gameState.put(bot.getPlayerId(), bot);
        }
        new BotHandlerThread(bot).start();
    }

    private class BotHandlerThread extends Thread {
        PlayerStats bot;

        public BotHandlerThread(PlayerStats bot) {
            this.bot = bot;
        }

        public void run() {
            int[] dx = new int[] { 0, 1, 0, -1 };
            int[] dy = new int[] { 1, 0, -1, 0 };

            while (true) {
                try {
                    Thread.sleep(5);

                    int dir = (int)(Math.random() * dx.length);

                    double newX = bot.getX() + dx[dir];
                    double newY = bot.getY() + dy[dir];

                    int xBlock = (int)(newX / CELL_SIZE);
                    int yBlock = (int)(newY / CELL_SIZE);

                    if (world.get(xBlock, yBlock) != TerrainType.WALL) {
                        bot.setX(newX);
                        bot.setY(newY);
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private class HandlerThread extends Thread {
        SocketChannel sc;
        String playerId;
        long lastShotTime;
        long shotInterval = 150;

        public HandlerThread(SocketChannel sc) {
            this.sc = sc;
        }

        public void run() {
            DataAccumulator dac = new DataAccumulator();
            boolean[] keyDown = new boolean[256];

            try {
                sc.configureBlocking(false);
                ByteBuffer buffer = ByteBuffer.allocate(1024);

                long lastTransmitTime = 0;

                // TODO: Figure out where to spawn in new players

                playerId = generateRandomPlayerId();
                PlayerStats playerStats = new PlayerStats(playerId, 50, 50, 100, false);

                synchronized (gameState) {
                    gameState.put(playerId, playerStats);
                }

                sendPositionToPlayer(sc, playerStats);
                sendWorldToClient(sc);

                while (true) {
                    readUpdatesFromClient(dac, buffer);

                    ClientShotAttempt csa = processUpdatesFromClient(dac, keyDown);

                    updatePlayerState(keyDown, csa);

                    if (System.currentTimeMillis() - lastTransmitTime > 20) {
                        sendStateToClient(sc);
                        lastTransmitTime = System.currentTimeMillis();
                    }

                    Thread.sleep(5);
                }
            } catch (Exception ex) {
                if (ex.getMessage() != null && ex.getMessage().startsWith("Connection reset")) {
                    System.out.println("Client disconnected");
                } else {
                    ex.printStackTrace();
                }
            }

            if (playerId != null) {
                synchronized (gameState) {
                    gameState.remove(playerId);
                }
            }
        }


        private void sendPositionToPlayer(SocketChannel sc, PlayerStats playerStats) throws IOException {
            sc.write(ByteBuffer.wrap((playerStats.toString() + DataAccumulator.DELIMITER).getBytes()));
        }

        private void sendWorldToClient(SocketChannel sc) throws IOException {
            sc.write(ByteBuffer.wrap((world.toString() + DataAccumulator.DELIMITER).getBytes()));
        }

        private void readUpdatesFromClient(DataAccumulator dac, ByteBuffer buffer) throws IOException {
            int bytesRead = sc.read(buffer);
            if (bytesRead > 0) {
                dac.accumulate(buffer.array(), bytesRead);
                buffer.clear();
            }
        }

        private void updatePlayerState(boolean[] keyDown, ClientShotAttempt shotAttempt) {
            PlayerStats ppos = null;
            synchronized (gameState) {
                ppos = gameState.get(playerId);
            }

            // TODO: Check for obstacles
            int dx = 0, dy = 0;
            if (keyDown[KeyEvent.VK_UP]) {
                dy = -1;
            } else if (keyDown[KeyEvent.VK_DOWN]) {
                dy = 1;
            }
            if (keyDown[KeyEvent.VK_LEFT]) {
                dx = -1;
            } else if (keyDown[KeyEvent.VK_RIGHT]) {
                dx = 1;
            }

            double newX = ppos.getX() + dx;
            double newY = ppos.getY() + dy;
            int xBlock = (int)(newX / CELL_SIZE);
            int yBlock = (int)(newY / CELL_SIZE);

            if (world.get(xBlock, yBlock) != TerrainType.WALL) {
                ppos.setX(newX);
                ppos.setY(newY);
            } else {
                System.out.println("Cannot move to " + newX + ", " + newY);
                System.out.println("      Checking the terrain at " + xBlock + ", " + yBlock);
            }

            if (shotAttempt != null) {
                long now = System.currentTimeMillis();
                if (now - lastShotTime > shotInterval) {
                    Point endPt = shotAttempt.getAimPt();

                    // Extend shot past aim point
                    double shotAngle = Math.atan2(endPt.getY() - ppos.getY(), endPt.getX() - ppos.getX());
                    endPt.x += 500 * Math.cos(shotAngle);
                    endPt.y += 500 * Math.sin(shotAngle);

                    double x1 = ppos.getX();
                    double y1 = ppos.getY();

                    double x2 = endPt.getX();
                    double y2 = endPt.getY();

                    for (Point obs : obstacles) {
                        double[] intPt = checkIntersectionWithBlock(obs.x, obs.y, x1, y1, x2, y2);
                        if (intPt != null) {
                            if (endPt.distance(intPt[0], intPt[1]) > .5) {
                                endPt.setLocation(intPt[0], intPt[1]);
//                                        checkIntersectionWithBlock(ii, jj, x1, y1, x2, y2);
                                x2 = endPt.getX();
                                y2 = endPt.getY();
                            }
                        }
                    }

                    // TODO: Check for shots hitting other players
                    synchronized (gameState) {

                    }

                    ShotFired sf = new ShotFired(new Point((int) ppos.getX(), (int) ppos.getY()), endPt);

                    synchronized (shots) {
                        shots.add(sf);
                    }

                    synchronized (shotsToTransmit) {
                        for (String playerId : gameState.keySet()) {
                            List<ShotFired> tt = shotsToTransmit.get(playerId);
                            if (tt == null) {
                                tt = new ArrayList<>();
                                shotsToTransmit.put(playerId, tt);
                            }
                            tt.add(sf);
                        }
                    }

                    lastShotTime = now;
                }
            }
        }

        private ClientShotAttempt processUpdatesFromClient(DataAccumulator dac, boolean[] keyDown) throws IOException {
            ClientShotAttempt ret = null;
            while (dac.hasData()) {
                Object obj = dac.getData();
                if (obj instanceof PlayerStats) {

                    PlayerStats pp = (PlayerStats)obj;
                    playerId = pp.getPlayerId();

                    synchronized (gameState) {
                        gameState.put(playerId, pp);
                    }
                } else if (obj instanceof ClientKeyEvent) {
                    ClientKeyEvent cke = (ClientKeyEvent)obj;
                    keyDown[cke.getKeyCode()] = cke.isDown();
                } else if (obj instanceof ClientShotAttempt) {
                    ret = (ClientShotAttempt)obj;
                } else if (obj instanceof TimingPacket) {
                    TimingPacket tp = (TimingPacket)obj;
                    tp.recordResponseTime();
                    sc.write(ByteBuffer.wrap((tp.toString() + DataAccumulator.DELIMITER).getBytes()));
                }
            }
            return ret;
        }

        private void sendStateToClient(SocketChannel sc) throws IOException {
            StringBuilder bld = new StringBuilder();
            synchronized (gameState) {
                for (PlayerStats pp : gameState.values()) {
                    bld.append(pp.toString()).append(DataAccumulator.DELIMITER);
                }
            }

            synchronized (shotsToTransmit) {
                List<ShotFired> toTransmit = shotsToTransmit.get(playerId);
                if (toTransmit != null) {
                    for (ShotFired s : toTransmit) {
                        bld.append(s.toString()).append(DataAccumulator.DELIMITER);
                    }
                    toTransmit.clear();
                }
            }

            sc.write(ByteBuffer.wrap(bld.toString().getBytes()));
        }
    }

    private double[] checkIntersectionWithBlock(int blockX, int blockY, double x1, double y1, double x2, double y2) {

        double[] top = getIntersection(x1, y1, x2, y2, blockX*CELL_SIZE, blockY*CELL_SIZE, CELL_SIZE + blockX*CELL_SIZE, blockY*CELL_SIZE);
        double[] bot = getIntersection(x1, y1, x2, y2, blockX*CELL_SIZE, CELL_SIZE + blockY* CELL_SIZE, CELL_SIZE + blockX*CELL_SIZE, CELL_SIZE + blockY*CELL_SIZE);
        double[] left = getIntersection(x1, y1, x2, y2, blockX*CELL_SIZE, blockY*CELL_SIZE, blockX*CELL_SIZE, CELL_SIZE + blockY*CELL_SIZE);
        double[] right = getIntersection(x1, y1, x2, y2, CELL_SIZE + blockX*CELL_SIZE, blockY*CELL_SIZE, CELL_SIZE + blockX*CELL_SIZE, CELL_SIZE + blockY*CELL_SIZE);

        double[] ret = top;

        if (bot != null && (ret == null || dist(x1, y1, ret[0], ret[1]) > dist(x1, y1, bot[0], bot[1]))) {
            ret = bot;
        }
        if (left != null && (ret == null || dist(x1, y1, ret[0], ret[1]) > dist(x1, y1, left[0], left[1]))) {
            ret = left;
        }
        if (right != null && (ret == null || dist(x1, y1, ret[0], ret[1]) > dist(x1, y1, right[0], right[1]))) {
            ret = right;
        }

        return ret;
    }

    private double dist(double x1, double y1, double x2, double y2) {
        double dx = x2-x1;
        double dy = y2-y1;
        return Math.sqrt(dx*dx + dy*dy);
    }

    private class GameManagerThread extends Thread {
        public void run() {
            while (true) {
                try {
                    Thread.sleep(500);

                    synchronized (shots) {
                        Iterator<ShotFired> iter = shots.iterator();
                        while (iter.hasNext()) {
                            ShotFired sf = iter.next();
                            if (sf.expired()) {
                                iter.remove();
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }


    public double[] getIntersection(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
        // parallel check
        if ((x1-x2) * (y3-y4) - (y1-y2) * (x3-x4) == 0) {
            return null;
        }

        double x = (((x1*y2 - y1*x2)*(x3 - x4)) - (x1 - x2)*(x3*y4 - y3*x4)) /
                ((x1 - x2)*(y3 - y4) - (y1 - y2)*(x3 - x4));
        double y = (((x1*y2 - y1*x2)*(y3 - y4)) - (y1 - y2)*(x3*y4 - y3*x4)) /
                ((x1 - x2)*(y3 - y4) - (y1 - y2)*(x3 - x4));

        if (x >= Math.min(x1, x2) && x <= Math.max(x1, x2) && y >= Math.min(y1, y2) && y <= Math.max(y1, y2) &&
            x >= Math.min(x3, x4) && x <= Math.max(x3, x4) && y >= Math.min(y3, y4) && y <= Math.max(y3, y4))
        {
            return new double[]{x, y};
        } else {
            return null;
        }
    }

    private String generateRandomPlayerId() {
        String ret = "";
        for (int ii=0; ii<10; ii++) {
            ret += (char)((int)(Math.random()*25) + 'a');
        }
        return ret;
    }
}

