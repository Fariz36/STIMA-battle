package frontReset.util;

import frontReset.*;
import battlecode.common.*;

/**
 * Movement manager. This class stores info about adjacent tiles. There are three possible cases: PASSAABLE, IMPASSABLE, or FILL+PASSABLE.
 * When bugnaving, Fill+Passable is treated as passable if there are enough resources (this class also has its custom "move" method, which attempts to fill if necessary).
 */
public class MovementManager {
    public static final int PASSABLE = 0;
    public static final int IMPASSABLE = 1;

    public static enum Mode {
        ANY,
        NO_ENEMY,
        ALLY_ONLY
    }

    private static Mode mode = Mode.ANY;

    static int[] movementTypes;

    public static void setMode(Mode mode_){
        mode=mode_;
    }

    public static void update() throws GameActionException {
        movementTypes = new int[9];
        MapLocation currentLoc = Robot.rc.getLocation();
        for (Direction dir : Direction.DIRECTION_ORDER){
            MapLocation newLoc = currentLoc.add(dir);
            if (Robot.rc.canMove(dir) && switch (mode) { 
                case Mode.ANY -> true;
                case Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(newLoc).getPaint().isAlly(); 
                case Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(newLoc).getPaint().isEnemy(); 
            }){
                movementTypes[dir.getDirectionOrderNum()] = PASSABLE;
            }else {
                movementTypes[dir.getDirectionOrderNum()] = IMPASSABLE;
            }
        }
        movementTypes[Direction.CENTER.getDirectionOrderNum()] = PASSABLE;
    }

    public static boolean canMove(Direction dir){
        return movementTypes[dir.getDirectionOrderNum()] != IMPASSABLE;
    }

    public static void move(Direction dir) throws GameActionException {
        if (dir == null || dir == Direction.CENTER) return;
        int m = movementTypes[dir.getDirectionOrderNum()];
        if (m == PASSABLE) {
            Robot.rc.move(dir);
            MapData.updateNewlyVisible(dir);
        }
    }
}
