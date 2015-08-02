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

/**
 * Created by jake on 7/25/15.
 */
public class DemoServer {
    public static void main(String[] args) throws IOException {
        new DemoServer(8080).go();
    }

    private int port;
    private GameWorld world;
    private Map<String, PlayerPosition> gameState;
    private List<ShotFired> shots;

    public DemoServer(int port) {
        this.port = port;
        world = new GameWorld(30);
        world.populateRandomWalls();
        gameState = new HashMap<>();
        shots = new ArrayList<>();
    }

    public void go() throws IOException {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.bind(new InetSocketAddress(port));

        new GameManagerThread().start();

        System.out.println("Server ready");
        while (true) {
            SocketChannel sc = ssc.accept();
            System.out.println("Client connected");
            new HandlerThread(sc).start();
        }
    }

    private class HandlerThread extends Thread {
        SocketChannel sc;
        String playerId;
        long lastShotTime;
        long shotInterval = 500;

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

                playerId = "";
                for (int ii=0; ii<10; ii++) {
                    playerId += (char)((int)(Math.random()*25) + 'a');
                }
                PlayerPosition playerPosition = new PlayerPosition(playerId, 50, 50);

                synchronized (gameState) {
                    gameState.put(playerId, playerPosition);
                }
                sendPositionToPlayer(sc, playerPosition);
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
                if (ex.getMessage().startsWith("Connection reset")) {
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

        private void sendPositionToPlayer(SocketChannel sc, PlayerPosition playerPosition) throws IOException {
            sc.write(ByteBuffer.wrap((playerPosition.toString() + DataAccumulator.DELIMITER).getBytes()));
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
            PlayerPosition ppos = null;
            synchronized (gameState) {
                ppos = gameState.get(playerId);
            }

            // TODO: Check for obstacles
            int dx = 0, dy = 0;
            if (keyDown[KeyEvent.VK_UP]) {
                dy = -1;
            } else if (keyDown[KeyEvent.VK_DOWN]) {
                dy = 1;
            } else if (keyDown[KeyEvent.VK_LEFT]) {
                dx = -1;
            } else if (keyDown[KeyEvent.VK_RIGHT]) {
                dx = 1;
            }

            double newX = ppos.getX() + dx;
            double newY = ppos.getY() + dy;
            int xBlock = (int)(newX / 10);
            int yBlock = (int)(newY / 10);

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
                    shots.add(new ShotFired(new Point((int)ppos.getX(), (int)ppos.getY()), shotAttempt.getAimPt()));
                    lastShotTime = now;
                }
            }
        }

        private ClientShotAttempt processUpdatesFromClient(DataAccumulator dac, boolean[] keyDown) {
            ClientShotAttempt ret = null;
            while (dac.hasData()) {
                Object obj = dac.getData();
                if (obj instanceof PlayerPosition) {

                    PlayerPosition pp = (PlayerPosition)obj;
                    playerId = pp.getPlayerId();

                    synchronized (gameState) {
                        gameState.put(playerId, pp);
                    }
                } else if (obj instanceof ClientKeyEvent) {
                    ClientKeyEvent cke = (ClientKeyEvent)obj;
                    keyDown[cke.getKeyCode()] = cke.isDown();
                } else if (obj instanceof ClientShotAttempt) {
                    ret = (ClientShotAttempt)obj;
                }
            }
            return ret;
        }

        private void sendStateToClient(SocketChannel sc) throws IOException {
            StringBuilder bld = new StringBuilder();
            synchronized (gameState) {
                for (PlayerPosition pp : gameState.values()) {
                    bld.append(pp.toString()).append(DataAccumulator.DELIMITER);
                }
            }
            synchronized (shots) {
                for (ShotFired s : shots) {
                    bld.append(s.toString()).append(DataAccumulator.DELIMITER);
                }
            }

            sc.write(ByteBuffer.wrap(bld.toString().getBytes()));
        }
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
}
