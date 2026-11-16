package dev.itemvisuals.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class Binding {
   public String model;
   public String animAlways = "none";
   public String animRightClick = "none";
   public String animSwing = "none";
   public String animRelease = "none";
   public int switchDelay = 100;
   public String switchMode = "smooth";
   public boolean glint = true;
   public Map<String, Placement> places = new LinkedHashMap<>();

   public Placement place(String id) {
      return this.places.computeIfAbsent(id, k -> new Placement());
   }

   public Placement placeOrNull(String id) {
      return this.places.get(id);
   }
}
