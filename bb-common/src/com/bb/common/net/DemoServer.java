package com.bb.common.net;

import com.bb.common.data.PlayerPosition;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jake on 7/25/15.
 */
public class DemoServer {
    public static void main(String[] args) throws IOException {
        new DemoServer(8080).go();
    }

    private int port;
    private Map<String, PlayerPosition> gameState;

    public DemoServer(int port) {
        this.port = port;
        gameState = new HashMap<>();
    }

    public void go() throws IOException {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.bind(new InetSocketAddress(port));

        while (true) {
            SocketChannel sc = ssc.accept();
            new HandlerThread(sc).start();
        }
    }

    private class HandlerThread extends Thread {
        SocketChannel sc;
        String playerId;

        public HandlerThread(SocketChannel sc) {
            this.sc = sc;
        }

        public void run() {
            DataAccumulator dac = new DataAccumulator();
            try {
                sc.configureBlocking(false);
                ByteBuffer buffer = ByteBuffer.allocate(1024);

                long lastTransmitTime = 0;

                while (true) {
                    readUpdatesFromClient(dac, buffer);

                    processUpdatesFromClient(dac);

                    if (System.currentTimeMillis() - lastTransmitTime > 20) {
                        sendStateToClient(sc);
                        lastTransmitTime = System.currentTimeMillis();
                    }

                    Thread.sleep(5);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (playerId != null) {
                synchronized (gameState) {
                    gameState.remove(playerId);
                }
            }
        }

        private void readUpdatesFromClient(DataAccumulator dac, ByteBuffer buffer) throws IOException {
            int bytesRead = sc.read(buffer);
            if (bytesRead > 0) {
                dac.accumulate(buffer.array(), bytesRead);
                buffer.clear();
            }
        }

        private void processUpdatesFromClient(DataAccumulator dac) {
            while (dac.hasData()) {
                Object obj = dac.getData();
                if (obj instanceof PlayerPosition) {

                    PlayerPosition pp = (PlayerPosition)obj;
                    playerId = pp.getPlayerId();

                    synchronized (gameState) {
                        gameState.put(playerId, pp);
                    }
                }
            }
        }

        private void sendStateToClient(SocketChannel sc) throws IOException {
            StringBuilder bld = new StringBuilder();
            synchronized (gameState) {
                for (PlayerPosition pp : gameState.values()) {
                    bld.append(pp.toString()).append(DataAccumulator.DELIMITER);
                }
            }

            sc.write(ByteBuffer.wrap(bld.toString().getBytes()));
        }
    }
}
