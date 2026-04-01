package frontReset.util;

import frontReset.*;

import battlecode.common.*;

public class Painter {

  private static final MapLocation[] paintCache = new MapLocation[GameConstants.PATTERN_SIZE * GameConstants.PATTERN_SIZE];
  private static MapLocation cacheLoc;

  /**
   * Handles the logic for painting (or attacking) a specific square
   * @param loc The location to paint
   * @return Whether we actually painted the space or not
   */
  public static boolean paint(MapLocation loc) throws GameActionException {
    boolean useSecondary = MapData.useSecondaryPaint(loc);
    if (!shouldPaint(loc, useSecondary)) { return false; }
    Robot.rc.attack(loc, useSecondary);
    return true;
  }

  /**
   * Checks whether a square should (and can) be painted with the given color.
   * @param loc The location to paint
   * @param useSecondary Whether to use secondary paint or not
   * @return Whether the square should be painted or not
   */
  public static boolean shouldPaint(MapLocation loc, boolean useSecondary) throws GameActionException {
    if (!Robot.rc.canAttack(loc) || // If I can't attack OR
       (!Robot.rc.canPaint(loc) && // (I can't paint AND
        !(Robot.rc.canSenseRobotAtLocation(loc) && // There isn't an enemy robot)
          Robot.rc.senseRobotAtLocation(loc).getTeam() == Robot.rc.getTeam().opponent())))
            { return false; }
    PaintType current = Robot.rc.senseMapInfo(loc).getPaint();
    return !current.isAlly() || (MapData.knownPaintColor(loc) && current.isSecondary() != useSecondary);
  }

  /**
   * Checks whether a square should (and can) be painted
   * @param loc The location to paint
   * @return Whether the square should be painted or not
   */
  public static boolean shouldPaint(MapLocation loc) throws GameActionException {
    boolean useSecondary = MapData.useSecondaryPaint(loc);
    return shouldPaint(loc, useSecondary);
  }

  /**
   * Handles the logic for mopping (or attacking) a specific square
   * @param loc The location to mop
   * @return Whether we actually mopped the space or not
   */
  public static boolean mop(MapLocation loc) throws GameActionException {
    if (shouldMop(loc)) { Robot.rc.attack(loc); return true; }
    return false;
  }

  /**
   * Checks whether a square should (and can) be mopped.
   * @param loc The location to mop
   * @return Whether the square should be mopped or not
   */
  public static boolean shouldMop(MapLocation loc) throws GameActionException {
    return Robot.rc.canAttack(loc) &&  // Can attack AND (
      (Robot.rc.canSenseRobotAtLocation(loc) && Robot.rc.senseRobotAtLocation(loc).getTeam() != Robot.rc.getTeam() || // Can sense enemy robot
      Robot.rc.senseMapInfo(loc).getPaint().isEnemy()); // OR enemy paint )
  }

  /**
   * Takes care of robot painting logic when no specific target is in mind
   * 
   * TODO: Make this not suck
   * 
   * @return Whether we actually painted or not
   */
  public static boolean paint() throws GameActionException {
    if (!Robot.rc.isActionReady()) { return false; }
    
    // Paint under self
    if (Robot.rc.senseMapInfo(Robot.rc.getLocation()).getPaint() == PaintType.EMPTY && paint(Robot.rc.getLocation())) {
      return true;
    }

    // Try to attack someone
    RobotInfo[] robots = Robot.rc.senseNearbyRobots(Robot.rc.getType().actionRadiusSquared, Robot.rc.getTeam().opponent());
    for (RobotInfo robot : robots) {
      if (paint(robot.getLocation())) {
        return true;
      }
    }

    // Paint elsewhere
    MapInfo[] infos = Robot.rc.senseNearbyMapInfos(Robot.rc.getLocation(), Robot.rc.getType().actionRadiusSquared);
    MapLocation backup = infos[0].getMapLocation();
    for (MapInfo info : infos) {
      if (info.getPaint() == PaintType.EMPTY) {
        if (paint(info.getMapLocation())) { return true; }
      } else {
        if (shouldPaint(info.getMapLocation())) { backup = info.getMapLocation(); }
      }
    }

    if (paint(backup)) {
      return true;
    }
    
    return false;
  }

  /**
   * Takes care of robot mopping logic when no specific target is in mind
   * 
   * TODO: Make this not suck
   * 
   * @return Whether we actually mopped or not
   */
  public static boolean mop() throws GameActionException {
    if (!Robot.rc.isActionReady()) { return false; }

    // Mop under self (if enemy paint)
    if (mop(Robot.rc.getLocation())) {
      return true;
    }

    // Try to attack someone
    RobotInfo[] robots = Robot.rc.senseNearbyRobots(Robot.rc.getType().actionRadiusSquared, Robot.rc.getTeam().opponent());
    MapLocation nearestRobot = null;
    for (RobotInfo robot : robots) {
      if (mop(robot.getLocation())) {
        return true;
      }
      //look for non-tower robots to chase down
      if(!robot.getType().isTowerType() && (nearestRobot==null || Robot.rc.getLocation().distanceSquaredTo(robot.getLocation())<Robot.rc.getLocation().distanceSquaredTo(nearestRobot))){
        nearestRobot = robot.getLocation();
      }
    }

    //try to chase down the nearest enemy robot
    if(Robot.rc.isMovementReady() && nearestRobot!=null){
      BugPath.moveTo(nearestRobot);
      if (mop(nearestRobot)) return true;
    }

    // Mop elsewhere
    //MapLocation[] locs = Robot.rc.getAllLocationsWithinRadiusSquared(Robot.rc.getLocation(), Robot.rc.getType().actionRadiusSquared);
    MapInfo[] locs = Robot.rc.senseNearbyMapInfos();
    MapLocation nearestEnemyPaint = null;
    for (MapInfo loc : locs) {
      if (mop(loc.getMapLocation())) {
        return true;
      }
      if(loc.getPaint().isEnemy() && (nearestEnemyPaint == null || Robot.rc.getLocation().distanceSquaredTo(loc.getMapLocation())<Robot.rc.getLocation().distanceSquaredTo(nearestEnemyPaint))){
        nearestEnemyPaint = loc.getMapLocation();
      }
    }

    //try to move towards the nearest enemy paint
    if(Robot.rc.isMovementReady() && nearestEnemyPaint!=null){
      BugPath.moveTo(nearestEnemyPaint);
      if (mop(nearestEnemyPaint)) return true;
    }

    return false;
  }

  /**
   * Handles the logic for fighting (painting) an enemy
   * @param enemy The enemy to target
   * @throws GameActionException
   */
  public static void paintFight(RobotInfo enemy) throws GameActionException {
    // We are far away, let macro take care of Pathfinding
    if (enemy == null || !Robot.rc.getLocation().isWithinDistanceSquared(enemy.getLocation(), GameConstants.VISION_RADIUS_SQUARED)) { return; }
    MapLocation enemyLoc = enemy.getLocation();

    MovementManager.move(Micro.getMove());

    // Attack enemy
    if (Robot.rc.canAttack(enemyLoc)) {
      paint(enemyLoc);
    }

    MovementManager.move(Micro.getMove());

    // Whatever square we end on, try to paint it
    if (Robot.rc.canPaint(Robot.rc.getLocation())) { paint(Robot.rc.getLocation()); }
  }

  /**
   * Handles the logic for fighting (mopping) an enemy
   * @param enemy The enemy to target
   * @throws GameActionException
   */
  public static void mopFight(RobotInfo enemy) throws GameActionException {
    MapLocation enemyLoc = enemy.getLocation();
    int distance_sq = Robot.rc.getLocation().distanceSquaredTo(enemyLoc);
    int enemy_range_sq = enemy.getType().actionRadiusSquared;

    // If we can't attack move in
    if (distance_sq > Robot.rc.getType().actionRadiusSquared && Robot.rc.isMovementReady()) {
      Direction moveIn = Pathfinding.getGreedyMove(Robot.rc.getLocation(), enemyLoc, true, MovementManager.Mode.ALLY_ONLY);
      // But only move in range of enemy if we are ready to attack
      if (moveIn != null && Robot.rc.canMove(moveIn) && (Robot.rc.getLocation().add(moveIn).distanceSquaredTo(enemyLoc) > enemy_range_sq || Robot.rc.isActionReady())) {
        MovementManager.move(moveIn);
      }
    }

    // Attack enemy
    if (Robot.rc.canAttack(enemyLoc)) { mop(enemyLoc); }

    // If enemy can see us, back up
    if (distance_sq <= enemy_range_sq && Robot.rc.isMovementReady()) {
      Direction backup = Pathfinding.getGreedyMove(Robot.rc.getLocation(), enemyLoc.directionTo(Robot.rc.getLocation()), true, MovementManager.Mode.ANY);
      if (backup != null && Robot.rc.canMove(backup)) { MovementManager.move(backup); }
    }

    // Whatever square we end on, try to paint it
    if (Robot.rc.canAttack(Robot.rc.getLocation())) { mop(Robot.rc.getLocation()); }
  }

  /**
   * Handles the logic for capturing a ruin
   * @param Pathfinding The class to help with movement, with the ruin set as the target
   * @return Whether the ruin was successfully captured
   * @throws GameActionException
   */
  public static boolean paintCaptureRuin() throws GameActionException {
    MapLocation current = Robot.rc.getLocation();

    // TODO: Don't just stand here if you can't make progress

    int low_x = GoalManager.current().target.x - (GameConstants.PATTERN_SIZE / 2);
    int low_y = GoalManager.current().target.y - (GameConstants.PATTERN_SIZE / 2);
    
    // Check the cache to see if we are capturing same ruin
    if (!GoalManager.current().target.equals(cacheLoc)) {
      // If not, build the cache
      cacheLoc = GoalManager.current().target;
      // TODO: UNROLL THESE LOOPS TO SAVE BYTECODE?
      for (int x_offset = 0; x_offset < GameConstants.PATTERN_SIZE; ++x_offset) {
        for (int y_offset = 0; y_offset < GameConstants.PATTERN_SIZE; ++y_offset) {
          paintCache[x_offset * GameConstants.PATTERN_SIZE + y_offset] = new MapLocation(low_x + x_offset, low_y + y_offset);
        }
      }
    }

    // Check if someone has already captured the ruin
    if (Robot.rc.canSenseRobotAtLocation(cacheLoc)) {
      MapData.updateData(Robot.rc.senseMapInfo(cacheLoc));
      cacheLoc = null;
      return true;
    }


    // If we are standing in the ruin and can't move, prioritize paint under our feet
    /*
    if (!Robot.rc.isMovementReady()) {
      int my_x_offset = current.x - low_x;
      int my_y_offset = current.y - low_y;
      if (my_x_offset >= 0 && my_x_offset < GameConstants.PATTERN_SIZE &&
      my_y_offset >= 0 && my_y_offset < GameConstants.PATTERN_SIZE) {
        paint(current);
      }
    }
    */
  
    boolean jobComplete = true;
    boolean enemyPaintSeen = false;
    // Try painting the rest of the ruin
    for (MapLocation loc : paintCache) {
      if (loc.equals(cacheLoc)) { continue; }
      if (!Robot.rc.canSenseLocation(loc)) { jobComplete = false; }
      // Only interested in ally, empty, or unknown paint
      if (Robot.rc.canSenseLocation(loc)) {
        PaintType paint = Robot.rc.senseMapInfo(loc).getPaint();
        if (paint.isEnemy() || (paint.isAlly() && (paint.isSecondary() == MapData.useSecondaryPaint(loc)))) {
          if (paint.isEnemy()) { enemyPaintSeen = true; }
          continue;
        }
      }
      if (jobComplete) { Robot.rc.setIndicatorDot(loc, 0, 255, 0); }
      else { Robot.rc.setIndicatorDot(loc, 255, 0, 0); }
      jobComplete = false;
      // If we can't reach it, move towards it
      if (Robot.rc.isMovementReady() && current.distanceSquaredTo(loc) > Robot.rc.getType().actionRadiusSquared) {
        Direction dir = Pathfinding.getGreedyMove(current, loc, true, Robot.rc.isActionReady() ? MovementManager.Mode.ANY : MovementManager.Mode.NO_ENEMY);
        if (dir == null || !Robot.rc.canMove(dir)) { continue; }
        MovementManager.move(dir);
        current = Robot.rc.getLocation();
        // Check if there is paint under our feet
        //if (paint(current)) { break; }
      }
      if (!shouldPaint(loc)) { continue; }
      if (paint(loc)) { break; }
    }

    // Try to complete the ruin
    if (Robot.rc.canCompleteTowerPattern(MapData.getGoalTowerType(cacheLoc), cacheLoc)) {
      Robot.rc.completeTowerPattern(MapData.getGoalTowerType(cacheLoc), cacheLoc);
      MapData.updateData(Robot.rc.senseMapInfo(cacheLoc));
      cacheLoc = null;
      return true;
    }

    // In this case, we go to get reinforcements
    return jobComplete && enemyPaintSeen;
  }

  /**
   * Handles the logic for capturing a Special Resource Pattern
   * @param Pathfinding The class to help with movement, with the center set as the target
   * @return Whether the SRP was successfully captured
   * @throws GameActionException
   */
  public static boolean paintCaptureSRP() throws GameActionException {
    MapLocation current = Robot.rc.getLocation();

    // TODO: Don't just stand here if you can't make progress

    int low_x = GoalManager.current().target.x - (GameConstants.PATTERN_SIZE / 2);
    int low_y = GoalManager.current().target.y - (GameConstants.PATTERN_SIZE / 2);
    
    // Check the cache to see if we are capturing same ruin
    if (!GoalManager.current().target.equals(cacheLoc)) {
      // If not, build the cache
      cacheLoc = GoalManager.current().target;
      // TODO: UNROLL THESE LOOPS TO SAVE BYTECODE?
      for (int x_offset = 0; x_offset < GameConstants.PATTERN_SIZE; ++x_offset) {
        for (int y_offset = 0; y_offset < GameConstants.PATTERN_SIZE; ++y_offset) {
          paintCache[x_offset * GameConstants.PATTERN_SIZE + y_offset] = new MapLocation(low_x + x_offset, low_y + y_offset);
        }
      }
    }

    // Check if someone already captured SRP
    if (Robot.rc.canSenseLocation(cacheLoc) && Robot.rc.senseMapInfo(cacheLoc).isResourcePatternCenter()) {
      // Might not actually be captured yet
      if (Robot.rc.canCompleteResourcePattern(cacheLoc)) {
        Robot.rc.completeResourcePattern(cacheLoc);
      }
      MapData.updateData(Robot.rc.senseMapInfo(cacheLoc));
      cacheLoc = null;
      return true;
    }

    // If we are standing in the ruin, prioritize paint under our feet
    int my_x_offset = current.x - low_x;
    int my_y_offset = current.y - low_y;
    if (my_x_offset >= 0 && my_x_offset < GameConstants.PATTERN_SIZE &&
        my_y_offset >= 0 && my_y_offset < GameConstants.PATTERN_SIZE) {
      paint(current);
    }
  
    // Try painting the rest of the ruin
    if (Robot.rc.isActionReady()) {
      for (MapLocation loc : paintCache) {
        // Only interested in ally, empty, or unknown paint
        if (Robot.rc.canSenseLocation(loc) && (Robot.rc.senseMapInfo(loc).getPaint().isEnemy() || Robot.rc.senseMapInfo(loc).getPaint().isAlly() && Robot.rc.senseMapInfo(loc).getPaint().isSecondary() == MapData.useSecondaryPaint(loc))) { continue; }

        // If we can't reach it, move towards it
        if (Robot.rc.isMovementReady() && current.distanceSquaredTo(loc) > Robot.rc.getType().actionRadiusSquared) {
          Direction dir = Pathfinding.getGreedyMove(current, loc, true, Robot.rc.isActionReady() ? MovementManager.Mode.ANY : MovementManager.Mode.NO_ENEMY);
          if (dir == null || !Robot.rc.canMove(dir)) { continue; }
          MovementManager.move(dir);
          current = Robot.rc.getLocation();
          // Check if there is paint under our feet
          if (paint(current)) { break; }
        }
        if (!shouldPaint(loc)) { continue; }
        if (paint(loc)) { break; }
      }
    }

    // Try to complete the SRP
    if (Robot.rc.canCompleteResourcePattern(cacheLoc)) {
      Robot.rc.completeResourcePattern(cacheLoc);
      MapData.updateData(Robot.rc.senseMapInfo(cacheLoc));
      cacheLoc = null;
      return true;
    }
      
    return false;
  }

  /**
   * Handles the logic for defending a ruin by cleaning enemy paint
   * @returns true if our job is complete, false otherwise
   */
  public static boolean mopCaptureRuin() throws GameActionException {
    MapLocation current = Robot.rc.getLocation();
    int low_x = GoalManager.current().target.x - (GameConstants.PATTERN_SIZE / 2);
    int low_y = GoalManager.current().target.y - (GameConstants.PATTERN_SIZE / 2);

    // TODO: Don't just stand here if you can't make progress

    // Check the cache to see if we are capturing same ruin
    if (!GoalManager.current().target.equals(cacheLoc)) {
      // If not, build the cache
      cacheLoc = GoalManager.current().target;
      // TODO: UNROLL THESE LOOPS TO SAVE BYTECODE?
      for (int x_offset = 0; x_offset < GameConstants.PATTERN_SIZE; ++x_offset) {
        for (int y_offset = 0; y_offset < GameConstants.PATTERN_SIZE; ++y_offset) {
          paintCache[x_offset * GameConstants.PATTERN_SIZE + y_offset] = new MapLocation(low_x + x_offset, low_y + y_offset);
        }
      }
    }

    // Check if someone has already captured the ruin
    if (Robot.rc.canSenseRobotAtLocation(cacheLoc)) {
      MapData.updateData(Robot.rc.senseMapInfo(cacheLoc));
      cacheLoc = null;
      return true;
    }

    // If we are standing in the ruin, prioritize paint under our feet
    /*
    if (!Robot.rc.isMovementReady()) {
      int my_x_offset = current.x - low_x;
      int my_y_offset = current.y - low_y;
      if (my_x_offset >= 0 && my_x_offset < GameConstants.PATTERN_SIZE &&
      my_y_offset >= 0 && my_y_offset < GameConstants.PATTERN_SIZE) {
        mop(current);
      }
    }
      */
    
    boolean jobComplete = true;
    boolean ruinComplete = true;
    // Try cleaning the rest of the ruin
    for (MapLocation loc : paintCache) {
      if(!Robot.rc.canSenseLocation(loc)) { jobComplete = false; }
      if (Robot.rc.canSenseLocation(loc) && !Robot.rc.senseMapInfo(loc).getPaint().isAlly()) { ruinComplete = false; }
      // Only interested in enemy (or unknown) paint
      if (Robot.rc.canSenseLocation(loc) && !Robot.rc.senseMapInfo(loc).getPaint().isEnemy()) { continue; }
      jobComplete = false;
      // If we can't reach it, move towards it
      if (Robot.rc.isMovementReady() && current.distanceSquaredTo(loc) > Robot.rc.getType().actionRadiusSquared) {
        Direction dir = null;
        //we only care about staying on ally paint if we are already on ally paint
        if(Robot.rc.senseMapInfo(current).getPaint().isAlly()){
          dir = Pathfinding.getGreedyMove(current, loc, true, MovementManager.Mode.ALLY_ONLY);
        }else{
          dir = Pathfinding.getGreedyMove(current, loc, true, MovementManager.Mode.NO_ENEMY);
        }
        if (dir == null || !Robot.rc.canMove(dir)) { continue; }
        MovementManager.move(dir);
        current = Robot.rc.getLocation();
        // Check if there is enemy paint under our feet
        //if (mop(current)) { break; }
      }
      if (!shouldMop(loc)) { continue; }
      if (mop(loc)) { break; }
    }

    // Try to complete the ruin
    if (Robot.rc.canCompleteTowerPattern(MapData.getGoalTowerType(cacheLoc), GoalManager.current().target)) {
      Robot.rc.completeTowerPattern(MapData.getGoalTowerType(cacheLoc), GoalManager.current().target);
      MapData.updateData(Robot.rc.senseMapInfo(GoalManager.current().target));
      cacheLoc = null;
      return true;
    }

    //this only runs if there's no enemy paint in the tower pattern
    return jobComplete && !ruinComplete;
  }

  /**
   * Handles the logic for defending a SRP by cleaning enemy paint
   * @param Pathfinding The Pathfinding utility class
   * @return Whether our job is complete or not
   * @throws GameActionException
   */
  public static boolean mopCaptureSRP() throws GameActionException {
    MapLocation current = Robot.rc.getLocation();

    // TODO: Don't just stand here if you can't make progress

    int low_x = GoalManager.current().target.x - (GameConstants.PATTERN_SIZE / 2);
    int low_y = GoalManager.current().target.y - (GameConstants.PATTERN_SIZE / 2);
    
    // Check the cache to see if we are capturing same ruin
    if (!GoalManager.current().target.equals(cacheLoc)) {
      // If not, build the cache
      cacheLoc = GoalManager.current().target;
      // TODO: UNROLL THESE LOOPS TO SAVE BYTECODE?
      for (int x_offset = 0; x_offset < GameConstants.PATTERN_SIZE; ++x_offset) {
        for (int y_offset = 0; y_offset < GameConstants.PATTERN_SIZE; ++y_offset) {
          paintCache[x_offset * GameConstants.PATTERN_SIZE + y_offset] = new MapLocation(low_x + x_offset, low_y + y_offset);
        }
      }
    }

    // Check if someone already captured SRP
    if (Robot.rc.canSenseLocation(cacheLoc) && Robot.rc.senseMapInfo(cacheLoc).isResourcePatternCenter()) {
      // Might not actually be captured yet
      if (Robot.rc.canCompleteResourcePattern(cacheLoc)) {
        Robot.rc.completeResourcePattern(cacheLoc);
      }
      MapData.updateData(Robot.rc.senseMapInfo(cacheLoc));
      cacheLoc = null;
      return true;
    }

    // If we are standing in the ruin, prioritize paint under our feet
    int my_x_offset = current.x - low_x;
    int my_y_offset = current.y - low_y;
    if (my_x_offset >= 0 && my_x_offset < GameConstants.PATTERN_SIZE &&
        my_y_offset >= 0 && my_y_offset < GameConstants.PATTERN_SIZE) {
      paint(current);
    }
  
    // Try painting the rest of the ruin
    boolean jobComplete = true;
    for (MapLocation loc : paintCache) {
      if (!Robot.rc.canSenseLocation(loc)) { jobComplete = false; }
      // Only interested in ally, empty, or unknown paint
      if (Robot.rc.canSenseLocation(loc) && !Robot.rc.senseMapInfo(loc).getPaint().isEnemy()) { continue; }
      jobComplete = false;
      // If we can't reach it, move towards it
      if (Robot.rc.isMovementReady() && current.distanceSquaredTo(loc) > Robot.rc.getType().actionRadiusSquared) {
        Direction dir = null;
        //we only care about staying on ally paint if we are already on ally paint
        if(Robot.rc.senseMapInfo(current).getPaint().isAlly()){
          dir = Pathfinding.getGreedyMove(current, loc, true, MovementManager.Mode.ALLY_ONLY);
        }else{
          dir = Pathfinding.getGreedyMove(current, loc, true, MovementManager.Mode.NO_ENEMY);
        }
        if (dir == null || !Robot.rc.canMove(dir)) { continue; }
        MovementManager.move(dir);
        current = Robot.rc.getLocation();
        // Check if there is paint under our feet
        if (mop(current)) { break; }
      }
      if (!shouldMop(loc)) { continue; }
      if (mop(loc)) { break; }
    }

    // Try to complete the SRP
    if (Robot.rc.canCompleteResourcePattern(cacheLoc)) {
      Robot.rc.completeResourcePattern(cacheLoc);
      MapData.updateData(Robot.rc.senseMapInfo(cacheLoc));
      cacheLoc = null;
      return true;
    }
      
    return jobComplete;
  }


  public static void emergencyBugNav() throws GameActionException{
    if(!Robot.rc.isMovementReady()) return;
    // Check if we are a dumbass running into a wall, if so bugnav
    MapLocation towerLoc = GoalManager.current().target;
    MapLocation myLoc = Robot.rc.getLocation();
    int distance = myLoc.distanceSquaredTo(towerLoc);
    if (distance >= 16) {
        BugPath.moveTo(towerLoc);
        Robot.rc.setIndicatorDot(towerLoc,255,0,255);
    }
  }
}
