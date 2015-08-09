package com.bb.common.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jake on 8/8/15.
 */
public class TimingPacket {
    private static final String PREFIX = "TimingPacket";
    private long initiatedTime;
    private long serverResponseTime;
    private long returnTime;

    public TimingPacket() {
        initiatedTime = System.currentTimeMillis();
    }

    public TimingPacket(long initiatedTime, long serverResponseTime, long returnTime) {
        this.initiatedTime = initiatedTime;
        this.serverResponseTime = serverResponseTime;
        this.returnTime = returnTime;
    }

    public void recordResponseTime() {
        serverResponseTime = System.currentTimeMillis();
    }
    public void recordReturnTime() {
        returnTime = System.currentTimeMillis();
    }

    public long getInitiatedTime() {
        return initiatedTime;
    }

    public long getServerResponseTime() {
        return serverResponseTime;
    }

    public long getReturnTime() {
        return returnTime;
    }

    public static boolean matches(String data) {
        return data.startsWith(PREFIX);
    }
    public String toString() {
        return PREFIX + "{" + initiatedTime + "," + serverResponseTime + "," + returnTime + "}";
    }
    public static TimingPacket parse(String data) {
        Pattern p = Pattern.compile(PREFIX + "\\{(.*)\\}");
        Matcher m = p.matcher(data);
        if (m.matches()) {
            if (m.groupCount() == 1) {
                String body = m.group(1);
                String[] parts = body.split(",");
                long it = Long.parseLong(parts[0]);
                long sr = Long.parseLong(parts[1]);
                long rt = Long.parseLong(parts[2]);
                return new TimingPacket(it, sr, rt);
            }
        }
        return null;
    }

}
