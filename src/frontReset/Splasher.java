package frontReset;

import battlecode.common.*;
import frontReset.util.*;

public final class Splasher extends Robot {

  // Constants
  private final int REFILL_PAINT_THRESHOLD = GameConstants.INCREASED_COOLDOWN_THRESHOLD;

  private boolean bugPathAroundWall = false;

  // Other goal helpers
  RobotInfo goalTower;
  String emptyTowers = "";

  public Splasher(RobotController rc_) throws GameActionException {
    super(rc_);
  }


  public void doMicro() throws GameActionException{
    rc.setIndicatorString("GOAL - " + GoalManager.current());
    if (GoalManager.current().target != null) {
      rc.setIndicatorLine(rc.getLocation(), GoalManager.current().target, 255, 0, 255);
    }

    // Check for a suicide message, if received this is priority number 1
    for (Message m : rc.readMessages(rc.getRoundNum()-1)) {
      switch (m.getBytes() & Communication.MESSAGE_TYPE_BITMASK) {
        case Communication.SUICIDE:
          Goal goal = new Goal(Goal.Type.CAPTURE_RUIN, Communication.getCoordinates(m.getBytes()));
          if (!GoalManager.pushGoal(goal)) { // CANNOT fail to push this goal
            GoalManager.replaceTopGoal(goal);
          };
          Robot.rc.setIndicatorString("Received Suicide message " + Communication.getCoordinates(m.getBytes()));
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

    // If received paint transfer from mopper, update goal
    if (GoalManager.current().type == Goal.Type.REFILL_PAINT && rc.getPaint() > REFILL_PAINT_THRESHOLD * rc.getType().paintCapacity / 100) {
      GoalManager.popGoal();
    }

    // If low on paint, set goal to refill
    if (GoalManager.current().type.v < Goal.Type.REFILL_PAINT.v && rc.getPaint() < REFILL_PAINT_THRESHOLD * rc.getType().paintCapacity / 100) {
      MapLocation tower = MapData.closestFriendlyTower(emptyTowers);
      GoalManager.pushGoal(Goal.Type.REFILL_PAINT, tower == null ? Robot.spawnTower : tower);
    }

    // Look for nearby ruins if we aren't already fighting a tower
    if (GoalManager.current().type.v < Goal.Type.FIGHT_TOWER.v) {
      boolean setGoal = false;
      MapLocation[] ruins = rc.senseNearbyRuins(-1);
      for (MapLocation ruin : ruins) {
        RobotInfo info = rc.senseRobotAtLocation(ruin);
        //we don't care about splashers capturing ruins
        if (info == null) { // Unclaimed Ruin
          //if (GoalManager.current().type.v >= Goal.Type.CAPTURE_RUIN.v) { continue; }
          //GoalManager.pushGoal(Goal.Type.CAPTURE_RUIN, ruin);
          //setGoal = true;
          continue;
        }
        if (info.getTeam() == OPPONENT) { // Enemy Tower
          if (setGoal) {
            GoalManager.replaceTopGoal(Goal.Type.FIGHT_TOWER, ruin);
          } else {
            GoalManager.pushGoal(Goal.Type.FIGHT_TOWER, ruin);
          }
          goalTower = info;
          break;
        }
      }
    }

    // DO THINGS --------------------------------------------------------------
    // Can't do anything, no point
    if (!rc.isMovementReady() && !rc.isActionReady()) { return; }

    switch (GoalManager.current().type) {
      case FIGHT_TOWER:
        Painter.emergencyBugNav();
        //fight using goalTower
        fightTower();
        //stop attacking if we killed the tower
        if (!rc.canSenseRobotAtLocation(GoalManager.current().target) || rc.senseRobotAtLocation(GoalManager.current().target).getTeam() != OPPONENT) {
          GoalManager.setNewGoal(Goal.Type.EXPLORE, MapData.getExploreTarget());
        }
        break;
      case REFILL_PAINT:
        if (rc.getLocation().isWithinDistanceSquared(GoalManager.current().target, GameConstants.PAINT_TRANSFER_RADIUS_SQUARED)) {
          RobotInfo tower = rc.senseRobotAtLocation(GoalManager.current().target);
          if (tower == null) {
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, GoalManager.current().target)) {
              rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, GoalManager.current().target);
              tower = rc.senseRobotAtLocation(GoalManager.current().target);
            } else {
              emptyTowers += GoalManager.current().target;
              MapLocation friendlyTower = MapData.closestFriendlyTower(emptyTowers);
              GoalManager.replaceTopGoal(Goal.Type.REFILL_PAINT, friendlyTower == null ? Robot.spawnTower : friendlyTower);
              return;
            }
          }
          if (tower.getPaintAmount() == 0) {
            emptyTowers += tower.getLocation().toString();
            MapLocation friendlyTower = MapData.closestFriendlyTower(emptyTowers);
            GoalManager.replaceTopGoal(Goal.Type.REFILL_PAINT, friendlyTower == null ? spawnTower : friendlyTower);
            break;
          }
          int paintAmount = rc.getType().paintCapacity - rc.getPaint();
          if (tower.getPaintAmount() < paintAmount) { paintAmount = tower.getPaintAmount(); }
          if (rc.canTransferPaint(GoalManager.current().target, -paintAmount)) {
            rc.transferPaint(GoalManager.current().target, -paintAmount);
            GoalManager.popGoal();
            if (rc.getPaint() >= REFILL_PAINT_THRESHOLD) {
              emptyTowers = "";
            }
          }
        }
        break;
      case CAPTURE_RUIN:
        BugPath.moveTo(GoalManager.current().target);
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, GoalManager.current().target)) {
          rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, GoalManager.current().target);
          MapData.updateData(rc.senseMapInfo(GoalManager.current().target));
          //GoalManager.replaceTopGoal(Goal.Type.EXPLORE,MapData.getExploreTarget());
        }
        if(rc.canSenseRobotAtLocation(GoalManager.current().target)){
          //ruin has no more enemy paint around it, start exploring
          rc.setIndicatorDot(GoalManager.current().target,255,255,255);
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
        /*
        else{
          //prioritize exploring towards enemy towers
          MapLocation closestEnemyTower = MapData.closestEnemyTower();
          if(closestEnemyTower!=null && GoalManager.current().target != closestEnemyTower){
            GoalManager.replaceTopGoal(Goal.Type.EXPLORE,closestEnemyTower);
          }
        } 
        */
        break;
      default: break;
    }
  }
  
  public void doMacro() throws GameActionException{
    if (GoalManager.current().type != Goal.Type.FIGHT_TOWER || !rc.getLocation().isWithinDistanceSquared(GoalManager.current().target, GameConstants.VISION_RADIUS_SQUARED)) {
      Pathfinding.moveTo(GoalManager.current().target); //note that Splasher defaults to ANY, can be set anywhere, but must be set back to ANY
    }
    if(GoalManager.current().type != Goal.Type.REFILL_PAINT){  //REFILL_PAINT splashers shouldn't waste paint
      if(GoalManager.current().type == Goal.Type.EXPLORE && shouldPaintExplore()){
        MapLocation attackTarget = searchForBestPaintTargetsExplore();
        if(attackTarget!=null && rc.canAttack(attackTarget)){
          rc.setIndicatorDot(attackTarget,255,0,0);
          rc.attack(attackTarget);
        }
      }
    }
  }

  /**
   * We only want to paint explore when we don't have an enemy tower in mind
   * @return whether we should splash paint while exploring
   */
  private Boolean shouldPaintExplore(){
    return (MapData.closestEnemyTower()==null);
  }

  /**
   * Searches every tile in attack radius and picks the best one
   * Doesn't take into account ruins/enemy towers
   * @return the best attack location (null if it somehow can't find a single attack location)
   */
  private MapLocation searchForBestPaintTargetsExplore() throws GameActionException{
    //TODO: reduce bytecode by storing scores in strings and checking only checking maplocations in splash range once
    if(!rc.isActionReady()) return null;
    MapLocation[] locationsInAttackRange = rc.getAllLocationsWithinRadiusSquared(rc.getLocation(),4);
    int bestScore=0;
    MapLocation bestLocation=null;
    for(MapLocation loc: locationsInAttackRange){
      int score=0;
      if(!rc.canSenseLocation(loc)) continue;
      MapInfo m = rc.senseMapInfo(loc);
      if(!m.isPassable() && !rc.canAttack(loc)) continue; //we can't paint walls (with an extra check in case we can)

      //adjacent locations
      for(Direction d : Direction.DIRECTION_ORDER){
        MapLocation tile = loc.add(d);
        if(!rc.canSenseLocation(tile)) continue;
        MapInfo tileInfo = rc.senseMapInfo(tile);
        if(!tileInfo.isPassable()) continue;
        if(tileInfo.getPaint().isAlly()){
          if(tileInfo.getPaint().isSecondary()){
            //if a tile adjacent to attack target is our own secondary paint, there's a chance we're messing up one of our patterns
            //this is bad, so we'll give it a negative score
            score-=5;
          }
          continue;
        }
        if(tileInfo.getPaint().isEnemy()){
          //enemy tiles adjacent to the attack target get a big score (this is the main purpose of splashers)
          score+=10;
        }else{
          //empty tiles adjacent to the attack target get a small score (better filled by soldiers)
          score+=1;
        }
      }
      //locations 2 away in cardinal directions
      for(Direction d : Direction.cardinalDirections()){
        MapLocation tile = loc.add(d).add(d);
        if(!rc.canSenseLocation(tile)) continue;
        MapInfo tileInfo = rc.senseMapInfo(tile);
        if(!tileInfo.isPassable()) continue;
        if(tileInfo.getPaint().isAlly()){
          if(tileInfo.getPaint().isSecondary()){
            //if a tile adjacent to attack target is our own secondary paint, there's a chance we're messing up one of our patterns
            //this is bad, so we'll give it a negative score
            score-=5;
          }
          continue;
        }
        if(tileInfo.getPaint().isEnemy()){
          //enemy tiles adjacent to the attack target get a big score (this is the main purpose of splashers)
          //note that locations 2 away from cardinal directions can't be painted
          //score+=10;
        }else{
          //empty tiles adjacent to the attack target get a small score (better filled by soldiers)
          score+=1;
        }
      }
      if(score>bestScore){
        bestScore=score;
        bestLocation = loc;
      }
    }
    return bestLocation;
  }

  /**
   * Searches every tile in attack radius and picks the best one
   * Takes into account enemy towers
   * This is a separate method from searchForBestPaintTargetExplore so we can add robot-tracking logic down the line
   * @return the best attack location (null if it somehow can't find a single attack location)
   */
  private MapLocation searchForBestPaintTargetsAttack() throws GameActionException{
    //TODO: reduce bytecode by storing scores in strings and checking only checking maplocations in splash range once
    //TODO: score by enemy robots too
    if(!rc.isActionReady()) return null;
    MapLocation[] locationsInAttackRange = rc.getAllLocationsWithinRadiusSquared(rc.getLocation(),4);
    int bestScore=0;
    MapLocation bestLocation=null;
    for(MapLocation loc: locationsInAttackRange){
      int score=0;
      if(!rc.canSenseLocation(loc)) continue;
      MapInfo m = rc.senseMapInfo(loc);
      if(!m.isPassable() && !rc.canAttack(loc)) continue; //we can't paint walls (with an extra check in case we can)

      //adjacent locations
      for(Direction d : Direction.DIRECTION_ORDER){
        MapLocation tile = loc.add(d);
        if(!rc.canSenseLocation(tile)) continue;
        MapInfo tileInfo = rc.senseMapInfo(tile);
        if(!tileInfo.isPassable()) continue;
        if(tileInfo.getPaint().isAlly()){
          if(tileInfo.getPaint().isSecondary()){
            //if a tile adjacent to attack target is our own secondary paint, there's a chance we're messing up one of our patterns
            //this is bad, so we'll give it a negative score
            score-=5;
          }
          continue;
        }
        if(tileInfo.getPaint().isEnemy()){
          //enemy tiles adjacent to the attack target get a big score (this is the main purpose of splashers)
          score+=10;
        }else{
          //empty tiles adjacent to the attack target get a small score (better filled by soldiers)
          score+=1;
        }
      }
      //locations 2 away in cardinal directions
      for(Direction d : Direction.cardinalDirections()){
        MapLocation tile = loc.add(d).add(d);
        if(!rc.canSenseLocation(tile)) continue;
        MapInfo tileInfo = rc.senseMapInfo(tile);
        if(!tileInfo.isPassable()) continue;
        if(tileInfo.getPaint().isAlly()){
          if(tileInfo.getPaint().isSecondary()){
            //if a tile adjacent to attack target is our own secondary paint, there's a chance we're messing up one of our patterns
            //this is bad, so we'll give it a negative score
            score-=5;
          }
          continue;
        }
        if(tileInfo.getPaint().isEnemy()){
          //enemy tiles adjacent to the attack target get a big score (this is the main purpose of splashers)
          //note that locations 2 away from cardinal directions can't be painted
          //score+=10;
        }else{
          //empty tiles adjacent to the attack target get a small score (better filled by soldiers)
          score+=1;
        }
      }

      //bonus for target distance being closer to the tower
      //might want to do a robot analysis later
      if(goalTower!=null&&goalTower.getLocation().isWithinDistanceSquared(loc, 34)){
        score+=(34-goalTower.getLocation().distanceSquaredTo(loc));
      }
      if(score>bestScore){
        bestScore=score;
        bestLocation = loc;
      }
    }
    return bestLocation;
  }

  private void fightTower() throws GameActionException{
    if(goalTower==null) return;
    //move closer to tower, but not in it's attack range
    int distanceSquaredToTower = goalTower.getLocation().distanceSquaredTo(rc.getLocation());
    if(distanceSquaredToTower>16 && rc.isMovementReady()){
      //we are well outside of attack range, move closer
      Direction d = Pathfinding.getGreedyMove(rc.getLocation(),goalTower.getLocation());
      MovementManager.move(d);
      //rc.setIndicatorString("Move towards enemy tower: "+distanceSquaredToTower+", "+goalTower.getLocation());
    }else if(distanceSquaredToTower>9 && distanceSquaredToTower <= 16){
      //we are close to attack range but still outside
      //try moving tangentially to the tower?
      rc.setIndicatorString("We are happy with our distance to enemy tower\nWe also haven't been programed to move for now");
    }
    //attack
    MapLocation attackTarget = searchForBestPaintTargetsAttack();
    if(attackTarget!=null && rc.canAttack(attackTarget)){
      rc.setIndicatorDot(attackTarget,255,0,0);
      rc.attack(attackTarget);
    }

    //move away from tower if we need to
    distanceSquaredToTower = goalTower.getLocation().distanceSquaredTo(rc.getLocation());
    if(distanceSquaredToTower<9 && rc.isMovementReady()){
      Direction d = Pathfinding.getGreedyMove(rc.getLocation(),goalTower.getLocation().directionTo(rc.getLocation()),true);
      MovementManager.move(d);
      //rc.setIndicatorString("Move away from enemy tower");
    }

  }
}