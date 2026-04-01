package frontReset.util;

import frontReset.*;

import battlecode.common.*;

/**
 * A class which contains the robots knowledge of the map. This only contains static (unchanging)
 * data.
 */
public class MapData {

  public static int MAP_WIDTH;
  public static int MAP_HEIGHT;
  public static MapLocation MAP_CENTER;

  public static int MAX_DISTANCE_SQ;

  private static final int EXPLORE_CHUNK_SIZE = 5;

  // TODO: Switch to using GameConstants int
  private static boolean[][] SRP_ARRAY;
  private static boolean[][] PAINT_ARRAY;
  private static boolean[][] MONEY_ARRAY;
  private static boolean[][] DEFENSE_ARRAY;
  
  public static String activeFronts;
  public static String inactiveFronts;

  public static MapLocation foundSRP = null; // TODO: REMOVE THIS TEMPORARY WORKAROUND

  public static int symmetryType     = 0b111;
  public static final int ROTATIONAL = 0b001;
  public static final int HORIZONTAL = 0b010;
  public static final int VERTICAL   = 0b100;
  public static boolean hasCommedSymmetry = false;

  private static int[] knownRuins;
  private static int ruinIndex;

  private static int[] mapData;
  private static final int UNKNOWN = 0b0;

  // Bits 0-1: Immutable characteristics
  private static final int EMPTY             = 0b01;
  private static final int RUIN              = 0b10;
  private static final int WALL              = 0b11;
  private static final int TILE_TYPE_BITMASK = 0b11;
  
  // Bits 2-4: Tower Type Data (Only applicable for ruins)
  private static final int UNCLAIMED_RUIN     = 0b001_00;
  private static final int MONEY_TOWER        = 0b010_00;
  private static final int PAINT_TOWER        = 0b011_00;
  private static final int DEFENSE_TOWER      = 0b100_00;
  private static final int TOWER_TYPE_BITMASK = 0b111_00;

  // Bit 5: Friendly = 1, foe = 0 (Only applicable for claimed ruins)
  private static final int FRIENDLY_TOWER = 0b1_000_00;

  // Bits 6-16: Last round updated (Only applicable for ruins)
  private static final int LAST_UPDATED_BITMASK = 0b11111111111_0_000_00;
  private static final int LAST_UPDATED_BITSHIFT = 6;

  // Bits 17-18: Paint status of tiles (Only applicable for empty)
  // TODO: UPDATE THESE VALUES AND USE THEM?
  // private static final int ENEMY_PAINT        = 0b01_00000000000_0_000_00;
  // private static final int FRIENDLY_PRIMARY   = 0b10_00000000000_0_000_00;
  // private static final int FRIENDLY_SECONDARY = 0b11_00000000000_0_000_00;
  // private static final int PAINT_BITMASK      = 0b11_00000000000_0_000_00;

  // Bits 19-20: Goal Tower Type
  private static final int GOAL_MONEY_TOWER   = 0b01_00_00000000000_0_000_00;
  private static final int GOAL_PAINT_TOWER   = 0b10_00_00000000000_0_000_00;
  private static final int GOAL_DEFENSE_TOWER = 0b11_00_00000000000_0_000_00;
  private static final int GOAL_TOWER_BITMASK = 0b11_00_00000000000_0_000_00;

  // Bit 21: Goal Paint Color
  private static final int GOAL_SECONDARY_PAINT = 0b001_00_00_00000000000_0_000_00;
  private static final int GOAL_COLOR_KNOWN     = 0b010_00_00_00000000000_0_000_00;
  private static final int GOAL_COLOR_CANDIDATE = 0b100_00_00_00000000000_0_000_00;

  // Bit 22: Contested
  private static final int CONTESTED_TARGET = 0b1_000_00_00_00000000000_0_000_00;

  public static void init() throws GameActionException {
    MAP_WIDTH = Robot.rc.getMapWidth();
    MAP_HEIGHT = Robot.rc.getMapHeight();
    MAP_CENTER = new MapLocation(MAP_WIDTH / 2, MAP_HEIGHT / 2);
    MAX_DISTANCE_SQ = MAP_WIDTH * MAP_WIDTH + MAP_HEIGHT * MAP_HEIGHT;

    SRP_ARRAY = Robot.rc.getResourcePattern();
    PAINT_ARRAY = Robot.rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER);
    MONEY_ARRAY = Robot.rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER);
    DEFENSE_ARRAY = Robot.rc.getTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER);

    mapData = new int[MAP_WIDTH * MAP_HEIGHT];
    knownRuins = new int[(MAP_WIDTH / GameConstants.PATTERN_SIZE + 1) * (MAP_HEIGHT / GameConstants.PATTERN_SIZE + 1)];

    activeFronts = "";
    inactiveFronts = "";
  }

  public static void incorporateSymmetryInfo(int info) {
    symmetryType &= info;
  }

  /**
   * Checks all visible squares around the robot and adds their information to the
   * mapData grid.
   */
  public static void updateAllVisible() throws GameActionException {
    foundSRP = null;
    // Update the towers first to save bytecode
    for (RobotInfo info : Robot.rc.senseNearbyRobots(GameConstants.BUILD_ROBOT_RADIUS_SQUARED, Robot.TEAM)) {
      if (!info.getType().isTowerType()) { continue; }
      updateData(Robot.rc.senseMapInfo(info.getLocation()), false);
      break;
    }
    for (MapInfo info : Robot.rc.senseNearbyMapInfos()) {
      updateData(info, true);
    }
  }

  /**
   * Checks visible ruins and updates their data
   * @throws GameActionException
   */
  public static void updateVisibleRuins() throws GameActionException {
    for (int i = 0; i < ruinIndex; ++i) {
      MapLocation loc = getLoc(knownRuins[i]);
      if (Robot.rc.canSenseLocation(loc)) {
        updateData(Robot.rc.senseMapInfo(loc));
      }
    }
  }
  
  /**
   * Updates only the newly visible squares and updates them to the mapData grid
   * @param lastDir The Direction the robot just moved
   */
  public static void updateNewlyVisible(Direction lastDir) throws GameActionException {
    foundSRP = null;
    if (lastDir == Direction.CENTER) { return; }
    // This needs to be updated if the vision radius changes
    assert GameConstants.VISION_RADIUS_SQUARED == 20;
    
    // Manually update the other newly visible squares
    MapLocation current = Robot.rc.getLocation();
    Direction leftDir = lastDir.rotateLeft().rotateLeft();
    Direction rightDir = lastDir.rotateRight().rotateRight();
    // Cardinal direction
    if (lastDir.dx == 0 ^ lastDir.dy == 0) {
      MapLocation center = current.translate(4 * lastDir.dx, 4 * lastDir.dy);
      MapLocation leftLoc = center.add(leftDir);
      MapLocation rightLoc = center.add(rightDir);
      if (Robot.rc.onTheMap(center)) { updateData(Robot.rc.senseMapInfo(center)); }
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftDir = leftDir.rotateLeft();
      rightDir = rightDir.rotateRight();
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
    } else { // Diagonal direction
      MapLocation center = current.translate(3 * lastDir.dx, 3 * lastDir.dy);
      MapLocation leftLoc = center.add(leftDir);
      MapLocation rightLoc = center.add(rightDir);
      if (Robot.rc.onTheMap(center)) { updateData(Robot.rc.senseMapInfo(center)); }
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftDir = leftDir.rotateLeft();
      rightDir = rightDir.rotateRight();
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      leftLoc = leftLoc.add(leftDir);
      rightLoc = rightLoc.add(rightDir);
      if (Robot.rc.onTheMap(leftLoc)) { updateData(Robot.rc.senseMapInfo(leftLoc)); }
      if (Robot.rc.onTheMap(rightLoc)) { updateData(Robot.rc.senseMapInfo(rightLoc)); }
      if (Robot.rc.onTheMap(center.add(leftDir))) { updateData(Robot.rc.senseMapInfo(center.add(leftDir))); }
      if (Robot.rc.onTheMap(center.add(rightDir))) { updateData(Robot.rc.senseMapInfo(center.add(rightDir))); }
    }
  }

  /**
   * Takes the info for a single space and updates the robot's knowledge with
   * that information.
   * 
   * @param info The `MapInfo` of the tile to be updated
   */
  public static void updateData(MapInfo info) throws GameActionException { updateData(info, false); }

  /**
   * Takes the info for a single space and updates the robot's knowledge with
   * that information.
   * 
   * @param info The `MapInfo` of the tile to be updated
   * @param cheap Whether we want to skip the expensive portions (e.g. updateAllVisible)
   */
  public static void updateData(MapInfo info, boolean cheap) throws GameActionException {
    // Update this square
    MapLocation loc = info.getMapLocation();
    //Robot.rc.setIndicatorDot(loc, 255, 0, 0);
    int index = getIndex(loc);

    // First time seeing square
    if (mapData[index] == 0) {
      if (info.hasRuin()) {
        mapData[index] = (RUIN | UNCLAIMED_RUIN);
        knownRuins[ruinIndex++] = index;
          setGoalTowerType(index, UnitType.LEVEL_ONE_MONEY_TOWER);
      }
      else if (info.isWall()) { mapData[index] = WALL; }
      else { mapData[index] = EMPTY; }
      
      // If symmetry is not known, try to figure it out
      if (!symmetryKnown() && !cheap) {
        if ((symmetryType & HORIZONTAL) > 0) {
          int h_index = symmetryIndex(index, HORIZONTAL);
          if (mapData[h_index] != UNKNOWN && mapData[h_index] != mapData[index]) {
            symmetryType ^= HORIZONTAL;
          }
        }
        if ((symmetryType & VERTICAL) > 0) {
          int v_index = symmetryIndex(index, VERTICAL);
          if (mapData[v_index] != UNKNOWN && mapData[v_index] != mapData[index]) {
            symmetryType ^= VERTICAL;
          }
        }
        if ((symmetryType & ROTATIONAL) > 0) {
          int r_index = symmetryIndex(index, ROTATIONAL);
          if (mapData[r_index] != UNKNOWN && mapData[r_index] != mapData[index]) {
            symmetryType ^= ROTATIONAL;
          }
        }
      }
    }

    // Update the last seen info
    mapData[index] &= ~LAST_UPDATED_BITMASK;
    mapData[index] |= Robot.rc.getRoundNum() << LAST_UPDATED_BITSHIFT;
      
    // Copy data over symmetrically
    if (symmetryKnown()) {
      int symIndex = symmetryIndex(index, symmetryType);
      if (symIndex == UNKNOWN) {
        mapData[symIndex] = mapData[index] & TILE_TYPE_BITMASK;
        if (info.hasRuin()) { knownRuins[ruinIndex++] = index; }
      }
    }

    if (cheap) { return; }
    
    // If it is a ruin or empty, more info can be gathered
    if ((mapData[index] & TILE_TYPE_BITMASK) == RUIN) {
      //Robot.rc.setIndicatorDot(loc, 0, 255, 0);
      RobotInfo towerInfo = Robot.rc.senseRobotAtLocation(loc);
      mapData[index] &= ~TOWER_TYPE_BITMASK;
      if (towerInfo != null) {
        mapData[index] |= switch (towerInfo.getType().getBaseType()) {
          case UnitType.LEVEL_ONE_DEFENSE_TOWER -> DEFENSE_TOWER;
          case UnitType.LEVEL_ONE_PAINT_TOWER -> PAINT_TOWER;
          case UnitType.LEVEL_ONE_MONEY_TOWER -> MONEY_TOWER;
          default -> 0;
        };
        if (towerInfo.getTeam().equals(Robot.TEAM)) { 
          mapData[index] |= FRIENDLY_TOWER;
        }
        if ((mapData[index] & GOAL_TOWER_BITMASK) == 0) {
          setGoalTowerType(index, towerInfo.getType().getBaseType());
        }
      } else {
        mapData[index] |= UNCLAIMED_RUIN;
        // TODO: Put this elsewhere and add logic for different tower types
        // setGoalTowerType(index, UnitType.LEVEL_ONE_MONEY_TOWER);
      }
    } else if ((mapData[index] & TILE_TYPE_BITMASK) == EMPTY) {
      if (info.isResourcePatternCenter()) {
        markSRP(loc, false);
        foundSRP = loc;
      } else {
        switch (info.getMark()) {
          case ALLY_PRIMARY: // Candidate SRP
            markSRP(loc, false);
            foundSRP = loc;
            break;
          default: break;
        }
      }
    } 
    
    // else if ((mapData[index] & TILE_TYPE_BITMASK) == EMPTY) {
      // TODO: Decide if it is worth it to do this
      // PaintType paint = info.getPaint();
      // mapData[index] &= ~PAINT_BITMASK;
      // if (paint.isEnemy()) {
      //   mapData[index] |= ENEMY_PAINT;
      // } else if (paint.isAlly()) {
      //   mapData[index] |= paint.isSecondary() ? FRIENDLY_SECONDARY : FRIENDLY_PRIMARY;
      // }
  }

  /**
   * Determines whether a given location is passable. Will return true if:
   * - The location does NOT have a ruin (or tower) or wall
   * - The location is unknown
   * @param loc The location to check passability of
   * @return Whether the location is passable or not
   */
  public static boolean passable(MapLocation loc) { return (readData(loc) & TILE_TYPE_BITMASK) < RUIN; }

  /**
   * Determines whether a given location is known
   * 
   * @param loc The location to check knowledge of
   * @return Whether that location is known or not
   */
  public static boolean known(MapLocation loc) { return readData(loc) != UNKNOWN; }

  /**
   * Gets the index of the value in the `mapData` array corresponding to the location
   * @param loc The `MapLocation` to find the index of
   * @return The index in the `mapData` array of `loc`
   */
  private static int getIndex(MapLocation loc) { return getIndex(loc.x, loc.y); }

  /**
   * Gets the index of the value in the `mapData` array corresponding to the location
   * @param x The x coordinate to find the index for
   * @param y The y coordinate to find the index for
   * @return The index in the `mapData` array of `loc`
   */
  private static int getIndex(int x, int y) { return x + y * MAP_WIDTH; }

  /**
   * Gets the x coordinate associated with the given index
   * @param index The index in the `mapData` array
   * @return The x coordinate of the corresponding location
   */
  private static int getX(int index) { return index % MAP_WIDTH; }

  /**
   * Gets the y coordinate associated with the given index
   * @param index The index in the `mapData` array
   * @return The y coordinate of the corresponding location
   */
  private static int getY(int index) { return index / MAP_WIDTH; }

  /**
   * Gets the `MapLocation` corresponding to the index in `mapData`
   * @param index The position in the `mapData` array
   * @return The corresponding `MapLocation`
   */
  private static MapLocation getLoc(int index) { return new MapLocation(getX(index), getY(index)); }

  /**
   * Gets the `mapData` value associated with the given `MapLocation`
   * 
   * @param loc The `MapLocation` to get the value for
   * @return The value representing the known information about that square
   */
  private static int readData(MapLocation loc) { return mapData[getIndex(loc)]; }

  /**
   * Gets the `mapData` value associated with the given coordinates
   * @param x The x coordinate
   * @param y The y coordinate
   * @return The value representing the known information about that square
   */
  private static int readData(int x, int y) { return mapData[getIndex(x, y)]; }

  /**
   * Checks whether the symmetry of the map is known
   *  
   * @return Whether the symmetry is known or not
   */
  public static boolean symmetryKnown() { return (symmetryType == 1) || (symmetryType & (symmetryType - 1)) == 0; }

  /**
   * Returns the location of the symmetrically paired tile on the map
   * @param loc The starting position to find the pair for
   * @param symmetryType The type of symmetry
   * @return The position of the symmetric value
   */
  public static MapLocation symmetryLoc(MapLocation loc, int symmetryType) {
    return switch (symmetryType) {
      case HORIZONTAL -> new MapLocation(loc.x, MAP_HEIGHT - (loc.y + 1));
      case VERTICAL -> new MapLocation(MAP_WIDTH - (loc.x + 1), loc.y);
      case ROTATIONAL -> new MapLocation(MAP_WIDTH - (loc.x + 1), MAP_HEIGHT - (loc.y + 1));
      default -> null; 
    };
  }

  /**
   * Returns the index of the symmetrically paired tile on the map
   * @param index The starting index to find the pair for
   * @param symmetryType The type of symmetry
   * @return The position of the symmetric value
   */
  private static int symmetryIndex(int index, int symmetryType) {
    int x = getX(index);
    int y = getY(index);
    return switch (symmetryType) {
      case HORIZONTAL -> x + (MAP_HEIGHT - (y + 1)) * MAP_WIDTH;
      case VERTICAL -> MAP_WIDTH - (x + 1) + y * MAP_WIDTH;
      case ROTATIONAL -> MAP_WIDTH - (x + 1) + (MAP_HEIGHT - (y + 1)) * MAP_WIDTH;
      default -> -1;
    };
  }

  /**
   * Returns the closest known ruin (claimed or unclaimed) to the robot
   * @return The location of the closest known ruin
   */
  public static MapLocation closestRuin() {
    if (ruinIndex == 0) { return null; }
    MapLocation current = Robot.rc.getLocation();
    MapLocation closestRuin = getLoc(knownRuins[0]);
    int closestDist = current.distanceSquaredTo(closestRuin);
    for (int i = 1; i < ruinIndex; ++i) {
      MapLocation ruinLoc = getLoc(knownRuins[i]);
      int ruinDist = current.distanceSquaredTo(ruinLoc);
      if (ruinDist < closestDist) {
        closestDist = ruinDist;
        closestRuin = ruinLoc;
      }
    }
    return closestRuin;
  }

  /**
   * Returns the closest known unclaimed (or unknown if claimed) ruin to the robot
   * @return The location of the closest known unclaimed ruin
   */
  public static MapLocation closestUnclaimedRuin() {
    if (ruinIndex == 0) { return null; }
    MapLocation current = Robot.rc.getLocation();
    MapLocation closestTower = null;
    int closestDist = 0;
    for (int i = 0; i < ruinIndex; ++i) {
      if ((mapData[knownRuins[i]] & TOWER_TYPE_BITMASK) > UNCLAIMED_RUIN) { continue; }
      MapLocation towerLoc = getLoc(knownRuins[i]);
      int ruinDist = current.distanceSquaredTo(towerLoc);
      if (closestTower == null || ruinDist < closestDist) {
        closestTower = towerLoc;
        closestDist = ruinDist;
      }
    }
    return closestTower;
  }

  /**
   * Sets the location of a ruin to be contested, and returns whether this made a change or not
   * 
   * CURRENTLY THIS WILL ONLY DO ANYTHING IF THE GIVEN LOCATION IS THE LOCATION OF A RUIN
   * 
   * @param loc The location of the ruin to mark as contested
   * @return Whether this is different from the previous value or not
   */
  public static boolean setContested(MapLocation loc) {
    int index = getIndex(loc);
    if (isContested(index)) { return false; }
    mapData[index] |= CONTESTED_TARGET;
    return true;
  }

  /**
   * Sets the location of a ruin to be uncontested, and returns whether this made a change or not
   * 
   * CURRENTLY THIS WILL ONLY DO ANYTHING IF THE GIVEN LOCATION IS THE LOCATION OF A RUIN
   * 
   * @param loc The location of the ruin to mark as contested
   * @return Whether this is different from the previous value or not
   */
  public static boolean setUncontested(MapLocation loc) {
    int index = getIndex(loc);
    if (!isContested(index)) { return false; }
    mapData[index] &= ~CONTESTED_TARGET;
    return true;
  }

  /**
   * Checks whether the given location is contested
   * 
   * CURRENTLY THIS WILL ONLY DO ANYTHING IF THE GIVEN LOCATION IS THE LOCATION OF A RUIN
   * @param loc The location of the ruin to check
   * @return Whether it is contested or not
   */
  public static boolean isContested(MapLocation loc) { return isContested(getIndex(loc)); }

  /**
   * Checks whether the given location is contested
   * 
   * CURRENTLY THIS WILL ONLY DO ANYTHING IF THE GIVEN LOCATION IS THE LOCATION OF A RUIN
   * @param index The index in `mapData` of the ruin to check
   * @return Whether it is contested or not
   */
  private static boolean isContested(int index) { return (mapData[index] & CONTESTED_TARGET) > 0; }

  /**
   * Returns the closest known contested / uncontested unclaimed ruin to the robot
   * @return The location of the closest known uncontested ruin
   */
  public static MapLocation closestUnclaimedRuin(boolean contested) {
    if (ruinIndex == 0) { return null; }
    MapLocation current = Robot.rc.getLocation();
    MapLocation closestRuin = null;
    int closestDist = 0;
    for (int i = 0; i < ruinIndex; ++i) {
      if ((mapData[knownRuins[i]] & TOWER_TYPE_BITMASK) > UNCLAIMED_RUIN ||
          ((mapData[knownRuins[i]] & CONTESTED_TARGET) > 0) != contested) { continue; }
      MapLocation ruinLoc = getLoc(knownRuins[i]);
      int ruinDist = current.distanceSquaredTo(ruinLoc);
      if (closestRuin == null || ruinDist < closestDist) {
        closestRuin = ruinLoc;
        closestDist = ruinDist;
      }
    }
    return closestRuin;
  }

  /**
   * Checks whether the given location has a friendly tower on it
   */
  public static boolean isFriendlyTower(MapLocation loc) { return isFriendlyTower(getIndex(loc)); }

  /**
   * Checks whether the given location has a friendly tower on it
   */
  private static boolean isFriendlyTower(int index) {
    if ((mapData[index] & TOWER_TYPE_BITMASK) == 0) { return false; }
    if ((mapData[index] & TOWER_TYPE_BITMASK) == UNCLAIMED_RUIN) { return false; }
    if ((mapData[index] & FRIENDLY_TOWER) == 0) { return false; }
    return true;
  }
  
  /**
   * Returns the closest known friendly tower to the robot
   * @return The location of the closest known friendly tower
   */
  public static MapLocation closestFriendlyTower() {
    if (ruinIndex == 0) { return null; }
    MapLocation current = Robot.rc.getLocation();
    MapLocation closestTower = null;
    int closestDist = 0;
    for (int i = 0; i < ruinIndex; ++i) {
      if (!isFriendlyTower(knownRuins[i])) { continue; }
      MapLocation towerLoc = getLoc(knownRuins[i]);
      int ruinDist = current.distanceSquaredTo(towerLoc);
      if (closestTower == null || ruinDist < closestDist) {
        closestTower = towerLoc;
        closestDist = ruinDist;
      }
    }
    return closestTower;
  }

  /**
   * Returns the closest known friendly tower that isn't in the "ignore" list
   * @return The loctaion of the closest known friendly tower that fits
   */
  public static MapLocation closestFriendlyTower(String ignore) {
    if (ruinIndex == 0) { return null; }
    MapLocation current = Robot.rc.getLocation();
    MapLocation closestTower = null;
    int closestDist = 0;
    for (int i = 0; i < ruinIndex; ++i) {
      try {
          Robot.rc.setIndicatorLine(Robot.rc.getLocation(), getLoc(knownRuins[i]), 255, 255, 0);
      } catch (Exception e) {
        
      }
      if (!isFriendlyTower(knownRuins[i]) || ignore.contains(getLoc(knownRuins[i]).toString())) { continue; }
      try {
          Robot.rc.setIndicatorLine(Robot.rc.getLocation(), getLoc(knownRuins[i]), 0, 255, 255);
      } catch (Exception e) {
        
      }
      MapLocation towerLoc = getLoc(knownRuins[i]);
      int ruinDist = current.distanceSquaredTo(towerLoc);
      if (closestTower == null || ruinDist < closestDist) {
        closestTower = towerLoc;
        closestDist = ruinDist;
      }
    }
    return closestTower;
  }

  /**
   * Returns the closest known enemy tower to the robot
   * @return The location of the closest known enemy tower
   */
  public static MapLocation closestEnemyTower() {
    if (ruinIndex == 0) { return null; }
    MapLocation current = Robot.rc.getLocation();
    MapLocation closestTower = null;
    int closestDist = 0;
    for (int i = 0; i < ruinIndex; ++i) {
      if ((mapData[knownRuins[i]] & TOWER_TYPE_BITMASK) == 0) { continue; }
      if ((mapData[knownRuins[i]] & TOWER_TYPE_BITMASK) == UNCLAIMED_RUIN) { continue; }
      if ((mapData[knownRuins[i]] & FRIENDLY_TOWER) > 0) { continue; }
      MapLocation towerLoc = getLoc(knownRuins[i]);
      int ruinDist = current.distanceSquaredTo(towerLoc);
      if (closestTower == null || ruinDist < closestDist) {
        closestTower = towerLoc;
        closestDist = ruinDist;
      }
    }
    return closestTower;
  }

  /**
   * Returns the goal paint color of the given tile
   * @param loc The location to paint
   * @return True if should use secondary color
   */
  public static boolean useSecondaryPaint(MapLocation loc) {
    return knownPaintColor(loc) &&
           (readData(loc) & (GOAL_SECONDARY_PAINT)) > 0;
  }

  /**
   * Returns whether the goal color of the paint is known
   * @param loc The location of the given tile
   * @return True if it is known which color to use
   */
  public static boolean knownPaintColor(MapLocation loc) {
    return (readData(loc) & (GOAL_COLOR_KNOWN | GOAL_COLOR_CANDIDATE)) != 0;
  }

  /**
   * Sets the goal tower type for a ruin. Returns whether this was successful
   * @param loc The location of the ruin
   * @param towerType The goal type of the tower to be painted
   * @return Whether this was successfully set
   */
  public static boolean setGoalTowerType(MapLocation loc, UnitType towerType) {
    UnitType baseType = towerType.getBaseType();
    boolean[][] pattern = switch (baseType) {
      case UnitType.LEVEL_ONE_PAINT_TOWER -> PAINT_ARRAY;
      case UnitType.LEVEL_ONE_MONEY_TOWER -> MONEY_ARRAY;
      case UnitType.LEVEL_ONE_DEFENSE_TOWER -> DEFENSE_ARRAY;
      default -> null;
    };
    // Check for valid arguments
    if (pattern == null) { return false; }
    // Set the goal tower type
    int towerIndex = getIndex(loc);
    mapData[towerIndex] &= ~GOAL_TOWER_BITMASK;
    mapData[towerIndex] |= switch (baseType) {
      case UnitType.LEVEL_ONE_PAINT_TOWER -> GOAL_PAINT_TOWER;
      case UnitType.LEVEL_ONE_MONEY_TOWER -> GOAL_MONEY_TOWER;
      case UnitType.LEVEL_ONE_DEFENSE_TOWER -> GOAL_DEFENSE_TOWER;
      default -> 0;
    };
    // Set the goal paint types - Unroll loop for bytecode
    int index = getIndex(loc.x - (GameConstants.PATTERN_SIZE / 2), loc.y - (GameConstants.PATTERN_SIZE / 2));
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[0][0]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[1][0]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[2][0]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[3][0]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[4][0]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[0][1]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[1][1]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[2][1]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[3][1]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[4][1]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[0][2]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[1][2]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    index += 2;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[3][2]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[4][2]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[0][3]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[1][3]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[2][3]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[3][3]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[4][3]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[0][4]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[1][4]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[2][4]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[3][4]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    ++index;
    mapData[index] |= (GOAL_COLOR_KNOWN | EMPTY);
    if (pattern[4][4]) { mapData[index] |= GOAL_SECONDARY_PAINT; }
    else { mapData[index] &= ~GOAL_SECONDARY_PAINT; }

    return true;
  }

  /**
   * Sets the goal tower type for a ruin. Returns whether this was successful
   * @param index The index of the location in mapData
   * @param towerType The goal type of the tower to be painted
   * @return Whether this was successfully set
   */
  private static boolean setGoalTowerType(int index, UnitType towerType) { return setGoalTowerType(getLoc(index), towerType); }

  public static UnitType getGoalTowerType(MapLocation loc) { return getGoalTowerType(getIndex(loc)); }

  private static UnitType getGoalTowerType(int index) {
    return switch (mapData[index] & GOAL_TOWER_BITMASK) {
      case GOAL_MONEY_TOWER -> UnitType.LEVEL_ONE_MONEY_TOWER;
      case GOAL_PAINT_TOWER -> UnitType.LEVEL_ONE_PAINT_TOWER;
      case GOAL_DEFENSE_TOWER -> UnitType.LEVEL_ONE_DEFENSE_TOWER;
      default -> UnitType.LEVEL_ONE_MONEY_TOWER;
    };
  }

  /**
   * Returns an explore target
   * Tries to randomize between a checker target (ie: the corners) and a normal target
   * @return MapLocation that is the explore target
   */
  public static MapLocation getExploreTarget(){
    if (Robot.rc.getID()%2 == 0) return getCheckerTarget();
    else return getEmergencyTarget();
  }

  /**
   * Returns the closest unknown center of a NxN chunk of the map
   * @return The closest unexplored center of a chunk
   */
  public static MapLocation getEmergencyTarget() {
    int startX = Robot.rng.nextInt(MAP_WIDTH / EXPLORE_CHUNK_SIZE - 1) * EXPLORE_CHUNK_SIZE + EXPLORE_CHUNK_SIZE / 2;
    int startY = Robot.rng.nextInt(MAP_HEIGHT / EXPLORE_CHUNK_SIZE - 1) * EXPLORE_CHUNK_SIZE + EXPLORE_CHUNK_SIZE / 2;

    //try { Robot.rc.setIndicatorDot(new MapLocation(startX, startY), 255, 255, 0); } catch (Exception e) { e.printStackTrace(); }
    
    int x = startX;
    int y = startY;
    
    do {
      if ((readData(x, y) & LAST_UPDATED_BITMASK) == 0) { return new MapLocation(x, y); }
      
      // Increment to next
      x += EXPLORE_CHUNK_SIZE;
      if (x >= MAP_WIDTH) {
        x -= MAP_WIDTH;
        y += EXPLORE_CHUNK_SIZE;
        if (y >= MAP_HEIGHT) {
          y -= MAP_WIDTH;
        }
        //try { Robot.rc.setIndicatorDot(new MapLocation(x, y), 0, 255, 255); } catch (Exception e) { e.printStackTrace(); }

      }
    } while (x != startX || y != startY);

    return MAP_CENTER;
  }

  /**
   * Returns a 'random' checker location (ie: the corners of the map)
   * @return The chosen MapLocation
   * @throws GameActionException
   */
    private static MapLocation getCheckerTarget(){
      int tries = 15;
      MapLocation[] checkLocs = new MapLocation[5];
      checkLocs[0] = new MapLocation(MAP_WIDTH/2,MAP_HEIGHT/2);
      checkLocs[1] = new MapLocation(0,0);
      checkLocs[2] = new MapLocation(MAP_WIDTH-1,0);
      checkLocs[3] = new MapLocation(0,MAP_HEIGHT-1);
      checkLocs[4] = new MapLocation(MAP_WIDTH-1,MAP_HEIGHT-1);
      //MapLocation myLoc = rc.getLocation();
      MapLocation exploreLoc = null;
      while (tries-- > 0){
          int checkerIndex = Robot.rng.nextInt(checkLocs.length);
          MapLocation newLoc = checkLocs[checkerIndex];
          if (known(newLoc)) continue;
          if (exploreLoc != null && Robot.rc.getLocation().distanceSquaredTo(exploreLoc) < Robot.rc.getLocation().distanceSquaredTo(newLoc)) continue;
          exploreLoc = newLoc;
      }
      if (exploreLoc == null) return getEmergencyTarget();
      return exploreLoc;
  }

  /**
   * Checks whether you can mark a Special Resource Pattern at the given
   * location and does if so
   * @param loc The location to mark
   * @return Whether the location was successfully marked or not
   * @throws GameActionException
   */
  public static boolean tryMarkSRP(MapLocation loc) throws GameActionException {
    if (canMarkSRP(loc)) {
      markSRP(loc);
      return true;
    }
    return false;
  }

  /**
   * Checks whether you can mark a special resource pattern at the given location
   * @param loc The location of interest
   * @return Whether we can mark a special resource pattern there
   * @throws GameActionException
   */
  private static boolean canMarkSRP(MapLocation loc) throws GameActionException {

    // Check if it can be marked
    if (!Robot.rc.canMarkResourcePattern(loc)) { return false; }

    // Check if someone already marked this
    MapInfo info = Robot.rc.senseMapInfo(loc);
    if (info.isResourcePatternCenter()) { return true; }
    PaintType mark = Robot.rc.senseMapInfo(loc).getMark();
    if (mark == PaintType.ALLY_PRIMARY) {
      return true;
    }

    // Check if there are possible ruin conflicts we can't see
    MapLocation checkLoc = loc.translate(-3, -4);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(-4, -3);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(3, -4);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(4, -3);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(3, 4);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(4, 3);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(-3, 4);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }
    checkLoc = loc.translate(-4, 3);
    if (Robot.rc.onTheMap(checkLoc) && (readData(checkLoc) & TILE_TYPE_BITMASK) == UNKNOWN) { return false; }

    // Check if there are any nearby marks we don't know about
    for (MapInfo nearby_info : Robot.rc.senseNearbyMapInfos()) {
      if (!nearby_info.getMark().isAlly()) { continue; }
      markSRP(nearby_info.getMapLocation(), false);
      // Robot.rc.setIndicatorDot(loc, 255, 0, 0);
      // foundSRP = loc;
    }

    // Check the goal paint for squares around it
    int index = getIndex(loc.x - (GameConstants.PATTERN_SIZE / 2), loc.y - (GameConstants.PATTERN_SIZE / 2));
    int tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; } // Wall, ruin, or unknown
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 && // Already chose another color
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[0][0])
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[1][0])
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[2][0]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[3][0]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[4][0]) 
    { return false; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[0][1]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[1][1])
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[2][1]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[3][1]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[4][1]) 
    { return false; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[0][2]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[1][2])
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[2][2]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[3][2]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[4][2]) 
    { return false; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[0][3]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[1][3])
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[2][3]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[3][3]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[4][3]) 
    { return false; }

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[0][4]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[1][4])
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[2][4]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[3][4]) 
    { return false; }

    ++index;
    tileData = mapData[index];
    if ((tileData & TILE_TYPE_BITMASK) != EMPTY) { return false; }
    if ((tileData & (GOAL_COLOR_CANDIDATE | GOAL_COLOR_KNOWN)) > 0 &&
        ((tileData & GOAL_SECONDARY_PAINT) > 0) != SRP_ARRAY[4][4]) 
    { return false; }
      
    return true;
  }

  private static void markSRP(MapLocation loc) throws GameActionException { markSRP(loc, true); }

  /**
   * Marks a special resource pattern at the given location
   * @param loc The location to mark
   * @param first Whether this is the first robot to mark the location
   */
  private static void markSRP(MapLocation loc, boolean first) throws GameActionException {
    if (first && Robot.rc.canSenseLocation(loc)) {
      PaintType mark = Robot.rc.senseMapInfo(loc).getMark();
      if (mark != PaintType.ALLY_PRIMARY) {
        Robot.rc.mark(loc, false);
      }
    }

    int index = getIndex(loc.x - (GameConstants.PATTERN_SIZE / 2), loc.y - (GameConstants.PATTERN_SIZE / 2));
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[0][0] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[1][0] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[2][0] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[3][0] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[4][0] ? GOAL_SECONDARY_PAINT : 0));

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[0][1] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[1][1] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[2][1] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[3][1] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[4][1] ? GOAL_SECONDARY_PAINT : 0));

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[0][2] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[1][2] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[2][2] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[3][2] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[4][2] ? GOAL_SECONDARY_PAINT : 0));

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[0][3] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[1][3] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[2][3] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[3][3] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[4][3] ? GOAL_SECONDARY_PAINT : 0));

    index += MAP_WIDTH - GameConstants.PATTERN_SIZE + 1;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[0][4] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[1][4] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[2][4] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[3][4] ? GOAL_SECONDARY_PAINT : 0));

    ++index;
    mapData[index] |= (GOAL_COLOR_CANDIDATE | (SRP_ARRAY[4][4] ? GOAL_SECONDARY_PAINT : 0));
  }

  public static int numActiveFronts() { return activeFronts.length() / 3; }

  public static MapLocation getActiveFrontAtIndex(int i) {
    if (i < numActiveFronts()) {
      return new MapLocation((int)activeFronts.charAt(3*i), (int)activeFronts.charAt(3*i+1));
    } else { return null; }
  }

  public static int numInactiveFronts() { return inactiveFronts.length() / 3; }

  public static MapLocation getInactiveFrontAtIndex(int i) {
    if (i < numInactiveFronts()) {
      return new MapLocation((int)inactiveFronts.charAt(3*i), (int)inactiveFronts.charAt(3*i+1));
    } else { return null; }
  }

  public static void addToFronts(MapLocation m) { addToFronts(m.x, m.y); }

  public static void addToFronts(int x, int y){
    String locString = "";
    locString +=(char)x;
    locString +=(char)y;
    locString +=(char)69;
    if(inactiveFronts.contains(locString)){
      inactiveFronts = inactiveFronts.replace(locString,""); //effectively removes locString from inactiveFronts
    }
    if(activeFronts.contains(locString)) return;
    activeFronts += locString;
  }

  public static void removeFromFronts(MapLocation m) { removeFromFronts(m.x, m.y); }

  public static void removeFromFronts(int x, int y){
    String locString = "";
    locString +=(char)x;
    locString +=(char)y;
    locString +=(char)69;
    if(activeFronts.contains(locString)){
      activeFronts = activeFronts.replace(locString,""); //effectively removes locString from activeFronts
    }
    if(inactiveFronts.contains(locString)) return;
    inactiveFronts += locString;
  }

  //fronts are just enemy towers (for simplicity)
  public static void lookForFrontsToAdd() throws GameActionException{
    //if(Clock.getBytecodesLeft()<500) return;
    MapLocation[] ruins = Robot.rc.senseNearbyRuins(-1);
    for(MapLocation ruin : ruins){
      RobotInfo tower = Robot.rc.senseRobotAtLocation(ruin);
      if(tower!=null && tower.getTeam()!=Robot.rc.getTeam()){
        addToFronts(ruin);
        Robot.rc.setIndicatorDot(ruin,0,255,255);
      }
    }
  }

  //check if an enemy tower is still at the location
  public static void analyzeExistingFronts() throws GameActionException{
    //see if we need to transition any of our active fronts to inactive
    MapLocation[] ruins = Robot.rc.senseNearbyRuins(-1);
    for(int i=0; i<activeFronts.length(); i+=3){
      MapLocation front = new MapLocation((int)activeFronts.charAt(i),(int)activeFronts.charAt(i+1));
      Robot.rc.setIndicatorLine(Robot.rc.getLocation(), front,0,0,0);
      if(!Robot.rc.canSenseLocation(front)) continue;
      if(Clock.getBytecodesLeft()<500) break;  //cut out early if we're out of bytecode
      //make sure we have good vision of the 5x5 block where the tower could be (MapLocation front could be noisy)
      if(Robot.rc.getLocation().distanceSquaredTo(front)>3) continue;
      //boolean activeFront = false;
      for(MapLocation ruin : ruins){
        RobotInfo towerInfo = Robot.rc.senseRobotAtLocation(ruin);
        if(towerInfo!=null && towerInfo.getType().isTowerType() && towerInfo.getTeam()!=Robot.rc.getTeam()){
          //activeFront = true;
          return;
        }
      }
      removeFromFronts(front);
    }
  }

  public static MapLocation getNearestFront() throws GameActionException{
    MapLocation currentLoc = Robot.rc.getLocation();
    MapLocation closestLoc = null;
    for(int i=0; i<activeFronts.length(); i+=3){
      MapLocation front = new MapLocation((int)activeFronts.charAt(i),(int)activeFronts.charAt(i+1));
      if(closestLoc==null || currentLoc.distanceSquaredTo(front)<currentLoc.distanceSquaredTo(closestLoc)){
        closestLoc = front;
      }
    }
    return closestLoc;
  }

  public static void resetFronts() { 
    activeFronts = "";
    inactiveFronts = "";
  }
}