package frontReset.util;

import battlecode.common.*;
import frontReset.*;

public class Micro {

  static private MicroInfo[] infos;

  static private int visibleFriendlySoldiers;
  static private int currentDistanceToEnemyTower;

  static public Direction getMove() throws GameActionException {
    int bytecode = Clock.getBytecodeNum();
    if (!Robot.rc.isMovementReady()) { 
      if (Robot.DEBUG) { System.out.println("Can't move, defaulting to center"); }
      return Direction.CENTER;
    }
    
    // Reset variables
    infos = new MicroInfo[9];
    infos[0] = new MicroInfo(Direction.NORTH);
    infos[1] = new MicroInfo(Direction.NORTHEAST);
    infos[2] = new MicroInfo(Direction.EAST);
    infos[3] = new MicroInfo(Direction.SOUTHEAST);
    infos[4] = new MicroInfo(Direction.SOUTH);
    infos[5] = new MicroInfo(Direction.SOUTHWEST);
    infos[6] = new MicroInfo(Direction.WEST);
    infos[7] = new MicroInfo(Direction.NORTHWEST);
    infos[8] = new MicroInfo(Direction.CENTER);
    
    visibleFriendlySoldiers = 0;
    currentDistanceToEnemyTower = MapData.MAX_DISTANCE_SQ;
    
    if (Robot.DEBUG) { System.out.println("Micro Bytecode - Init: " + (Clock.getBytecodeNum() - bytecode)); }

    // Check nearby robot info
    for (RobotInfo info : Robot.rc.senseNearbyRobots()) {
      if (info.getTeam() == Robot.TEAM && info.getType() == UnitType.SOLDIER) {
        ++visibleFriendlySoldiers;
      }
      if (info.getTeam() == Robot.OPPONENT && info.getType().isTowerType() && 
          Robot.rc.getLocation().isWithinDistanceSquared(info.getLocation(), currentDistanceToEnemyTower)) {
        currentDistanceToEnemyTower = Robot.rc.getLocation().distanceSquaredTo(info.getLocation());
      }
      infos[0].updateRobotInfo(info);
      infos[1].updateRobotInfo(info);
      infos[2].updateRobotInfo(info);
      infos[3].updateRobotInfo(info);
      infos[4].updateRobotInfo(info);
      infos[5].updateRobotInfo(info);
      infos[6].updateRobotInfo(info);
      infos[7].updateRobotInfo(info);
      infos[8].updateRobotInfo(info);
    }

    if (Robot.DEBUG) { System.out.println("Micro Bytecode - RobotInfo: " + (Clock.getBytecodeNum() - bytecode)); }

    // Check nearby map info
    if (GoalManager.current().type == Goal.Type.CAPTURE_RUIN || GoalManager.current().type == Goal.Type.CAPTURE_SRP) {
      if (Robot.rc.getType() == UnitType.MOPPER) {
        for (MapInfo info : Robot.rc.senseNearbyMapInfos(8)) {
          if (Robot.DEBUG) { Robot.rc.setIndicatorDot(info.getMapLocation(), 0, 0, 0); }
          if (info.getPaint().isEnemy()) {
            boolean allTrue = true;
            MapLocation loc = info.getMapLocation();
            allTrue = infos[0].updateEnemyPaint(loc) && allTrue;
            allTrue = infos[1].updateEnemyPaint(loc) && allTrue;
            allTrue = infos[2].updateEnemyPaint(loc) && allTrue;
            allTrue = infos[3].updateEnemyPaint(loc) && allTrue;
            allTrue = infos[4].updateEnemyPaint(loc) && allTrue;
            allTrue = infos[5].updateEnemyPaint(loc) && allTrue;
            allTrue = infos[6].updateEnemyPaint(loc) && allTrue;
            allTrue = infos[7].updateEnemyPaint(loc) && allTrue;
            allTrue = infos[8].updateEnemyPaint(loc) && allTrue;
            if (allTrue) { break; }
          }
        }
      } else if (Robot.rc.getType() == UnitType.SOLDIER) {
        for (MapInfo info : Robot.rc.senseNearbyMapInfos(12)) {
          if (info.getPaint() == PaintType.EMPTY) {
            boolean allTrue = true;
            MapLocation loc = info.getMapLocation();
            allTrue = infos[0].updateEmptyPaint(loc) && allTrue;
            allTrue = infos[1].updateEmptyPaint(loc) && allTrue;
            allTrue = infos[2].updateEmptyPaint(loc) && allTrue;
            allTrue = infos[3].updateEmptyPaint(loc) && allTrue;
            allTrue = infos[4].updateEmptyPaint(loc) && allTrue;
            allTrue = infos[5].updateEmptyPaint(loc) && allTrue;
            allTrue = infos[6].updateEmptyPaint(loc) && allTrue;
            allTrue = infos[7].updateEmptyPaint(loc) && allTrue;
            allTrue = infos[8].updateEmptyPaint(loc) && allTrue;
            if (allTrue) { break; }
          }
        }
      }
    }

    if (Robot.DEBUG) { System.out.println("Micro Bytecode - MapInfo: " + (Clock.getBytecodeNum() - bytecode)); }

    // Choose the best square
    MicroInfo bestMicro = infos[8];

    if (Robot.DEBUG) { System.out.println(" >>>>> Choosing best move from " + bestMicro.location); }

    if (infos[0].isBetterThan(bestMicro)) { bestMicro = infos[0]; }
    if (infos[1].isBetterThan(bestMicro)) { bestMicro = infos[1]; }
    if (infos[2].isBetterThan(bestMicro)) { bestMicro = infos[2]; }
    if (infos[3].isBetterThan(bestMicro)) { bestMicro = infos[3]; }
    if (infos[4].isBetterThan(bestMicro)) { bestMicro = infos[4]; }
    if (infos[5].isBetterThan(bestMicro)) { bestMicro = infos[5]; }
    if (infos[6].isBetterThan(bestMicro)) { bestMicro = infos[6]; }
    if (infos[7].isBetterThan(bestMicro)) { bestMicro = infos[7]; }

    if (Robot.DEBUG) { System.out.println(" >>>>> CHOSE " + bestMicro.dir); }

    if (Robot.DEBUG) { System.out.println("Micro Bytecode - Choice: " + (Clock.getBytecodeNum() - bytecode)); }

    Robot.rc.setIndicatorLine(Robot.rc.getLocation(), Robot.rc.getLocation().add(bestMicro.dir), 0, 0, 0);

    return bestMicro.dir;
  }

  private static class MicroInfo {
    // Initialized on construction
    Direction dir;
    MapLocation location;
    boolean canMove;
    PaintType paint;
    int distanceToGoal;
    
    // Set by updateRobotInfo
    int enemyTowersCanAttack = 0;
    int enemyMoppersCanAttack = 0;
    int adjacentFriendlyUnits = 0;
    int distanceToFriendlyUnit = MapData.MAX_DISTANCE_SQ;
    int distanceToEnemyTower = MapData.MAX_DISTANCE_SQ;
    
    // Set by updateMapInfo - Only when goal is CAPTURE_RUIN
    boolean emptyPaintInRange = false; // Only when soldier type
    boolean enemyPaintInRange = false; // Only when mopper type

    private MicroInfo(Direction dir) throws GameActionException {
      this.dir = dir;
      this.location = Robot.rc.getLocation().add(dir);
      this.canMove = (dir == Direction.CENTER || Robot.rc.canMove(dir));
      if (!canMove) { return; }
      this.paint = Robot.rc.senseMapInfo(location).getPaint();
      this.distanceToGoal = location.distanceSquaredTo(GoalManager.current().target);
    }

    private void updateRobotInfo(RobotInfo info) {
      if (!canMove) { return; }

      if (info.getTeam() == Robot.TEAM) {
        int distance = location.distanceSquaredTo(info.getLocation());
        if (distance < distanceToFriendlyUnit) { distanceToFriendlyUnit = distance; }
        if (distance <= 2) { ++this.adjacentFriendlyUnits; }
      } else { // Enemy
        if (info.getType().isTowerType()) {
          if (location.isWithinDistanceSquared(info.getLocation(), info.getType().actionRadiusSquared)) {
            ++this.enemyTowersCanAttack;
          }
          if (location.isWithinDistanceSquared(info.getLocation(), distanceToEnemyTower)) {
            distanceToEnemyTower = location.distanceSquaredTo(info.getLocation());
          }
        } else { // Enemy Robot
          if (info.getType() == UnitType.MOPPER && location.isWithinDistanceSquared(info.getLocation(), 13)) {
            ++enemyMoppersCanAttack;
          }
        }
      }
    }

    /**
     * Returns true if the enemy paint is in range (or we don't care about the value)
     */
    private boolean updateEnemyPaint(MapLocation loc) throws GameActionException {
      if (!canMove || enemyPaintInRange) { return true; }

      enemyPaintInRange = location.isWithinDistanceSquared(loc, Robot.rc.getType().actionRadiusSquared);
      return enemyPaintInRange;
    }

    /**
     * Returns true if the empty paint is in range (or we don't care about the value)
     */
    private boolean updateEmptyPaint(MapLocation loc) throws GameActionException {
      if (!canMove || emptyPaintInRange) { return true; }

      emptyPaintInRange = location.isWithinDistanceSquared(loc, Robot.rc.getType().actionRadiusSquared);
      return emptyPaintInRange;
    }

    private boolean isBetterThan(MicroInfo other) throws GameActionException {
      /**
       * BY DEFAULT, WE ASSUME OTHER IS BETTER BASED ON HOW IT IS CALLED IN `Micro.getMove()`
       * 
       * Otherwise we do unnecessary swapping
       */

      if (Robot.DEBUG) { System.out.println(" > Comparing " + this.dir + " and " + other.dir); }

      if (Robot.DEBUG) { System.out.println("Checking can move..."); }
      
      // Need to be able to move to the square
      if (!this.canMove) { return false; }
      if (!other.canMove) { return true; }
      
      if (Robot.DEBUG) { System.out.println("Checking adjacent to tower..."); }

      // Don't go adjacent to a tower
      if (other.distanceToEnemyTower < 3) { return true; }
      if (this.distanceToEnemyTower < 3) { return false; }

      // Soldier Attacking tower, want to be in range of a single tower
      if (Robot.rc.getType() == UnitType.SOLDIER &&  // We are a soldier
          GoalManager.current().type == Goal.Type.FIGHT_TOWER &&  // We are fighting a tower
          Robot.rc.isActionReady() && // We are ready to attack
          (currentDistanceToEnemyTower <= UnitType.SOLDIER.actionRadiusSquared || // Already in range
           (Robot.rc.getRoundNum() % 2 == 0 && (visibleFriendlySoldiers > 1 || Robot.rc.getHealth() > 30))) // or an even round number and with friend
         ) {
        /*
           IMPORTANT: This is more complicated if action radius is different

           Since this is soldier, we don't have to worry about it
         */
        // If we are in range and can attack, DON'T MOVE TO ANOTHER SPOT IN RANGE
        if (currentDistanceToEnemyTower <= UnitType.SOLDIER.actionRadiusSquared) {
          if (Robot.DEBUG) { System.out.println("Checking center in enemy attack range..."); }
          if (this.dir == Direction.CENTER) { return true; }
          if (other.dir == Direction.CENTER) { return false; }
        }
        if (Robot.DEBUG) { System.out.println("Checking single tower attack..."); }
        if (this.enemyTowersCanAttack == 0 && other.enemyTowersCanAttack == 1) { return false; } 
        if (other.enemyTowersCanAttack == 0 && this.enemyTowersCanAttack == 1) { return true; }
      } 

      // In general we want to avoid towers
      if (Robot.DEBUG) { System.out.println("Checking enemy tower attacks..."); }
      if (this.enemyTowersCanAttack < other.enemyTowersCanAttack) { return true; }
      if (other.enemyTowersCanAttack < this.enemyTowersCanAttack) { return false; }

      // If we are attacking but still out of range, we want to get close to the tower
      if (Robot.rc.getType() == UnitType.SOLDIER && // We are a soldier
          GoalManager.current().type == Goal.Type.FIGHT_TOWER && // We are fighting a tower
          enemyTowersCanAttack == 0 // Neither choice can be attacked
         ) {
        if (Robot.DEBUG) { System.out.println("Checking distance to enemy tower (want close if soldier attack)..."); }
        if (this.distanceToEnemyTower < other.distanceToEnemyTower - 1) { return true; }
        if (other.distanceToEnemyTower < this.distanceToEnemyTower - 1) { return false; }
      }

      // // Avoid substantial differences in paint penalty
      if (Robot.DEBUG) { System.out.println("Checking substantial paint penalties..."); }
      int thisPenalty = getPaintPenalty();
      int otherPenalty = other.getPaintPenalty();
      if (thisPenalty < otherPenalty - 4) { return true; }
      if (otherPenalty < thisPenalty - 4) { return false; }

      // If soldier / mopper, prefer empty / enemy paint in range
      if (Robot.DEBUG) { System.out.println("Checking paint in range..."); }
      if (Robot.rc.getType() == UnitType.SOLDIER) {
        if (this.emptyPaintInRange && !other.emptyPaintInRange) { return true; }
        if (other.emptyPaintInRange && !this.emptyPaintInRange) { return false; }
      } else if (Robot.rc.getType() == UnitType.MOPPER) {
        if (Robot.DEBUG) { System.out.println("Enemy Paint: " + this.enemyPaintInRange + " " + other.enemyPaintInRange); }
        if (this.enemyPaintInRange && !other.enemyPaintInRange) { return true; }
        if (other.enemyPaintInRange && !this.enemyPaintInRange) { return false; }
      }      
      
      // Choose whatever is closest to your target
      if (Robot.DEBUG) { System.out.println("Checking distance to goal..."); }
      if (this.distanceToGoal < other.distanceToGoal) { return true; }
      if (other.distanceToGoal < this.distanceToGoal) { return false; }
      
      // Avoid Moppers
      if (Robot.DEBUG) { System.out.println("Checking enemy mopper attacks..."); }
      if (this.enemyMoppersCanAttack < other.enemyMoppersCanAttack) { return true; }
      if (other.enemyMoppersCanAttack < this.enemyMoppersCanAttack) { return false; }
      
      // Minimize paint penalty
      if (Robot.DEBUG) { System.out.println("Checking paint penalties..."); }
      if (thisPenalty < otherPenalty) { return true; }
      if (otherPenalty < thisPenalty) { return false; }
      
      // Prefer to give an extra buffer to your friends so they can move
      if (this.distanceToFriendlyUnit <= 8 && other.distanceToFriendlyUnit > 8) { return false; }
      if (other.distanceToFriendlyUnit <= 8 && this.distanceToFriendlyUnit > 8) { return true; }
      
      // Default to the current one
      if (Robot.DEBUG) { System.out.println("Defaulting to current choice..."); }
      return false;
    }

    /**
     * Robots lose 1 paint on neutral, 2 on enemy, plus 1 for each acjacent friend
     */
    private int getPaintPenalty() {
      return (this.paint.isAlly() ? 0 : 1) + (this.paint.isEnemy() ? 1 : 0) + adjacentFriendlyUnits;
    }
  };
};
