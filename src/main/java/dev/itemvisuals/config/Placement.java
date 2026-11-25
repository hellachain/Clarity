package dev.itemvisuals.config;

public class Placement {
   public float moveX;
   public float moveY;
   public float moveZ;
   public float turnX;
   public float turnY;
   public float turnZ;
   public float size = 100.0F;

   public boolean isDefault() {
      return this.moveX == 0.0F
         && this.moveY == 0.0F
         && this.moveZ == 0.0F
         && this.turnX == 0.0F
         && this.turnY == 0.0F
         && this.turnZ == 0.0F
         && this.size == 100.0F;
   }
}
