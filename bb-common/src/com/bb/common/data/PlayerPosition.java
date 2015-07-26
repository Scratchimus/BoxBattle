package com.bb.common.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jake on 7/25/15.
 */
public class PlayerPosition {
    public static final java.lang.String PREFIX = "PlayerLocation";
    private String playerId;
    private double x, y;

    public PlayerPosition(String playerId, double x, double y) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
    }

    public String getPlayerId() {
        return playerId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    @Override
    public String toString() {
        return PREFIX + "{" + playerId + "," + x + "," + y + "}";
    }

    public static PlayerPosition parse(String data) {
        Pattern p = Pattern.compile(PREFIX + "\\{(.*)\\}");
        Matcher m = p.matcher(data);
        if (m.matches()) {
            if (m.groupCount() == 1) {
                String body = m.group(1);
                String[] parts = body.split(",");
                String playerId = parts[0];
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);

                return new PlayerPosition(playerId, x, y);
            }
        }
        return null;
    }

    public static boolean matches(String data) {
        return data.startsWith(PREFIX);
    }
}
