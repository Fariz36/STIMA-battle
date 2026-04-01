package frontReset;

import battlecode.common.*;
import frontReset.util.*;

public final class Soldier extends Robot {

  // Other goal helpers
  RobotInfo goalTower;

  //variables for hard coding the first 2 soldiers from each starting tower
  private boolean initialSoldiers; //true if this soldier was the 1st or 2nd soldier spawned from the starting paint/money towers
  private int followID; //2nd soldier spawned from the starting paint/money towers stores the 1st soldier's id (1st soldier store -1 here)
  private RobotInfo spawnTowerInfo; //true if this soldier is spawned from the starting paint tower

  private boolean bugPathAroundWall = false;


  public Soldier(RobotController rc_) throws GameActionException {
    super(rc_);
    
    //set variables for hard coding the first 2 soldiers (initializeSoldiers, followID, spawnedFromPaintTower)
    initialSoldiers = (rc.getRoundNum()<10);
    followID=-1;
    if(initialSoldiers){
      RobotInfo[] nearbyRobots = rc.senseNearbyRobots();

      //find the home tower (might have trouble if soldier is in spawn-range of both starting tower)
      for(RobotInfo robot : nearbyRobots){
        if(robot.getType().isTowerType() && robot.getLocation().distanceSquaredTo(rc.getLocation())<= GameConstants.BUILD_ROBOT_RADIUS_SQUARED){
          spawnTowerInfo = robot;
        }
      }
      MapLocation correctFirstSoldier = null;
      for(RobotInfo robot : nearbyRobots){
        if(rc.getRoundNum()>2&&robot.getType()==UnitType.SOLDIER && //TODO: change the roundNum threshold once we don't overflow on bytecode turn 1
        (correctFirstSoldier==null || spawnTowerInfo.getLocation().distanceSquaredTo(robot.getLocation()) < spawnTowerInfo.getLocation().distanceSquaredTo(correctFirstSoldier))){
          followID = robot.getID();
          correctFirstSoldier = robot.getLocation();
        }
      }
    }
  }

  protected void doMicro() throws GameActionException {
    //hard code the opening (ignores the rest of the function for now)
    if(initialSoldiers){
      rc.setIndicatorString("INITIAL SOLDIERS");
      opening();
      return;
    }
    rc.setIndicatorString("GOAL - " + GoalManager.current());
    if (GoalManager.current().target != null) {
      rc.setIndicatorLine(rc.getLocation(), GoalManager.current().target, 255, 0, 255);
    }

    // TODO: TAKE THIS OUT AND DEAL WITH CROWDING lol
    // If surrounded, kill yourself
    if (rc.senseNearbyRobots(9, TEAM).length > 24 /* Out of 28 */) {
      rc.disintegrate();
      return;
    }

    // UPDATE GOAL ------------------------------------------------------------

    // Check for a suicide message, if received this is priority number 1
    for (Message m : rc.readMessages(rc.getRoundNum()-1)) {
      switch (m.getBytes() & Communication.MESSAGE_TYPE_BITMASK) {
        case Communication.SUICIDE:
          Goal goal = new Goal(Goal.Type.CAPTURE_RUIN, Communication.getCoordinates(m.getBytes()));
          if (!GoalManager.pushGoal(goal)) { // CANNOT fail to push this goal
            GoalManager.replaceTopGoal(goal);
          };
          Robot.rc.setIndicatorString("Received Suicide message " + Communication.getCoordinates(m.getBytes()));
          //rebuild a tower
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

    //surviving takes all precendent over everything
    if(GoalManager.current().type == Goal.Type.SURVIVE || rc.getPaint()<7){
      if(rc.getPaint()>7){
        GoalManager.popGoal();
      }else{
        //rc.setIndicatorString("SURVIVE");
        survive();
        return;
      }
    }

    // Check if someone else finished the current SRP
    // TODO: Should this check stay here? Duplicated in Painter
    if (GoalManager.current().type == Goal.Type.CAPTURE_SRP) {
      MapLocation target = GoalManager.current().target;
      boolean contested = MapData.isContested(target);
      // If we don't think it is contested, look for enemy paint
      if(!contested){
        MapInfo[] towerPatternTiles = rc.senseNearbyMapInfos(target,8);
        for(MapInfo m : towerPatternTiles){
          if(m.getPaint().isEnemy()){
            contested = true;
            break;
          }
        }
      }
      // If there is enemy paint, look for moppers
      if(contested){
        //look for nearby moppers
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1,rc.getTeam());
        for(RobotInfo ally : nearbyAllies){
          if(ally.getType()==UnitType.MOPPER){
            contested=false;
            break;
          }
        }
        // Move on if contested, stay if uncontested (or moppers are there)
        if (!contested) { MapData.setUncontested(GoalManager.current().target); }
        else { GoalManager.popGoal(); }
      }
      
      if (rc.canSenseLocation(target) && rc.senseMapInfo(target).isResourcePatternCenter()) {
        MapData.updateData(rc.senseMapInfo(target));
        GoalManager.popGoal();
      }
    }

    // Look for nearby ruins if we aren't already fighting a tower
    if (GoalManager.current().type.v <= Goal.Type.FIGHT_TOWER.v && rc.getHealth() >= 30) {
      boolean setGoal = false;
      MapLocation[] ruins = rc.senseNearbyRuins(-1);
      for (MapLocation ruin : ruins) {
        RobotInfo info = rc.senseRobotAtLocation(ruin);
        if (info == null) { // Unclaimed Ruin
          if (GoalManager.current().type.v >= Goal.Type.CAPTURE_RUIN.v) { continue; }
          GoalManager.pushGoal(Goal.Type.CAPTURE_RUIN, ruin);
          setGoal = true;
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

    // Look for SRP if we are a lower priority
    // TODO: Make this process more intelligent. Pack better with other SRPs, etc
    if (GoalManager.current().type.v < Goal.Type.CAPTURE_SRP.v) {
      for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), GameConstants.MARK_RADIUS_SQUARED)) {
        if (MapData.tryMarkSRP(loc) && !MapData.isContested(loc)) {
          GoalManager.pushGoal(Goal.Type.CAPTURE_SRP, loc);
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
        Painter.paintFight(goalTower);
        MapLocation towerLoc = GoalManager.current().target;
        if (!rc.canSenseRobotAtLocation(towerLoc) || rc.senseRobotAtLocation(towerLoc).getTeam() != OPPONENT) {
          GoalManager.replaceTopGoal(Goal.Type.CAPTURE_RUIN, towerLoc);
        }
        // If health is too low and no friends around, stop fighting and explore
        if (rc.getHealth() < 30 && rc.senseNearbyRobots(rc.getType().actionRadiusSquared, TEAM).length == 0) {
          rc.setIndicatorString("HP Low, Popping goal");
          GoalManager.popGoal();
        }
        break;
      case CAPTURE_SRP:
        if (Painter.paintCaptureSRP()) {
          GoalManager.popGoal();
        }
        break;
      case CAPTURE_RUIN:
        Painter.emergencyBugNav();
        if (Painter.paintCaptureRuin()) {
          // Check if we actually finished ruin or if we just can't make progress
          if (rc.canSenseRobotAtLocation(GoalManager.current().target)) {
            // Refill if we complete the ruin
            RobotInfo tower = rc.senseRobotAtLocation(GoalManager.current().target);
            if (tower.getPaintAmount() > UnitType.MOPPER.paintCost) {
              int paintAmount = rc.getType().paintCapacity - rc.getPaint();
              if (tower.getPaintAmount() - UnitType.MOPPER.paintCost < paintAmount) { paintAmount = tower.getPaintAmount() - UnitType.MOPPER.paintCost; }
              if (rc.canTransferPaint(GoalManager.current().target, -paintAmount)) {
                rc.transferPaint(GoalManager.current().target, -paintAmount);
                emptyTowers = "";
                GoalManager.popGoal();
              }
            }
          } else {
            rc.setIndicatorString("Ruin pattern complete + enemy paint");
            //special case: we are trying to capture a tower near the front
            MapLocation nearestFront = MapData.getNearestFront();
            boolean quit = false;
            if(nearestFront!=null){
              //count the number of allied soldiers also trying to capture the tower, (we'll leave only 1 soldier)
              for(RobotInfo info : rc.senseNearbyRobots(GoalManager.current().target,8,TEAM)){
                //break soldier-ties with id num
                if(info.getType()==UnitType.SOLDIER && info.getID()<rc.getID()){
                  GoalManager.pushGoal(Goal.Type.FIGHT_TOWER,nearestFront);
                  quit = true;
                  rc.setIndicatorString("Quitting to attack "+nearestFront);
                  break;
                }
              }
            }
            if(!quit){
              // Only request backup if we don't already see a mopper
              boolean foundMopper = false;
              for (RobotInfo info : rc.senseNearbyRobots(-1, TEAM)) {
                if (info.getType() == UnitType.MOPPER) {
                  foundMopper = true;
                  break;
                }
              }
              if (!foundMopper) {
                rc.setIndicatorString("Get a mopper to help");
                MapLocation tower = MapData.closestFriendlyTower(emptyTowers);
                GoalManager.pushGoal(Goal.Type.GET_BACKUP, tower == null ? Robot.spawnTower : tower);
              }
            }
          }
        }
        break;
      case GET_BACKUP:
        if (rc.getLocation().isWithinDistanceSquared(GoalManager.current().target, GameConstants.MESSAGE_RADIUS_SQUARED)) {
          if (Communication.trySendMessage(Communication.addCoordinates(Communication.REQUEST_MOPPER, GoalManager.second().target), GoalManager.current().target)) {
            GoalManager.popGoal();
          }
        }
        break;
      case EXPLORE: // TODO: Address clumping of units
        if (rc.getLocation().isWithinDistanceSquared(GoalManager.current().target, GameConstants.VISION_RADIUS_SQUARED)) {
          MapData.updateData(rc.senseMapInfo(GoalManager.current().target));
          GoalManager.replaceTopGoal(Goal.Type.EXPLORE,MapData.getExploreTarget());
        }
        break;
      default: break;
    }
  }

  protected void doMacro() throws GameActionException {
    if (DEBUG) { System.out.println("Entering doMacro"); }
    if (GoalManager.current().type != Goal.Type.FIGHT_TOWER || !rc.getLocation().isWithinDistanceSquared(GoalManager.current().target, GameConstants.VISION_RADIUS_SQUARED)) {
      if (DEBUG) { System.out.println("Pathfinding to target " + GoalManager.current().target); }
      Pathfinding.moveTo(GoalManager.current().target); //note that Soldier defaults to ANY, can be set anywhere, but must be set back to ANY
    }
    if(!initialSoldiers && GoalManager.current().type == Goal.Type.EXPLORE && rc.getPaint()>100){  //initial soldier and soldiers low on paint shouldn't waste paint
      Painter.paint();
    }
  }

  private void opening() throws GameActionException {
    boolean isExploring = GoalManager.current().type == Goal.Type.CAPTURE_RUIN; //note that the for loop can set the GoalManager, I want to remember what it had before we enter the for loop
    MapLocation closestRuin = null;
    if (DEBUG) { System.out.println("Calling Opening (Goal: " + GoalManager.current() + ")"); }
    // Update any close ruins sites if we don't have a goal already
    if (goalTower == null) {
      for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
        RobotInfo tower = rc.senseRobotAtLocation(ruin);
        if(tower!=null){
          if(tower.getTeam()==rc.getTeam().opponent()){
            //enemy tower, attack!
            GoalManager.pushGoal(Goal.Type.FIGHT_TOWER,tower.getLocation());
            goalTower = tower;
            //if we're attacking a tower, assume that's all we're doing (we don't care about any other opening logic)
            break;
          }else{
            //ally tower (probably the spawn tower), we don't care
            continue;
          }
        }
        if(isExploring) continue;
        if(MapData.isContested(ruin)) continue; //ignore any ruins with enemy paint that we've already seen
        if(closestRuin == null || rc.getLocation().distanceSquaredTo(ruin)<rc.getLocation().distanceSquaredTo(closestRuin)){
          closestRuin = ruin;
          GoalManager.pushGoal(Goal.Type.CAPTURE_RUIN,ruin);
        }else if(!GoalManager.contains(Goal.Type.CAPTURE_RUIN,ruin)){
          GoalManager.pushSecondaryGoal(Goal.Type.CAPTURE_RUIN,ruin);
        }
      }
    }

    if (DEBUG ) { System.out.println("After goal ruin loop (Goal: " + GoalManager.current() + ")"); }
    
    // if we have a ruin in sight, look for enemy paint around it
    // we use a while loop so we can calculate multiple ruins at once (if possible)
    while(GoalManager.current().type == Goal.Type.CAPTURE_RUIN){
      MapInfo[] towerPatternTiles = rc.senseNearbyMapInfos(GoalManager.current().target,8);
      boolean ruinGood = true;
      for(MapInfo m : towerPatternTiles){
        if(m.getPaint().isEnemy()){
          MapData.setContested(GoalManager.current().target);
          //remove from GoalManager (we never want to go to a contested ruin in the opening)
          GoalManager.popGoal();
          ruinGood = false;
          break;
        }
      }
      if(!ruinGood){
        //try to block the ruin if it is contested (if we have to)
        MapLocation emptyTile = null;
        for(MapInfo m : towerPatternTiles){
          if(m.getPaint().isAlly()){
            emptyTile = null; //this is how we tell the post loop that we don't need to block
            break;
          }
          if(emptyTile==null && m.getPaint()==PaintType.EMPTY && m.getMapLocation().distanceSquaredTo(rc.getLocation())<9){
            emptyTile = m.getMapLocation();
          }
        }
        if(emptyTile!=null && rc.isActionReady() && rc.canAttack(emptyTile)){
          rc.attack(emptyTile,true);
        }
      }
      if(ruinGood || Clock.getBytecodesLeft() < 500) break;
    }

    if (DEBUG) { System.out.println("After contested ruin loop (Goal: " + GoalManager.current() + ")"); }
    

    if(GoalManager.current().type == Goal.Type.FIGHT_TOWER){
      Painter.emergencyBugNav();

      if (DEBUG) { System.out.println("Fighting Tower (Goal: " + GoalManager.current() + ")"); }

      MapLocation enemyTower = GoalManager.current().target;
      //goalTower is a class variable set in the ruin-scanning for loop (only set to enemy towers in opening())
      Painter.paintFight(goalTower);
      rc.setIndicatorDot(enemyTower,0,255,0);
      if(rc.canSenseLocation(enemyTower)&&!rc.canSenseRobotAtLocation(enemyTower)){
        //if we've killed the enemy tower
        initialSoldiers=false;  //we can be done with the opening if we successfully kill a tower
        GoalManager.setNewGoal(Goal.Type.EXPLORE, MapData.getExploreTarget());
      }
    }else if(GoalManager.current().type == Goal.Type.CAPTURE_RUIN){
      Painter.emergencyBugNav();

      if (DEBUG) { System.out.println("Capturing Ruin (Goal: " + GoalManager.current() + ")"); }

      MapLocation targetRuin = GoalManager.current().target;
      Painter.paintCaptureRuin();
      if (rc.canSenseLocation(targetRuin) && rc.canSenseRobotAtLocation(targetRuin)) {
        initialSoldiers=false;  //we can be done with the opening if we successfully capture the first tower
        //note initial soldiers will never finish working on a tower that is contested, so we never need to call for reinforcements
        RobotInfo tower = rc.senseRobotAtLocation(targetRuin);
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
    }else{ //explore

      if (DEBUG) { System.out.println("Exploring (Goal: " + GoalManager.current() + ")"); }

      //roam if there's no ruin in sight
      if(followID!=-1 && rc.canSenseRobot(followID)){ //second soldier
        RobotInfo firstSoldier = rc.senseRobot(followID);
        GoalManager.replaceTopGoal(Goal.Type.EXPLORE, firstSoldier.getLocation());
        rc.setIndicatorString("second soldier, followid: "+followID);
      }else{
        MapLocation closestEnemyTower = MapData.closestEnemyTower();
        if(closestEnemyTower!=null){
          GoalManager.replaceTopGoal(Goal.Type.EXPLORE, closestEnemyTower);
        }else{
          int[] symmetryPriority = new int[3];
          //we want the soldiers from each tower to assume different symmetries
          if(spawnTowerInfo.getType().getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER){
            symmetryPriority[0] = 0b010;  //horizontal
            symmetryPriority[1] = 0b001;  //rotational
            symmetryPriority[2] = 0b100;  //vertical
          }else{
            symmetryPriority[0] = 0b100;  //vertical
            symmetryPriority[1] = 0b001;  //rotational
            symmetryPriority[2] = 0b010;  //horizontal
          }

          //Guess the location of the enemy tower and choose it as our target
          MapLocation guessEnemyTower = MapData.symmetryLoc(spawnTowerInfo.getLocation(),symmetryPriority[0]);

          if(MapData.known(guessEnemyTower)){
            guessEnemyTower = MapData.symmetryLoc(spawnTowerInfo.getLocation(),symmetryPriority[1]);
            if(MapData.known(guessEnemyTower)){
              guessEnemyTower = MapData.symmetryLoc(spawnTowerInfo.getLocation(),symmetryPriority[2]);
              if(MapData.known(guessEnemyTower)){
                guessEnemyTower = null;
                
              }
            }
          }

          if(guessEnemyTower!=null){
            if (DEBUG) { System.out.println("Setting goal to guess enemy tower : " + guessEnemyTower); }
            GoalManager.replaceTopGoal(Goal.Type.EXPLORE, guessEnemyTower);
          }else{
            //this should never run, since it rules out all 3 symmetries, but if it does, default to normal exploration
            System.out.println("Initial soldiers ruled out all 3 symmetries???");
            GoalManager.replaceTopGoal(Goal.Type.EXPLORE, MapData.getExploreTarget());
          }
        }
      }
      
    }
  }

  //called by micro (and opening) when we only want the soldiers to survive
  //we don't care that much about soldiers doing anything like capturing towers or srps
  //runs when we think the soldier is low on paint deep in enemy territory
  private void survive() throws GameActionException{
    //only 3 main priorities: stay on allied paint and stay away from enemy towers/moppers (and allied robots)
    //secondary priorities: make sure every ruin we see has at least 1 of our paint around it, try to navigate back to an allied tower to refill paint (without dying)

    MapLocation currentLoc = rc.getLocation();
    int[] directionScores = {0,0,0,0,0,0,0,0,0}; //a score for every direction (including center) in the order described by Direction.getDirectionOrderNum()

    String enemyTowerString = "";
    MapLocation[] nearbyRuins = rc.senseNearbyRuins(-1);
    RobotInfo[] nearbyRobots = rc.senseNearbyRobots();

    //fill enemyTowerString with a for loop
    for (MapLocation ruin : nearbyRuins){
      RobotInfo ruinInfo = rc.senseRobotAtLocation(ruin);
      if (ruinInfo == null) { continue; }
      if(ruinInfo.getTeam()==rc.getTeam().opponent()){
        //add to enemyTowerString
        //note, we can't use maplocation.toString() here because toString will result in strings of variable length ie: "(1,0)" doesn't have the same length as "(51,24)"
        //we use a string bc it's a low-bytecode resizable array
        enemyTowerString += (char) ruin.x;
        enemyTowerString += (char) ruin.y;
        enemyTowerString += (char) 69;  //hope that no map exceeds 68x68 cuz its funny
        if(rc.canAttack(ruin)) rc.attack(ruin); //kamikaze ig
      } else {
        // We found a friendly tower, explore in that direction
        GoalManager.replaceTopGoal(Goal.Type.SURVIVE, ruin);
        //return;
      }
    }
    
    // Refill if we find our tower
    if(rc.canSenseLocation(GoalManager.current().target)){
      RobotInfo tower = rc.senseRobotAtLocation(GoalManager.current().target);
      if (tower!=null && tower.getType().isTowerType()) {
        int paintAmount = rc.getType().paintCapacity - rc.getPaint();
        if (tower.getPaintAmount() < paintAmount) { paintAmount = tower.getPaintAmount(); }
        rc.setIndicatorString("failed to refill " + paintAmount);
        if (rc.canTransferPaint(GoalManager.current().target, -paintAmount)) {
          rc.setIndicatorString("succeeded in refilling " + paintAmount);
          rc.transferPaint(GoalManager.current().target, -paintAmount);
          //emptyTowers = "";
          GoalManager.popGoal();
          return;
        }
      }
    }

    //This is the trigger for searching the map for nearby allied paint
    boolean alliedPaintInMoveRange = false;

    //give scores to each direction (scores are initialized to 0s)
    for(Direction d : Direction.DIRECTION_ORDER){
      MapLocation destination = currentLoc.add(d);
      if(!rc.onTheMap(destination) || !rc.sensePassability(destination) || (d!=Direction.CENTER && rc.senseRobotAtLocation(destination)!=null)){
        //ignore tiles off the map, walls, and tiles with robots on them (we can't move through robots) 
        directionScores[d.getDirectionOrderNum()]=Integer.MIN_VALUE;
        continue;
      }
      //give direction score penalties based on enemy towers
      if(enemyTowerString.length()>0){
        for(int i = 0; i<enemyTowerString.length(); i+=3){
          MapLocation enemyTower = new MapLocation(enemyTowerString.charAt(i),enemyTowerString.charAt(i+1));  //don't worry, I did my research, charAt is very bytecode efficient (although it doesn't have set-1 bytecode)
          int destinationDistanceToTower = destination.distanceSquaredTo(enemyTower);
          int currentDistanceToTower = currentLoc.distanceSquaredTo(enemyTower);
          if(destinationDistanceToTower<=16){ //tower attack range is 4 (and it's not a gameconstant)
            //penalize a direction's score if it's in range of an enemy tower
            directionScores[d.getDirectionOrderNum()]-=100;
          }
          if(currentDistanceToTower<=16 && destinationDistanceToTower < currentDistanceToTower){
            //penalize a direction's score if it gets closer to an enemy tower (only if we're already in attack range of the tower)
            directionScores[d.getDirectionOrderNum()]-=100;
          }
        }
      }

      //give direction score penalties/bonuses based on paint
      MapInfo tile = rc.senseMapInfo(destination);
      switch(tile.getPaint()){
        case PaintType.ALLY_PRIMARY:  //I'm the goat if switch case statements work like this
        case PaintType.ALLY_SECONDARY:
          directionScores[d.getDirectionOrderNum()]+=50;
          alliedPaintInMoveRange = true;
          break;
        case PaintType.ENEMY_PRIMARY:
        case PaintType.ENEMY_SECONDARY:
          directionScores[d.getDirectionOrderNum()]-=50;
          if(rc.getPaint()<10) directionScores[d.getDirectionOrderNum()]-=100;
          break;
        case PaintType.EMPTY:
          directionScores[d.getDirectionOrderNum()]-=25;
          if(rc.getPaint()<10) directionScores[d.getDirectionOrderNum()]-=50;
          break;
      }

      //give penalties based on enemy moppers and allied units
      // int friends = 0;
      for(RobotInfo r : nearbyRobots) {
        // If we see >2 nearby friends, someone has come to support us and we can go home
        // if (r.getTeam() == rc.getTeam()) { if (++friends > 2) { GoalManager.setNewGoal(Goal.Type.EXPLORE, spawnTower); return; } }
        if(r.getType().isTowerType() || (r.getTeam()!=rc.getTeam() && r.getType()!=UnitType.MOPPER)) continue;  //ignore towers (both teams) and enemies that aren't moppers
        if(r.getTeam()==rc.getTeam() && r.getLocation().distanceSquaredTo(destination)<=2){
          //allied crowding penalty
          directionScores[d.getDirectionOrderNum()]-=100;
          //rc.setIndicatorDot(destination,0,255,0);
        }else if(r.getTeam()!=rc.getTeam() && r.getType()==UnitType.MOPPER && r.getLocation().distanceSquaredTo(destination)<=13){
          //mopper in range of destination
          if(r.getLocation().distanceSquaredTo(currentLoc)<=5 && r.getLocation().distanceSquaredTo(destination)>r.getLocation().distanceSquaredTo(currentLoc)){
            //in this case, we are currently in a position where it's impossible to move outside of mopper range
            //it would be nice to move further away from it, but not crucial since we're getting hit either way
            directionScores[d.getDirectionOrderNum()]+=50;
          }
          directionScores[d.getDirectionOrderNum()]-=100;
          //rc.setIndicatorDot(destination,255,0,0);
        }
      }
    }

    //calculate the direction to the nearest allied paint
    if(!alliedPaintInMoveRange){
      MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
      for(MapInfo m : nearbyTiles){
        if(currentLoc.distanceSquaredTo(m.getMapLocation())<=2) continue; //we already checked these in previous scoring
        if(m.getPaint().isAlly()){
          //(20 - distance to allied paint) will give us a good, small score to add. Note that it will never be greater than any other single score addition, so it's essentially just a tiebreaker
          directionScores[currentLoc.directionTo(m.getMapLocation()).getDirectionOrderNum()]+=(20-currentLoc.distanceSquaredTo(m.getMapLocation()));
        }
        if(Clock.getBytecodesLeft()<500) break; //this for loop may get costly
      }
    }

    //another tiebreaker: distance to nearest allied tower:
    if(Clock.getBytecodesLeft()>500){
      MapLocation nearestAlliedTower = MapData.closestFriendlyTower();
      if (nearestAlliedTower != null) {
        directionScores[currentLoc.directionTo(nearestAlliedTower).getDirectionOrderNum()] += 10;
      }
    }

    //now pick the best direction
    Direction bestDirection = Direction.CENTER; // Start with center, guaranteed can move
    if (directionScores[0]>(directionScores[bestDirection.getDirectionOrderNum()])) { bestDirection = Direction.DIRECTION_ORDER[0]; }
    if (directionScores[1]>(directionScores[bestDirection.getDirectionOrderNum()])) { bestDirection = Direction.DIRECTION_ORDER[1]; }
    if (directionScores[2]>(directionScores[bestDirection.getDirectionOrderNum()])) { bestDirection = Direction.DIRECTION_ORDER[2]; }
    if (directionScores[3]>(directionScores[bestDirection.getDirectionOrderNum()])) { bestDirection = Direction.DIRECTION_ORDER[3]; }
    if (directionScores[4]>(directionScores[bestDirection.getDirectionOrderNum()])) { bestDirection = Direction.DIRECTION_ORDER[4]; }
    if (directionScores[5]>(directionScores[bestDirection.getDirectionOrderNum()])) { bestDirection = Direction.DIRECTION_ORDER[5]; }
    if (directionScores[6]>(directionScores[bestDirection.getDirectionOrderNum()])) { bestDirection = Direction.DIRECTION_ORDER[6]; }
    if (directionScores[7]>(directionScores[bestDirection.getDirectionOrderNum()])) { bestDirection = Direction.DIRECTION_ORDER[7]; }
    if (directionScores[8]>(directionScores[bestDirection.getDirectionOrderNum()])) { bestDirection = Direction.DIRECTION_ORDER[8]; }

    /*
    String indicatorString = "Loc "+rc.getLocation().toString()+"\n";
    for(int i = 0; i<9; ++i){
      indicatorString += Direction.DIRECTION_ORDER[i].toString()+": "+directionScores[i] + "\n";
    }
    rc.setIndicatorString(indicatorString);
    */

    //set goal to the best direction
    GoalManager.replaceTopGoal(Goal.Type.SURVIVE,currentLoc.add(bestDirection));
    //rc.setIndicatorDot(currentLoc.add(bestDirection),0,0,255);
    if(rc.canMove(bestDirection)){
      rc.move(bestDirection);
      //paint tile we want to go to if we never saw any allied paint nearby
      if(!alliedPaintInMoveRange && rc.getPaint()>7 && rc.canAttack(currentLoc.add(bestDirection))){
        rc.attack(currentLoc.add(bestDirection));
      }
    }
  }
}
