package frontResetLite;

import frontResetLite.util.*;

import battlecode.common.*;

public final class Tower extends Robot {

  private final int TOWER_ATTACK_RADIUS;

  private final MapLocation LOCATION;

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
        case Communication.SYMMETRY_KNOWLEDGE:
          MapData.incorporateSymmetryInfo(Communication.readSymmetryValue(m.getBytes()));
          break;
        default:
          System.out.println("RECEIVED UNKNOWN MESSAGE: " + m);
      }
    }
  }
    

  protected void doMacro() throws GameActionException {
    spawnRobots();
    
    // Tell other towers about symmmetry
    if (MapData.symmetryKnown() && Robot.rc.getRoundNum() % 10 == 2) {
      Communication.tryBroadcastMessage(Communication.makeSymmetryMessage());
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
      case 2: spawnRound2(); return;
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

    int round = rc.getRoundNum();
    int chips = rc.getChips();

    // Lite bot waits longer between spawns and delays splashers substantially.
    if (round < 80) {
      if (chips < 800) { return; }
      if (!seenMopper && round > 20) {
        trySpawn(UnitType.MOPPER, MapData.MAP_CENTER);
      } else if (round % 3 == 0) {
        trySpawn(UnitType.SOLDIER, MapData.MAP_CENTER);
      }
      return;
    }

    if (chips < 1000) { return; }
    switch (round % 6) {
      case 0:
        if (!seenMopper || round < 150) {
          trySpawn(UnitType.MOPPER, MapData.MAP_CENTER);
        } else {
          trySpawn(UnitType.SOLDIER, MapData.MAP_CENTER);
        }
        break;
      case 3:
        if (round >= 130 && chips >= 1500) {
          trySpawn(UnitType.SPLASHER, MapData.MAP_CENTER);
          break;
        }
      default:
        trySpawn(UnitType.SOLDIER, MapData.MAP_CENTER);
        break;
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

}
