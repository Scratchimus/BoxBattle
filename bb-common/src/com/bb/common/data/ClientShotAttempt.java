package com.bb.common.data;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sent to the server to indicate the client is trying to shoot
 */
public class ClientShotAttempt {
    private static final String PREFIX = "ClientShotAttempt";
    private Point aimPt;


    public ClientShotAttempt(Point aimPt) {
        this.aimPt = aimPt;
    }


    public Point getAimPt() {
        return aimPt;
    }

    public String toString() {
        return PREFIX + "{" + aimPt.x + "," + aimPt.y + "}";
    }
    public static boolean matches(String data) {
        return data.startsWith(PREFIX);
    }
    public static ClientShotAttempt parse(String data) {
        Pattern p = Pattern.compile(PREFIX + "\\{(.*)\\}");
        Matcher m = p.matcher(data);
        if (m.matches()) {
            if (m.groupCount() == 1) {
                String body = m.group(1);
                String[] parts = body.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                return new ClientShotAttempt(new Point(x, y));
            }
        }
        return null;
    }
}
