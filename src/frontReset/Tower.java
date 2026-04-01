package frontReset;

import frontReset.util.*;

import battlecode.common.*;

public final class Tower extends Robot {

  private final int TOWER_ATTACK_RADIUS;

  private final int MAX_BROADCAST = 5;

  private final MapLocation LOCATION;

  private final int PAINT_SUICIDE_THRESHOLD = UnitType.MOPPER.paintCost;

  public Tower(RobotController rc_) throws GameActionException {
    super(rc_);


    TOWER_ATTACK_RADIUS = rc.getType().actionRadiusSquared; // NOTE: This will have to change if upgrades modify radius

    LOCATION = rc.getLocation();
  }

  protected void doMicro() throws GameActionException {
    attackEnemies();

    // Read incoming messages
    for (Message m : rc.readMessages(rc.getRoundNum()-1)) {
      switch (m.getBytes() & Communication.MESSAGE_TYPE_BITMASK) {
        case Communication.REQUEST_MOPPER:
          trySpawn(UnitType.MOPPER, Communication.getCoordinates(m.getBytes()));
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

    // Spawn a unit specifically for suicide strat
    if(rc.getRoundNum()%6==rc.getID()%6){
      if (rc.getChips() > 5000 &&
        towerPatternComplete(UnitType.LEVEL_ONE_MONEY_TOWER)) {
        if (rc.getPaint() >= UnitType.SOLDIER.paintCost) {
          trySpawn(UnitType.SOLDIER);
        } else if (rc.getPaint() >= UnitType.MOPPER.paintCost) {
          trySpawn(UnitType.MOPPER);
        }
      }

      // Suicide for paint if worth it
      if (rc.getType().getBaseType() == rc.getType() && // DO NOT suicide level 2 towers (starter towers only?)
        (rc.getPaint() < PAINT_SUICIDE_THRESHOLD) && // No more paint
          rc.getChips() > 2000 && // Enough chips (we hope)
          towerPatternComplete(UnitType.LEVEL_ONE_MONEY_TOWER) &&
          Communication.trySendAllMessage( // We successfully sent the message to an adjacent bot
            Communication.addCoordinates(Communication.SUICIDE, LOCATION), rc.senseNearbyRobots(GameConstants.MESSAGE_RADIUS_SQUARED, TEAM))) {
        rc.setIndicatorString("Sent suicide message");
        rc.disintegrate();
        return;
      }
    }
  }
    

  protected void doMacro() throws GameActionException {
    spawnRobots();
    
    // Tell other towers about symmmetry
    if (MapData.symmetryKnown() && Robot.rc.getRoundNum() % 10 == 2) {
      Communication.tryBroadcastMessage(Communication.makeSymmetryMessage());
    }

    // Broadcast active fronts to other towers
    if (Robot.rc.getRoundNum() % 100 != 7) {  // Don't send any front messages every 100 rounds
      while (rc.canBroadcastMessage()) {
        if (!Communication.tryBroadcastMessage(Communication.createFrontsMessage(true))) { break; }
      }
    }
  }

  /**
   * Attacks all enemies with AoE, and targets the first enemy found which can
   * be killed in one shot. Otherwise, targets the lowest health enemy.
   * 
   * @throws GameActionException
   */
  private void attackEnemies() throws GameActionException {
    // Find nearby enemies
    RobotInfo[] enemies = rc.senseNearbyRobots(TOWER_ATTACK_RADIUS, OPPONENT);
    if (enemies.length == 0) { return; }
      
    // Perform AoE attack
    rc.attack(null);

    // Find weakest or one-shot enemy
    RobotInfo target = enemies[0];
    if (target.getHealth() > rc.getType().attackStrength) {
      for (RobotInfo enemy : enemies) {
        if (enemy.getHealth() < target.getHealth()) {
          target = enemy;
          if (target.getHealth() < rc.getType().attackStrength) {
            break;
          }
        }
      }
    }

    // Attack enemy
    if (rc.canAttack(target.getLocation())) {
      rc.attack(target.getLocation());
    }
  }

  /**
   * Handles the logic of spawning robots.
   * 
   * Each tower simply spawns a single soldier at the start of the game.
   * The location of the spawned soldier is in the closest available direction
   * to the middle of the map.
   * 
   * @throws GameActionException
   */
  private void spawnRobots() throws GameActionException {
    switch (rc.getRoundNum()) {
      case 1: spawnRound1(); return;
      case 2: spawnRound1(); return;
      default: break;
    }

    // If we see enemy paint and don't see a mopper, spawn one
    boolean seenMopper = false;
    for (RobotInfo info : rc.senseNearbyRobots(-1, TEAM)) {
      if (info.getType() == UnitType.MOPPER) { seenMopper = true; break; }
    }
    /*
    if (!seenMopper && rc.getRoundNum()>50) {
      for (MapInfo info : rc.senseNearbyMapInfos()) {
        if (info.getPaint().isEnemy()) {
          trySpawn(UnitType.MOPPER, info.getMapLocation());
          break;
        }
      }
    }
      */

    // Spawn more if we got hella chips
    if ((rc.getRoundNum()<50 &&rc.getChips() > rc.getType().moneyCost * 2) || (rc.getRoundNum()>=50 &&rc.getChips() >rc.getType().moneyCost+250)) {
      switch (rc.getRoundNum() % 4) {
        case 0: trySpawn(UnitType.MOPPER, MapData.MAP_CENTER); break;
        case 1: trySpawn(UnitType.SPLASHER, MapData.MAP_CENTER); break;
        default: trySpawn(UnitType.SOLDIER, MapData.MAP_CENTER); break;
      }
    }
  }

  /**
   * Finds the closest available spawn location to the provided target
   * @param target The location to get close to
   * @return The closest available location
   */
  private MapLocation getSpawnLoc(MapLocation target) throws GameActionException {
    // Check if we can build mopper, the cheapest unit
    if (target != null && rc.canBuildRobot(UnitType.MOPPER, target)) { return target; }

    MapLocation closest = null;
    int closest_dist = MapData.MAX_DISTANCE_SQ;
    for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(LOCATION, GameConstants.BUILD_ROBOT_RADIUS_SQUARED)) {
      if (!rc.canBuildRobot(UnitType.MOPPER, loc)) { continue; }
      if (target == null) { return loc; }
      int dist = loc.distanceSquaredTo(target);
      if (dist < closest_dist) {
        closest = loc;
        closest_dist = dist;
        if (closest_dist < 3) { break; }
      }
    }

    return closest;
  }

  private MapLocation trySpawn(UnitType type) throws GameActionException { return trySpawn(type, null); }

  /**
   * Tries to spawn a robot at the closest available location to the target
   * @param type The type of the robot to spawn
   * @param target The location to get close to
   * @return The location the robot was spawned at, if successful
   * @throws GameActionException
   */
  private MapLocation trySpawn(UnitType type, MapLocation target) throws GameActionException {
    MapLocation loc = getSpawnLoc(target);
    if (loc != null && rc.canBuildRobot(type, loc)) {
      rc.buildRobot(type, loc);
      // If we know symmetry, tell the robot on spawn
      if (MapData.symmetryKnown()) {
        Communication.trySendMessage(Communication.makeSymmetryMessage(), loc);
      }
      return loc;
    }
    return null;
  }

  /**
   * Handles robot spawning for round 1
   * 
   * @throws GameActionException
   */
  private void spawnRound1() throws GameActionException {
    trySpawn(UnitType.SOLDIER, MapData.MAP_CENTER);
  }

  /**
   * Handles robot spawning for round 2
   * 
   * @throws GameActionException
   */
  private void spawnRound2() throws GameActionException {
    trySpawn(UnitType.MOPPER, MapData.MAP_CENTER);
  }

  private boolean towerPatternComplete(UnitType type) throws GameActionException {
    if (!type.isTowerType()) { return false; }
    boolean[][] pattern = rc.getTowerPattern(type);

    int x = LOCATION.x - (GameConstants.PATTERN_SIZE / 2);
    int y = LOCATION.y - (GameConstants.PATTERN_SIZE / 2);

    for (MapInfo tile : rc.senseNearbyMapInfos(rc.getLocation(), 8)){
      MapLocation loc = tile.getMapLocation();
      if (loc.equals(LOCATION)) { continue; }
      PaintType paint = tile.getPaint();
      if (!paint.isAlly() || pattern[loc.x - x][loc.y - y] != paint.isSecondary()) { return false; }
    }

    return true;
  }
}