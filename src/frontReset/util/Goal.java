package frontReset.util;

import battlecode.common.*;

public class Goal {
  
  public enum Type {
    EXPLORE(0),
    CAPTURE_SRP(1),
    CAPTURE_RUIN(2),
    FIGHT_TOWER(3),
    REFILL_PAINT(4),
    GET_BACKUP(5),
    SURVIVE(6);

    public final int v;

    private Type(int v) {
      this.v = v;
    }
  }

  public final Type type;
  public final MapLocation target;

  public Goal(Type type, MapLocation target) {
    this.type = type;
    this.target = target;
  };

  @Override
  public String toString() { 
    return switch (type) {
      case EXPLORE -> "EXPLORE";
      case CAPTURE_SRP -> "CAPTURE_SRP"; 
      case CAPTURE_RUIN -> "CAPTURE_RUIN"; 
      case FIGHT_TOWER -> "FIGHT_TOWER";
      case REFILL_PAINT -> "REFILL_PAINT"; 
      case GET_BACKUP -> "GET_BACKUP";
      case SURVIVE -> "SURVIVE";
    } + target;
  }

  @Override
  public boolean equals(Object obj){
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    Goal g = (Goal) obj;
    return (this.type == g.type && this.target.equals(g.target));
  }

}
