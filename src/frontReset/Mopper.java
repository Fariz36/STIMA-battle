package frontReset;

import javax.sql.*;

import battlecode.common.*;
import frontReset.util.*;

public final class Mopper extends Robot {

  private MapLocation completedRuinJob = null;

  private boolean bugPathAroundWall = false;
  
  public Mopper(RobotController rc_) throws GameActionException {
    super(rc_);

    MovementManager.setMode(MovementManager.Mode.NO_ENEMY);
  }

  protected void doMicro() throws GameActionException {
    rc.setIndicatorString("GOAL - " + GoalManager.current());

    // TODO: TAKE THIS OUT AND DEAL WITH CROWDING lol
    // If surrounded, kill yourself
    if (rc.senseNearbyRobots(9, TEAM).length > 24 /* Out of 28 */) {
      rc.disintegrate();
      return;
    }

    // UPDATE GOAL ------------------------------------------------------------

    // Check for a suicide message, if received this is priority number 1
    // Read incoming messages
    for (Message m : rc.readMessages(rc.getRoundNum()-1)) {
      switch (m.getBytes() & Communication.MESSAGE_TYPE_BITMASK) {
        case Communication.SUICIDE: // INTENTIONAL FALLTHROUGH
        case Communication.REQUEST_MOPPER:
          GoalManager.replaceTopGoal(Goal.Type.CAPTURE_RUIN, Communication.getCoordinates(m.getBytes()));
          break;
        case Communication.SYMMETRY_KNOWLEDGE:
          MapData.incorporateSymmetryInfo(Communication.readSymmetryValue(m.getBytes()));
        case Communication.FRONT:
          if (rc.getRoundNum() % 100 != 8) {
            Communication.updateFronts(m.getBytes());
          }
          break;
        default:
          System.out.println("RECEIVED UNKNOWN MESSAGE: " + m);
      }
    }

    // Look for nearby ruins if we aren't already capturing one
    if (GoalManager.current().type.v < Goal.Type.CAPTURE_RUIN.v) {
      MapLocation[] ruins = rc.senseNearbyRuins(-1);
      for (MapLocation ruin : ruins) {
        if(ruin.equals(completedRuinJob)) continue; //completedRuinJob is the last ruin that we know we don't need a mopper for 
        RobotInfo info = rc.senseRobotAtLocation(ruin);
        if (info == null) { // Unclaimed Ruin
          GoalManager.pushGoal(Goal.Type.CAPTURE_RUIN, ruin);
          break;
        }
      }
    }

    // Look for SRP if we are a lower priority
    if (GoalManager.current().type.v < Goal.Type.CAPTURE_SRP.v && MapData.foundSRP != null && !MapData.foundSRP.equals(completedRuinJob)) {
      GoalManager.pushGoal(Goal.Type.CAPTURE_SRP, MapData.foundSRP);
    }

    // DO THINGS --------------------------------------------------------------

    // Can't do anything, no point
    if (!rc.isMovementReady() && !rc.isActionReady()) { return; }

    // Can't move, might as well try and mop
    if (!rc.isMovementReady() && rc.isActionReady()) { Painter.mop(); return; }

    // Transfer paint if possible
    // if (rc.isActionReady() && rc.getPaint() > REFILL_PAINT_THRESHOLD * rc.getType().paintCapacity / 100) {
    //   for (RobotInfo robot : rc.senseNearbyRobots(GameConstants.PAINT_TRANSFER_RADIUS_SQUARED, rc.getTeam())) {
    //     // Don't transfer to other moppers or towers, or if they have enough paint
    //     if (robot.type == UnitType.MOPPER || robot.getType().isTowerType() || robot.getPaintAmount() > REFILL_PAINT_THRESHOLD) { continue; }

    //     // Transfer the max possible
    //     int transfer_amt = Math.min(robot.getType().paintCapacity - robot.getPaintAmount(), rc.getPaint() - REFILL_PAINT_THRESHOLD);
    //     if (rc.canTransferPaint(robot.getLocation(), transfer_amt)) {
    //       rc.transferPaint(robot.getLocation(), transfer_amt);
    //       break;
    //     }
    //   }
    // }

    switch (GoalManager.current().type) {
      case CAPTURE_RUIN:
        Painter.emergencyBugNav();
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, GoalManager.current().target)) {
          rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, GoalManager.current().target);
          MapData.updateData(Robot.rc.senseMapInfo(GoalManager.current().target));
          GoalManager.popGoal();
          break;
        }
        if (Painter.mopCaptureRuin()) {   //TODO: make sure moppers don't ditch complete patterns that need rebuilding
          if(!rc.canSenseRobotAtLocation(GoalManager.current().target)){
            //ruin has no more enemy paint around it, start exploring
            completedRuinJob = GoalManager.current().target;
            GoalManager.popGoal();
          }else{
            RobotInfo tower = rc.senseRobotAtLocation(GoalManager.current().target);
            //mopper built the tower, refill from the tower
            if (tower.getPaintAmount() > UnitType.MOPPER.paintCost) {
              int paintAmount = rc.getType().paintCapacity - rc.getPaint();
              if (tower.getPaintAmount() - UnitType.MOPPER.paintCost < paintAmount) { paintAmount = tower.getPaintAmount() - UnitType.MOPPER.paintCost; }
              if (rc.canTransferPaint(GoalManager.current().target, -paintAmount)) {
                rc.transferPaint(GoalManager.current().target, -paintAmount);
                emptyTowers = "";
                GoalManager.popGoal();
              }
            }
          }
        }
        break;
      case CAPTURE_SRP:
        if (Painter.mopCaptureSRP()) {
          completedRuinJob = GoalManager.current().target;
          GoalManager.popGoal();
        }
        break;
      case EXPLORE:
        MapLocation front = MapData.getNearestFront();
        if(front!=null && GoalManager.current().target!=front){
          GoalManager.replaceTopGoal(Goal.Type.EXPLORE,front);
          rc.setIndicatorDot(front,255,255,0);
        }else if (rc.getLocation().isWithinDistanceSquared(GoalManager.current().target, GameConstants.VISION_RADIUS_SQUARED)) {
          MapData.updateData(rc.senseMapInfo(GoalManager.current().target));
          GoalManager.replaceTopGoal(Goal.Type.EXPLORE,MapData.getExploreTarget());
        }
        Painter.mop();
        break;
      default: break;
    }

    //mop swing
    if (rc.isActionReady()){
      Direction mopSwingDirection = pickMopSwingDirection();
      if(mopSwingDirection!=Direction.CENTER && rc.canMopSwing(mopSwingDirection)) rc.mopSwing(mopSwingDirection);
    }
  }

  protected void doMacro() throws GameActionException {
    Pathfinding.moveTo(GoalManager.current().target); //note that Mopper is currently set to NO_ENEMY by the constructor, and is never changed
    Painter.mop();
  }

  private Direction pickMopSwingDirection() throws GameActionException{
    RobotInfo[] enemyRobotsWithinMopSwingRange = rc.senseNearbyRobots(8,rc.getTeam().opponent());
    if(enemyRobotsWithinMopSwingRange.length==0)  return Direction.CENTER;
    Direction[] cardinaDirections = Direction.cardinalDirections();

    int mostEnemiesHit = 0;
    Direction chosenDirection = Direction.CENTER;
    for(Direction d : cardinaDirections){
      int directionEnemiesHit = 0;
      for(RobotInfo enemy : enemyRobotsWithinMopSwingRange){
        Direction dirToEnemy = rc.getLocation().directionTo(enemy.getLocation());
        if(dirToEnemy.dx==d.dx || dirToEnemy.dy==d.dy) ++directionEnemiesHit;
      }
      if(directionEnemiesHit>mostEnemiesHit){
        mostEnemiesHit=directionEnemiesHit;
        chosenDirection=d;
      }
    }
    return chosenDirection;
  }
}