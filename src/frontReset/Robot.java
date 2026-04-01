package frontReset;

import java.util.*;

import battlecode.common.*;
import frontReset.util.*;

public abstract class Robot {
  
  public static RobotController rc;
  
  public static boolean DEBUG;
  
  public static Random rng;

  public static Team TEAM;
  public static Team OPPONENT;
  
  public static int CREATED_ROUND;

  public static int turnNum = 1;

  public static MapLocation spawnTower;

  public static String emptyTowers = "";
  
  public Robot(RobotController rc_) throws GameActionException {
    rc = rc_;

    DEBUG = false;

    rng = new Random(rc.getID());
    
    TEAM = rc.getTeam();
    OPPONENT = TEAM.opponent();

    CREATED_ROUND = rc.getRoundNum();
    
    // THIS ORDER OF INITIALIZATION IS IMPORTANT
    MapData.init();
    MapData.updateAllVisible();
    spawnTower = MapData.closestFriendlyTower(); 
    if (spawnTower == null) { // If the tower suicides, might be a ruin now
      spawnTower = MapData.closestRuin();
    }
    rc.setIndicatorLine(rc.getLocation(), spawnTower, 0, 255, 255);
    GoalManager.init();
  };

  final public void run() throws GameActionException {
    if (Robot.rc.getRoundNum() % 100 == 8) {
      Robot.rc.setTimelineMarker("Reset fronts", 255, 0, 0);
      MapData.resetFronts();
    }
    if (turnNum == 2) {
      BugPath.init();
    }
    MovementManager.update();
    if (turnNum != 1) {
      MapData.updateVisibleRuins();
    }
    doMicro(); // Act based on immediate surroundings
    doMacro(); // Secondarily act to achieve big picture goal
    MapData.lookForFrontsToAdd();
    MapData.analyzeExistingFronts();
    // At the end of the turn, communicate fronts
    for (RobotInfo info : rc.senseNearbyRobots(GameConstants.MESSAGE_RADIUS_SQUARED, TEAM)) {
      if (info.getType().isTowerType() == Robot.rc.getType().isTowerType()) { continue; }
      if (!rc.canSendMessage(info.getLocation())) { continue; }
      // If we are a tower, only send active messages
      if (Robot.rc.getRoundNum() % 100 != 7) { // Don't send any front messages every 100 rounds
        if (Robot.rc.getType().isTowerType()) {
          Communication.resetSentFronts(); // We want to send all fronts to all robots
          while (Communication.trySendMessage(Communication.createFrontsMessage(), info.getLocation())) {}
        } else if (Communication.trySendMessage(Communication.createFrontsMessage(), info.getLocation())) {
          break;
        }
      }
      if (Clock.getBytecodesLeft() < 200) { break; }
    }
    ++turnNum;
  }

  /**
   * Handles the micro game of the robot.
   */
  protected abstract void doMicro() throws GameActionException;

  /**
   * Handles the macro game of the robot.
   */
  protected abstract void doMacro() throws GameActionException;
}