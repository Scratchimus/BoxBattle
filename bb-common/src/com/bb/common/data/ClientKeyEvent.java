package com.bb.common.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jake on 7/26/15.
 */
public class ClientKeyEvent {
    private static final String PREFIX = "ClientKeyEvent";

    private int keyCode;
    private boolean down;

    public ClientKeyEvent(int keyCode, boolean down) {
        this.keyCode = keyCode;
        this.down = down;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public boolean isDown() {
        return down;
    }

    public String toString() {
        return PREFIX + "{" + keyCode + "," + String.valueOf(down) + "}";
    }
    public static boolean matches(String data) {
        return data.startsWith(PREFIX);
    }
    public static ClientKeyEvent parse(String data) {
        Pattern p = Pattern.compile(PREFIX + "\\{(.*)\\}");
        Matcher m = p.matcher(data);
        if (m.matches()) {
            if (m.groupCount() == 1) {
                String body = m.group(1);
                String[] parts = body.split(",");
                int keyCode = Integer.parseInt(parts[0]);
                boolean isDown = Boolean.parseBoolean(parts[1]);
                return new ClientKeyEvent(keyCode, isDown);
            }
        }
        return null;
    }
}
