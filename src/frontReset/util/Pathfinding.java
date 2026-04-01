package frontReset.util;

import frontReset.*;

import battlecode.common.*;

/**
 * Pathfinding manager. In this game this class is unnecessary I pretty much only use bugnav.
 */
public class Pathfinding {

  public static void moveTo(MapLocation target) throws GameActionException {
      BugPath.moveTo(target);
  }

  /**
   * Finds the best move towards the target location.
   * 
   * @param loc The current position to move from
   * @return The direction to take to move towards the target 
   */
  public static Direction getGreedyMove(MapLocation loc) throws GameActionException { return getGreedyMove(loc, GoalManager.current().target); }

  /**
   * Finds the best move towards the `goal` location.
   * 
   * @param loc The current position to move from
   * @param goal The goal position to move towards
   * @return The direction to take to move towards the target 
   */
  public static Direction getGreedyMove(MapLocation loc, MapLocation goal) throws GameActionException { return getGreedyMove(loc, goal, false); }

  /**
   * Finds the closest legal move to the specified direction
   * @param loc The current position to move from
   * @param dir The goal direction to ty to match
   * @param checkCanMove Whether to check if the robot can move to a location
   * @return The direction to take to move towards the target 
   */
  public static Direction getGreedyMove(MapLocation loc, MapLocation goal, boolean checkCanMove) throws GameActionException { return getGreedyMove(loc, goal, checkCanMove, MovementManager.Mode.ANY); }

  /**
   * Finds the closest legal move to the specified direction
   * @param loc The current position to move from
   * @param dir The goal direction to ty to match
   * @param checkCanMove Whether to check if the robot can move to a location
   * @return The direction to take to move towards the target 
   */
  public static Direction getGreedyMove(MapLocation loc, Direction dir, boolean checkCanMove) throws GameActionException { return getGreedyMove(loc, dir, checkCanMove, MovementManager.Mode.ANY); }

  /**
   * Finds the closest legal move to the specified direction
   * @param loc The current position to move from
   * @param dir The goal direction to ty to match
   * @param checkCanMove Whether to check if the robot can move to a location
   * @param onlyAllyPaint Whether to only allow moves onto ally paint
   * @return The direction to take to move towards the target 
   */
  public static Direction getGreedyMove(MapLocation loc, MapLocation goal, boolean checkCanMove, MovementManager.Mode mode) throws GameActionException {
    // Try going towards it
    Direction l_dir = loc.directionTo(goal);
    MapLocation l_next = loc.add(l_dir);
    if (Robot.rc.onTheMap(l_next) && MapData.passable(l_next) && 
        (!checkCanMove || Robot.rc.canMove(l_dir)) &&
        (switch (mode) { 
          case MovementManager.Mode.ANY -> true;
          case MovementManager.Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(l_next).getPaint().isAlly(); 
          case MovementManager.Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(l_next).getPaint().isEnemy(); 
        })) { return l_dir; }
    
    // Figure out which direction is better
    Direction r_dir = l_dir.rotateRight();
    MapLocation r_next = loc.add(r_dir);
    l_dir = l_dir.rotateLeft();
    l_next = loc.add(l_dir);
    if (l_next.distanceSquaredTo(goal) < r_next.distanceSquaredTo(goal)) {
      // Try turning left
      if (Robot.rc.onTheMap(l_next) && MapData.passable(l_next) &&
          (!checkCanMove || Robot.rc.canMove(l_dir)) &&
          (switch (mode) { 
            case MovementManager.Mode.ANY -> true;
            case MovementManager.Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(l_next).getPaint().isAlly(); 
            case MovementManager.Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(l_next).getPaint().isEnemy(); 
          })) { return l_dir; }

      // Try turning right
      if (Robot.rc.onTheMap(r_next) && MapData.passable(r_next) &&
          (!checkCanMove || Robot.rc.canMove(r_dir)) &&
          (switch (mode) { 
            case MovementManager.Mode.ANY -> true;
            case MovementManager.Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(r_next).getPaint().isAlly(); 
            case MovementManager.Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(r_next).getPaint().isEnemy(); 
          })) { return r_dir; }
      
    } else {
      // Try turning right
      if (Robot.rc.onTheMap(r_next) && MapData.passable(r_next) &&
          (!checkCanMove || Robot.rc.canMove(r_dir)) &&
          (switch (mode) { 
            case MovementManager.Mode.ANY -> true;
            case MovementManager.Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(r_next).getPaint().isAlly(); 
            case MovementManager.Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(r_next).getPaint().isEnemy(); 
          })) { return r_dir; }

      // Try turning left
      if (Robot.rc.onTheMap(l_next) && MapData.passable(l_next) &&
          (!checkCanMove || Robot.rc.canMove(l_dir)) &&
          (switch (mode) { 
            case MovementManager.Mode.ANY -> true;
            case MovementManager.Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(l_next).getPaint().isAlly(); 
            case MovementManager.Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(l_next).getPaint().isEnemy(); 
          })) { return l_dir; }
    }
    return null;
  }

  /**
   * Finds the closest legal move to the specified direction
   * @param loc The current position to move from
   * @param dir The goal direction to ty to match
   * @param checkCanMove Whether to check if the robot can move to a location
   * @param onlyAllyPaint Whether to only allow moves onto ally paint
   * @return The direction to take to move towards the target 
   */
  public static Direction getGreedyMove(MapLocation loc, Direction dir, boolean checkCanMove, MovementManager.Mode mode) throws GameActionException {
    // Try going towards it
    MapLocation next = loc.add(dir);
    if (Robot.rc.onTheMap(next) && MapData.passable(next) && 
        (!checkCanMove || Robot.rc.canMove(dir)) &&
        (switch (mode) { 
          case MovementManager.Mode.ANY -> true;
          case MovementManager.Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(next).getPaint().isAlly(); 
          case MovementManager.Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(next).getPaint().isEnemy(); 
        })) { return dir; }
    
    // Try turning left
    Direction leftDir = dir.rotateLeft();
    next = loc.add(leftDir);
    if (Robot.rc.onTheMap(next) && MapData.passable(next) &&
        (!checkCanMove || Robot.rc.canMove(leftDir)) &&
        (switch (mode) { 
          case MovementManager.Mode.ANY -> true;
          case MovementManager.Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(next).getPaint().isAlly(); 
          case MovementManager.Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(next).getPaint().isEnemy(); 
        })) { return leftDir; }

    // Try turning right
    dir = dir.rotateRight();
    next = loc.add(dir);
    if (Robot.rc.onTheMap(next) && MapData.passable(next) &&
        (!checkCanMove || Robot.rc.canMove(dir)) &&
        (switch (mode) { 
          case MovementManager.Mode.ANY -> true;
          case MovementManager.Mode.ALLY_ONLY -> Robot.rc.senseMapInfo(next).getPaint().isAlly(); 
          case MovementManager.Mode.NO_ENEMY -> !Robot.rc.senseMapInfo(next).getPaint().isEnemy(); 
        })) { return dir; }

    // Give up if none work
    return null;
  }

}