package frontReset.util;

import frontReset.*;

import battlecode.common.*;

public class Communication {

  // Bits 0-3: Message Type
  public static final int MESSAGE_TYPE_BITMASK = 0b1111;
  // Bits 4-15: Coordinates (unless specified differently below)
  public static final int X_COORDINATE_MASK = 0b000000_111111_0000;
  public static final int X_COORDINATE_BITSHIFT = 4;
  public static final int Y_COORDINATE_MASK = 0b111111_000000_0000;
  public static final int Y_COORDINATE_BITSHIFT = 10;

  // TODO: IMPLEMENT THIS COMMUNICATION
  public static final int SYMMETRY_KNOWLEDGE = 0b0001; // ----------------------------

      // Bits 4-6: Symmetry Value
      public static final int SYMMETRY_VALUE_MASK = 0b111_0000;
      public static final int SYMMETRY_BITSHIFT = 4;

  // TODO: IMPLEMENT THIS COMMUNICATION
  public static final int REQUEST_MOPPER = 0b0010; // --------------------------------

      // Bits 4-15: Coordinates (specified above)

  public static final int SUICIDE = 0b0011; // ---------------------------------------

      // Bits 4-15: Coordinates (specified above)

  public static final int FRONT = 0b0100; // -----------------------------------------

      // 9 bits per coordinate, 8 for the 5x5 chunk, 1 for add / remove
      public static final int FRONT_LOC_BITMASK = 0b11111111; // Must be shifted
      public static final int LOC_1_BITSHIFT = 4;
      public static final int LOC_2_BITSHIFT = 13;
      public static final int LOC_3_BITSHIFT = 22;
      public static final int ADD_1_BITSHIFT = 12;
      public static final int ADD_2_BITSHIFT = 21;
      public static final int ADD_3_BITSHIFT = 30;

      public static int turnCount = 0;
      public static int turnSentActive = 0;
      public static int turnSentInactive = 0;

      public static int activeFrontsIndex = 0;
      public static int inactiveFrontsIndex = 0;


  /**
   * Extracts the message type from the given message
   * @param message The message to extract from
   * @return The type of the message
   */
  public static int getMessageType(int message) { return message & MESSAGE_TYPE_BITMASK; }

  public static void resetSentFronts() { turnCount = -1; }

  /**
   * Creates the fronts message from the next fronts which should be updated
   */
  public static int createFrontsMessage() {
    // Check if we need to reset the sent counter
    if (turnCount < Robot.rc.getRoundNum()) {
      turnCount = Robot.rc.getRoundNum();
      turnSentActive = 0;
      turnSentInactive = 0;
    }

    int activeTotal = MapData.numActiveFronts();
    int inactiveTotal = MapData.numInactiveFronts();

    // If we already sent all the messages, no need
    if (turnSentActive >= activeTotal && turnSentInactive >= inactiveTotal) { return 0; }

    // Create return values
    MapLocation loc1 = null;
    boolean add1 = false;
    MapLocation loc2 = null;
    boolean add2 = false;
    MapLocation loc3 = null;
    boolean add3 = false;

    // Want to stop after we have sent all fronts
    int activeSent = 0;
    int inactiveSent = 0;

    // Start from where we previously left off (or 0 if end / resize)
    int activeIndex = activeFrontsIndex;
    int inactiveIndex = inactiveFrontsIndex;

    // Which one should we try to send (always prefer active)
    boolean activePriority = (activeSent < activeTotal);

    // While there are still messages to send
    while (activeSent < activeTotal || inactiveSent < inactiveTotal) {
      // Grab the next location
      MapLocation loc = (activePriority ? MapData.getActiveFrontAtIndex(activeIndex) : MapData.getInactiveFrontAtIndex(inactiveIndex));
      if (loc == null) {
        System.out.println("Unreachable but just to be safe send 0");
        return 0;
      }

      // Put it in the list
      switch (activeSent + inactiveSent) {
        case 0: loc1 = loc; add1 = activePriority; break;
        case 1: loc2 = loc; add2 = activePriority; break;
        case 2: loc3 = loc; add3 = activePriority; break;
      }

      // Increment indices & counters
      if (activePriority) {
        ++activeSent;
        activeIndex = (activeIndex + 1) % activeTotal;
      } else {
        ++inactiveSent;
        inactiveIndex = (inactiveIndex + 1) % inactiveTotal;
      }

      // Alternate between the coordinate types
      if (activePriority) { // If currently active, only flip if more inactive to send
        activePriority = !(inactiveSent < inactiveTotal);
      } else { // If currently inactive, only flip if more active to send
        activePriority = (activeSent < activeTotal);
      }
    }

    // Store starting point for next round
    activeFrontsIndex = activeIndex;
    inactiveFrontsIndex = inactiveIndex;

    // Return the message
    return createFrontsMessage(loc1, add1, loc2, add2, loc3, add3);
  }

  public static int createFrontsMessage(boolean active) {
    // Check if we need to reset the sent counter
    if (turnCount < Robot.rc.getRoundNum()) {
      turnCount = Robot.rc.getRoundNum();
      turnSentActive = 0;
      turnSentInactive = 0;
    }

    // Don't send if we have sent all fronts
    int total = (active ? MapData.numActiveFronts() : MapData.numInactiveFronts());
    if ((active ? turnSentActive : turnSentInactive) > total) { return 0; }

    int sent = 0;
    int startIndex = (active ? activeFrontsIndex : inactiveFrontsIndex); 
    int index = startIndex;

    // Create return values
    MapLocation loc1 = null;
    MapLocation loc2 = null;
    MapLocation loc3 = null;

    // Stop after we have sent all fronts
    while (sent < total) {
      // Grab next location
      MapLocation loc = (active ? MapData.getActiveFrontAtIndex(index) : MapData.getInactiveFrontAtIndex(index));
      if (loc == null) {
        System.out.println("Unreachable but just to be safe send 0");
        return 0;
      }

      // Put it in the list
      switch (sent++) {
        case 0: loc1 = loc; break;
        case 1: loc2 = loc; break;
        case 3: loc3 = loc; break;
      }

      // Increment index
      index = (index + 1) % total;
    }

    // Store starting point for next round
    if (active) {
      activeFrontsIndex = index;
      inactiveFrontsIndex = index;
    }

    // Return the message
    return createFrontsMessage(loc1, active, loc2, active, loc3, active);
  }

  /**
   * Create Fronts message given all information
   */
  public static int createFrontsMessage(MapLocation loc1, boolean add1, MapLocation loc2, boolean add2, MapLocation loc3, boolean add3) {
    // Check if we need to reset the sent counter
    if (turnCount < Robot.rc.getRoundNum()) {
      turnCount = Robot.rc.getRoundNum();
      turnSentActive = 0;
      turnSentInactive = 0;
    }

    // If all are null, no message needed
    if (loc1 == null && loc2 == null && loc3 == null) { return 0; }

    // Update the sent counters
    if (add1) { ++turnSentActive; }
    else { ++turnSentInactive; }
    if (add2) { ++turnSentActive; }
    else { ++turnSentInactive; }
    if (add3) { ++turnSentActive; }
    else { ++turnSentInactive; }
    
    // Calculate the message
    int loc1bits = (loc1 == null ? FRONT_LOC_BITMASK : (loc1.x / 5) + (loc1.y / 5) * 12);
    int loc2bits = (loc2 == null ? FRONT_LOC_BITMASK : (loc2.x / 5) + (loc2.y / 5) * 12);
    int loc3bits = (loc3 == null ? FRONT_LOC_BITMASK : (loc3.x / 5) + (loc3.y / 5) * 12);

    int add1bits = (add1 ? 1 : 0);
    int add2bits = (add2 ? 1 : 0);
    int add3bits = (add3 ? 1 : 0);

    return ((add3bits << ADD_3_BITSHIFT) | (loc3bits << LOC_3_BITSHIFT) | 
            (add2bits << ADD_2_BITSHIFT) | (loc2bits << LOC_2_BITSHIFT) | 
            (add1bits << ADD_1_BITSHIFT) | (loc1bits << LOC_1_BITSHIFT) | FRONT);
  }

  /**
   * Update Fronts From Message
   */
  public static void updateFronts(int message) {
    int loc1bits = (message >> LOC_1_BITSHIFT) & FRONT_LOC_BITMASK;
    int loc2bits = (message >> LOC_2_BITSHIFT) & FRONT_LOC_BITMASK;
    int loc3bits = (message >> LOC_3_BITSHIFT) & FRONT_LOC_BITMASK;
    
    if (loc1bits != FRONT_LOC_BITMASK) {
      if ((message & (1 << ADD_1_BITSHIFT)) > 0) {
        MapData.addToFronts((loc1bits % 12) * 5 + 2, (loc1bits / 12) * 5 + 2);
      } else {
        MapData.removeFromFronts((loc1bits % 12) * 5 + 2, (loc1bits / 12) * 5 + 2);
      }
    }
    if (loc2bits != FRONT_LOC_BITMASK) {
      if ((message & (1 << ADD_2_BITSHIFT)) > 0) {
        MapData.addToFronts((loc2bits % 12) * 5 + 2, (loc2bits / 12) * 5 + 2);
      } else {
        MapData.removeFromFronts((loc2bits % 12) * 5 + 2, (loc2bits / 12) * 5 + 2);
      }
    }
    if (loc3bits != FRONT_LOC_BITMASK) {
      if ((message & (1 << ADD_3_BITSHIFT)) > 0) {
        MapData.addToFronts((loc3bits % 12) * 5 + 2, (loc3bits / 12) * 5 + 2);
      } else {
        MapData.removeFromFronts((loc3bits % 12) * 5 + 2, (loc3bits / 12) * 5 + 2);
      }
    }
  }

  /**
   * Extracts the coordinates from the given message
   * @param message The message to extract from
   * @return The location encoded in the message
   */
  public static MapLocation getCoordinates(int message) { 
    return new MapLocation((message & X_COORDINATE_MASK) >> X_COORDINATE_BITSHIFT, 
                           (message & Y_COORDINATE_MASK) >> Y_COORDINATE_BITSHIFT);
  }

  /**
   * Adds coordinate information to a message
   * @param message The previous value of the message
   * @param loc The location to add to the message
   * @return The successfully created message
   */
  public static int addCoordinates(int message, MapLocation loc) { return addCoordinates(message, loc.x, loc.y); }

  /**
   * Adds coordinate information to a message
   * @param message The previous value of the message
   * @param x The x coordinate to add to the message
   * @param y The y coordinate to add to the message
   * @return The successfully created message
   */
  public static int addCoordinates(int message, int x, int y) {
    message &= ~(X_COORDINATE_MASK | Y_COORDINATE_MASK); // Clear previous coordinates
    message |= (x << X_COORDINATE_BITSHIFT) | (y << Y_COORDINATE_BITSHIFT);
    return message;
  }
  
  public static int makeSymmetryMessage() {
    return (MapData.symmetryType << SYMMETRY_BITSHIFT) | SYMMETRY_KNOWLEDGE;
  }

  public static int readSymmetryValue(int message) {
    return (message & SYMMETRY_VALUE_MASK) >> SYMMETRY_BITSHIFT;  
  }

  /**
   * Tries to send the given message to the given target
   * @param message The message to be sent to the target
   * @param target The target location to send the message
   * @return Whether the message was sent or not
   * @throws GameActionException
   */
  public static boolean trySendMessage(int message, MapLocation target) throws GameActionException {
    if (message == 0) { return false; }
    if (!Robot.rc.canSenseRobotAtLocation(target) || !Robot.rc.canSendMessage(target)) { return false; }
    Robot.rc.setIndicatorLine(Robot.rc.getLocation(), target, 255, 200, 200);
    Robot.rc.setIndicatorDot(Robot.rc.getLocation(), 255, 200, 200);
    Robot.rc.sendMessage(target, message);
    return true;
  }

  /**
   * Tries to send the given message to all of the given targets
   * @param message The message to be sent to the targets
   * @param targets The potential recipients of the message
   * @return Whether anyone was successfully sent the message
   * @throws GameActionException
   */
  public static boolean trySendAllMessage(int message, RobotInfo[] targets) throws GameActionException {
    if (message == 0) { return false; }
    boolean success = false;
    for (RobotInfo robot : targets) {
      success = trySendMessage(message, robot.getLocation()) || success;
    }
    return success;
  }

  public static boolean tryBroadcastMessage(int message) throws GameActionException {
    if (message == 0) { return false; }
    if (!Robot.rc.canBroadcastMessage()) { return false; }
    Robot.rc.broadcastMessage(message);
    return true;
  }
}
