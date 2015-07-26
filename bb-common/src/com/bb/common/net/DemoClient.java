package com.bb.common.net;

import com.bb.common.data.PlayerPosition;

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
    private Map<String, PlayerPosition> gameState;
    private boolean[] keyIsDown;

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
        playerId = "";
        for (int ii=0; ii<10; ii++) {
            playerId += (char)('a' + (int)(Math.random() * 20));
        }
        myPosition = new PlayerPosition(playerId, 50, 50);
        gameState = new HashMap<>();
        keyIsDown = new boolean[256];

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

                long lastTransmitTime = 0;

                while (true) {
                    updateSelf();

                    readUpdatesFromServer(dac, buffer);

                    processUpdatesFromServer(dac);

                    if (System.currentTimeMillis() - lastTransmitTime > 20) {
                        sendPositionToServer(sc);
                        lastTransmitTime = System.currentTimeMillis();
                    }

                    Thread.sleep(5);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        private void updateSelf() {
            if (keyIsDown[KeyEvent.VK_UP]) {
                myPosition.setY(myPosition.getY()-1);
            }
            if (keyIsDown[KeyEvent.VK_DOWN]) {
                myPosition.setY(myPosition.getY()+1);
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
                if (obj instanceof PlayerPosition) {
                    PlayerPosition pos = (PlayerPosition)obj;
                    synchronized (gameState) {
                        gameState.put(pos.getPlayerId(), pos);
                    }
                    // TODO: Figure out how to do this in a way that the server isn't always clobbering the client
//                            if (pos.getPlayerId().equals(playerId)) {
//                                myPosition.setX(pos.getX());
//                                myPosition.setY(pos.getY());
//                            }
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
        keyIsDown[e.getKeyCode()] = true;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        keyIsDown[e.getKeyCode()] = false;
    }
}
