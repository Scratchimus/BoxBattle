package com.bb.common.net;

import com.bb.common.data.ClientKeyEvent;
import com.bb.common.data.GameWorld;
import com.bb.common.data.PlayerPosition;
import com.bb.common.data.TerrainType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageFilter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

/**
 * Created by jake on 7/25/15.
 */
public class DemoClient extends JPanel implements KeyListener {
    private String playerId;
    private PlayerPosition myPosition;
    private volatile GameWorld gameWorld;
    private Map<String, PlayerPosition> gameState;
    private volatile ClientKeyEvent current;

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

        Dimension dim = new Dimension(800, 600);
        setSize(dim);
        setMinimumSize(dim);
        setPreferredSize(dim);

        addKeyListener(this);
        setFocusable(true);
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
                sc.configureBlocking(false);
                DataAccumulator dac = new DataAccumulator();
                ByteBuffer buffer = ByteBuffer.allocate(1024);

                while (true) {
                    readUpdatesFromServer(dac, buffer);

                    processUpdatesFromServer(dac);

                    sendInputUpdatesToServer();

                    Thread.sleep(5);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        private void sendInputUpdatesToServer() throws IOException {
            ClientKeyEvent toSend = current;
            if (toSend != null) {
                sc.write(ByteBuffer.wrap((toSend.toString() + DataAccumulator.DELIMITER).getBytes()));
            }
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
                } else if (obj instanceof PlayerPosition) {
                    PlayerPosition pos = (PlayerPosition)obj;

                    if (playerId == null) {
                        // The first player position sent will always be for ME
                        myPosition = pos;
                        playerId = pos.getPlayerId();
                    }

                    synchronized (gameState) {
                        gameState.put(pos.getPlayerId(), pos);
                    }
                }
            }
        }

        private void sendPositionToServer(SocketChannel sc) throws IOException {
            sc.write(ByteBuffer.wrap((myPosition.toString() + DataAccumulator.DELIMITER).getBytes()));
        }
    }

    private class RepaintThread extends Thread {
        private BufferedImage backBuf;

        public void run() {
            while (true) {
                if ((backBuf == null) || backBuf.getWidth() != getWidth() || backBuf.getHeight() != getHeight()) {
                    backBuf = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
                }

                Graphics g = backBuf.getGraphics();

                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());

                java.util.List<PlayerPosition> positions = new ArrayList<>();
                synchronized (gameState) {
                    positions.addAll(gameState.values());
                }

                if (gameWorld != null) {
                    for (int xx = 0; xx < gameWorld.getSize(); xx++) {
                        for (int yy = 0; yy < gameWorld.getSize(); yy++) {
                            if (gameWorld.get(xx, yy) == TerrainType.OPEN) {
                                g.setColor(Color.LIGHT_GRAY);
                            } else {
                                g.setColor(Color.BLACK);
                            }
                            g.fillRect(xx * 10, yy * 10, 10, 10);
                        }
                    }
                }

                for (PlayerPosition pos : positions) {
                    if (playerId.equals(pos.getPlayerId())) {
                        g.setColor(Color.BLUE);
                    } else {
                        g.setColor(Color.RED);
                    }
                    g.fillRect((int) (pos.getX()) - 3, (int) (pos.getY() - 3), 7, 7);
                }

                if (getGraphics() != null) {
                    getGraphics().drawImage(backBuf, 0, 0, null);
                }

                try { Thread.sleep(10); } catch(InterruptedException intex) {}
            }
        }
    }

    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        current = new ClientKeyEvent(e.getKeyCode(), true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        current = new ClientKeyEvent(e.getKeyCode(), false);
    }
}
