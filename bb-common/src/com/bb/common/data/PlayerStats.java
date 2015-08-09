package com.bb.common.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jake on 7/25/15.
 */
public class PlayerStats {
    public static final java.lang.String PREFIX = "PlayerStats";
    private String playerId;
    private double x, y;
    private int health;
    private boolean bot;

    public PlayerStats(String playerId, double x, double y, int health, boolean bot) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.health = health;
        this.bot = bot;
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

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }

    public boolean isBot() {
        return bot;
    }

    @Override
    public String toString() {
        return PREFIX + "{" + playerId + "," + x + "," + y + "," + health + "," + bot + "}";
    }

    public static PlayerStats parse(String data) {
        Pattern p = Pattern.compile(PREFIX + "\\{(.*)\\}");
        Matcher m = p.matcher(data);
        if (m.matches()) {
            if (m.groupCount() == 1) {
                String body = m.group(1);
                String[] parts = body.split(",");
                String playerId = parts[0];
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                int health = Integer.parseInt(parts[3]);
                boolean bot = Boolean.parseBoolean(parts[4]);

                return new PlayerStats(playerId, x, y, health, bot);
            }
        }
        return null;
    }

    public static boolean matches(String data) {
        return data.startsWith(PREFIX);
    }
}
