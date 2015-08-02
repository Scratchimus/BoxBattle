package com.bb.common.data;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jake on 7/26/15.
 */
public class ShotFired {
    private static final String PREFIX = "ShotFired";

    private static int NEXT_ID = 0;

    private transient long createTime;
    private int id;
    private boolean active;
    private Point origin;
    private Point target;

    // For server use
    public ShotFired(Point origin, Point target) {
        id = NEXT_ID++;
        createTime = System.currentTimeMillis();
        this.active = true;
        this.origin = origin;
        this.target = target;
    }
    // For parse() use
    private ShotFired(int id, boolean active, Point origin, Point target) {
        createTime = System.currentTimeMillis();
        this.id = id;
        this.active = active;
        this.origin = origin;
        this.target = target;
    }

    public int getId() {
        return id;
    }

    public Point getOrigin() {
        return origin;
    }

    public Point getTarget() {
        return target;
    }

    public boolean expired() {
        return System.currentTimeMillis() - createTime > 500;
    }

    public static boolean matches(String data) {
        return data.startsWith(PREFIX);
    }
    public String toString() {
        return PREFIX + "{" + id + "," + origin.x + "," + origin.y + "," + target.x + "," + target.y + "," + active + "}";
    }
    public static ShotFired parse(String data) {
        Pattern p = Pattern.compile(PREFIX + "\\{(.*)\\}");
        Matcher m = p.matcher(data);
        if (m.matches()) {
            if (m.groupCount() == 1) {
                String body = m.group(1);
                String[] parts = body.split(",");
                int id = Integer.parseInt(parts[1]);
                Point origin = new Point(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                Point target = new Point(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                boolean active = Boolean.parseBoolean(parts[5]);
                return new ShotFired(id, active, origin, target);
            }
        }
        return null;
    }
}
