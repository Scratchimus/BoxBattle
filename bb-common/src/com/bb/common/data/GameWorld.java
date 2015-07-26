package com.bb.common.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jake on 7/26/15.
 */
public class GameWorld {
    private int size;
    private TerrainType[][] grid;
    private static final String PREFIX = "GameWorld";

    public GameWorld(int size) {
        this.size = size;
        grid = new TerrainType[size][size];
        for (int ii = 0; ii < size; ii++) {
            for (int jj = 0; jj < size; jj++) {
                grid[ii][jj] = TerrainType.OPEN;
            }
        }
    }

    public void populateRandomWalls() {
        for (int ii=0; ii<size; ii++) {
            int xx = (int)(Math.random() * size);
            int yy = (int)(Math.random() * size);
            grid[xx][yy] = TerrainType.WALL;
        }
    }

    public int getSize() {
        return size;
    }

    public TerrainType get(int x, int y) {
        return grid[x][y];
    }

    public String toString() {
        StringBuilder bld = new StringBuilder(PREFIX);

        bld.append("{");
        bld.append(size).append(",");
        for (int ii = 0; ii < size; ii++) {
            for (int jj = 0; jj < size; jj++) {
                bld.append(grid[ii][jj]).append(",");
            }
        }
        bld.append("}");
        return bld.toString();
    }

    public static boolean matches(String data) {
        return data.startsWith(PREFIX);
    }

    public static GameWorld parse(String data) {
        Pattern p = Pattern.compile(PREFIX + "\\{(.*)\\}");
        Matcher m = p.matcher(data);
        if (m.matches()) {
            if (m.groupCount() == 1) {
                String body = m.group(1);
                String[] parts = body.split(",");

                int size = Integer.parseInt(parts[0]);
                int ii=0;
                int jj=0;
                GameWorld ret = new GameWorld(size);
                for (int idx=1; idx< parts.length; idx++) {
                    TerrainType type = TerrainType.valueOf(parts[idx]);
                    ret.grid[ii][jj] = type;

                    ii++;
                    if (ii >= size) {
                        ii = 0;
                        jj++;
                    }
                }

                return ret;
            }
        }
        return null;
    }
}
