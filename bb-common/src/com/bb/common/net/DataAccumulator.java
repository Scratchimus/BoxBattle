package com.bb.common.net;

import com.bb.common.data.ClientKeyEvent;
import com.bb.common.data.GameWorld;
import com.bb.common.data.PlayerPosition;

/**
 * Created by jake on 7/25/15.
 */
public class DataAccumulator {
    public static final String DELIMITER = "!^!";

    private StringBuilder builder;

    public DataAccumulator() {
        builder = new StringBuilder();
    }

    public void accumulate(byte[] data, int bytesRead) {
        for (int ii=0; ii<bytesRead; ii++) {
            builder.append((char)data[ii]);
        }
    }

    public boolean hasData() {
        return builder.indexOf(DELIMITER) != -1;
    }

    public Object getData() {
        if (hasData()) {
            int pos = builder.indexOf(DELIMITER);
            String data = builder.substring(0, pos);
            builder.replace(0, pos + DELIMITER.length(), "");

            if (PlayerPosition.matches(data)) {
                return PlayerPosition.parse(data);
            } else if (GameWorld.matches(data)) {
                return GameWorld.parse(data);
            } else if (ClientKeyEvent.matches(data)) {
                return ClientKeyEvent.parse(data);
            } else {
                System.out.println("Unknown data starting with '" + data.substring(0, 10) + "...'");
            }
        }

        return null;
    }
}
