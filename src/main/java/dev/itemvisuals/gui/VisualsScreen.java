package dev.itemvisuals.gui;

import dev.itemvisuals.ItemKeys;
import dev.itemvisuals.bb.BBModel;
import dev.itemvisuals.config.Binding;
import dev.itemvisuals.config.Placement;
import dev.itemvisuals.config.VisualsConfig;
import dev.itemvisuals.render.ArmorHider;
import dev.itemvisuals.render.ModelLibrary;
import dev.itemvisuals.render.VisualsHook;
import dev.itemvisuals.share.Share;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * Main Clarity screen. Same features as before (slots, bindings, anims, placement, sharing, armor hiding),
 * redrawn with rounded glass panels, animated widgets and a staggered open animation.
 */
public class VisualsScreen extends Screen {
   private static final int[] DELAYS = new int[]{0, 100, 250, 500, 1000, 2000};
   private static int selectedSlot = 0;
   private static int tab = 0;
   private static final Ui.Smooth TAB_X = new Ui.Smooth(-1.0F);
   private static final Ui.Smooth TAB_W = new Ui.Smooth(0.0F);
   private final long openedAt = Ui.now();
   private final List<Placed> placed = new ArrayList<>();
   private int[] tabXs = new int[0];
   private int[] tabWs = new int[0];
   private int tabY = -1;
   private int midX;
   private int midW;
   private int prevX;

   private record Placed(AbstractWidget w, int x, int y, int col) {
   }

   public VisualsScreen() {
      super(Component.literal("Clarity"));
   }

   private ItemStack stack(int slot) {
      LocalPlayer p = this.minecraft.player;
      return p == null ? ItemStack.EMPTY : p.getInventory().getItem(slot);
   }

   private ItemStack selected() {
      return this.stack(selectedSlot);
   }

   private String currentKey() {
      ItemStack st = this.selected();
      if (st.isEmpty()) {
         return null;
      }
      String exact = ItemKeys.exactKey(st);
      return exact != null && VisualsConfig.INSTANCE.bindings.containsKey(exact) ? exact : ItemKeys.typeKey(st);
   }

   private Binding binding() {
      String k = this.currentKey();
      return k == null ? null : VisualsConfig.INSTANCE.bindings.get(k);
   }

   private Binding ensureBinding() {
      String k = this.currentKey();
      return VisualsConfig.INSTANCE.bindings.computeIfAbsent(k, x -> new Binding());
   }

   private boolean slotBound(int slot) {
      ItemStack st = this.stack(slot);
      return !st.isEmpty() && ItemKeys.find(st) != null;
   }

   /** Registers a widget for the open animation (col 0 = left, 1 = middle, 2 = top bar). */
   private <T extends AbstractWidget> T add(T w, int col) {
      this.placed.add(new Placed(w, w.getX(), w.getY(), col));
      return this.addRenderableWidget(w);
   }

   protected void init() {
      this.placed.clear();
      this.tabY = -1;
      int left = 10;
      this.prevX = this.width - 132;
      this.midX = left + 162 + 14;
      this.midW = this.prevX - 10 - this.midX;

      for (int i = 0; i < 9; i++) {
         this.addSlot(left + i * 18, 40, i);
      }
      for (int r = 0; r < 3; r++) {
         for (int c = 0; c < 9; c++) {
            this.addSlot(left + c * 18, 74 + r * 18, 9 + r * 9 + c);
         }
      }
      int[] armor = new int[]{39, 38, 37, 36, 40};
      for (int i = 0; i < armor.length; i++) {
         this.addSlot(left + i * 18 + (i == 4 ? 6 : 0), 144, armor[i]);
      }

      this.add(new UiToggle(left, 172, 80, "Mod", () -> VisualsConfig.INSTANCE.enabled, () -> {
         VisualsConfig.INSTANCE.enabled = !VisualsConfig.INSTANCE.enabled;
         this.changed();
      }).tip("Turn all Clarity models on or off"), 0);
      this.add(UiButton.of(left + 82, 170, 80, 18, "Open folder", () -> Util.getPlatform().openPath(VisualsConfig.itemsDir())), 0);
      this.add(UiButton.of(left, 190, 162, 18, "Reload models", () -> {
         ModelLibrary.reload();
         this.rebuildWidgets();
      }), 0);
      this.add(UiButton.chip(this.width - 156, 2, 74, 16, "Share mine", () -> VisualsConfig.INSTANCE.shareMine, () -> {
         VisualsConfig.INSTANCE.shareMine = !VisualsConfig.INSTANCE.shareMine;
         this.changed();
      }).tip("Upload the models you put on items, so other Clarity players see them on you (even without the files)"), 2);
      this.add(UiButton.chip(this.width - 79, 2, 74, 16, "See others", () -> VisualsConfig.INSTANCE.showOthers, () -> {
         VisualsConfig.INSTANCE.showOthers = !VisualsConfig.INSTANCE.showOthers;
         this.changed();
      }).tip("Show the models other Clarity players use on their items"), 2);
      this.add(new UiToggle(left + 84, 213, 78, "Others too", () -> VisualsConfig.INSTANCE.hideArmorOthers, () -> {
         VisualsConfig.INSTANCE.hideArmorOthers = !VisualsConfig.INSTANCE.hideArmorOthers;
         this.changed();
      }).tip("Also hide the crossed-out pieces on other players"), 0);

      int aw = 40;
      for (int i = 0; i < 4; i++) {
         int ai = i;
         this.add(UiButton.chip(left + i * aw, 229, i == 3 ? 162 - 3 * aw : aw - 2, 18, ArmorHider.NAMES[i], () -> !ArmorHider.hidden(ai), () -> {
            ArmorHider.toggle(ai);
            this.changed();
         }).strike(() -> ArmorHider.hidden(ai)).tip(ArmorHider.NAMES[i] + " \u00b7 click to show / hide on you"), 0);
      }

      ItemStack st = this.selected();
      if (st.isEmpty()) {
         return;
      }
      Binding b = this.binding();
      int y = 52;
      int x = this.midX;
      String exact = ItemKeys.exactKey(st);
      String type = ItemKeys.typeKey(st);
      String key = this.currentKey();
      boolean isExact = key != null && key.equals(exact);
      int half = (this.midW - 60) / 2;
      UiButton exactBtn = this.add(UiButton.chip(x + 60, y, half - 2, 18, "This exact item", () -> isExact, () -> this.moveBinding(exact)), 1);
      exactBtn.active = exact != null;
      this.add(UiButton.chip(x + 60 + half, y, half, 18, "Every " + shortType(type), () -> !isExact, () -> this.moveBinding(type)), 1);
      y += 22;

      List<String> models = new ArrayList<>(ModelLibrary.list());
      models.addFirst("none");
      String cur = b != null && b.model != null ? b.model : "none";
      int mi = Math.max(0, models.indexOf(cur));
      this.add(UiButton.of(x + 60, y, 18, 18, "<", () -> this.setModel(models.get((mi - 1 + models.size()) % models.size()))), 1);
      this.add(UiButton.styled(x + 80, y, this.midW - 104, 18, "" + this.trim(cur, this.midW - 116), UiButton.Style.PRIMARY,
         () -> this.minecraft.gui.setScreen(new ModelPickerScreen(this, st, cur, this::applyModel)))
         .tip("Browse folders, search, sort by date or helmet/weapon"), 1);
      this.add(UiButton.of(x + this.midW - 22, y, 18, 18, ">", () -> this.setModel(models.get((mi + 1) % models.size()))), 1);
      y += 22;
      if (b == null || b.model == null) {
         return;
      }

      BBModel model = ModelLibrary.get(b.model);
      List<String> anims = new ArrayList<>();
      anims.add("none");
      if (model != null) {
         anims.addAll(model.animations.keySet());
      }
      int third = (this.midW - 60) / 3;
      int quarter = (this.midW - 60) / 4;
      this.add(this.animCycle(x + 60, y, quarter - 2, "Idle", anims, b.animAlways, v -> b.animAlways = v), 1);
      this.add(this.animCycle(x + 60 + quarter, y, quarter - 2, "Right", anims, b.animRightClick, v -> b.animRightClick = v), 1);
      this.add(this.animCycle(x + 60 + quarter * 2, y, quarter - 2, "Release", anims, b.animRelease, v -> b.animRelease = v), 1);
      this.add(this.animCycle(x + 60 + quarter * 3, y, quarter, "Swing", anims, b.animSwing, v -> b.animSwing = v), 1);
      y += 22;

      int di = 0;
      for (int i = 0; i < DELAYS.length; i++) {
         if (DELAYS[i] == b.switchDelay) {
            di = i;
         }
      }
      int fdi = di;
      this.add(UiButton.of(x + 60, y, third - 2, 18, "Delay: " + (b.switchDelay == 0 ? "instant" : b.switchDelay + " ms"), () -> {
         b.switchDelay = DELAYS[(fdi + 1) % DELAYS.length];
         this.changed();
         this.rebuildWidgets();
      }), 1);
      this.add(UiButton.chip(x + 60 + third, y, third - 2, 18, "smooth".equals(b.switchMode) ? "Smooth" : "Hold\u2192switch", () -> "smooth".equals(b.switchMode), () -> {
         b.switchMode = "smooth".equals(b.switchMode) ? "hold" : "smooth";
         this.changed();
         this.rebuildWidgets();
      }), 1);
      this.add(UiButton.of(x + 60 + third * 2, y, third, 18, "Apply to all", () -> {
         for (Binding o : VisualsConfig.INSTANCE.bindings.values()) {
            o.switchDelay = b.switchDelay;
            o.switchMode = b.switchMode;
         }
         this.changed();
      }).tip("Copy this delay and switch mode to every binding"), 1);
      y += 26;

      int labels = 0;
      for (String l : VisualsHook.PLACE_LABELS) {
         labels += this.font.width(l) + 8;
      }
      int n = VisualsHook.PLACES.length;
      this.tabXs = new int[n];
      this.tabWs = new int[n];
      this.tabY = y;
      int tx = x;
      for (int ix = 0; ix < n; ix++) {
         int ti = ix;
         int tw = ix == n - 1 ? x + this.midW - tx : (this.font.width(VisualsHook.PLACE_LABELS[ix]) + 8) * this.midW / labels;
         this.tabXs[ix] = tx;
         this.tabWs[ix] = tw - 2;
         this.add(UiButton.styled(tx, y, tw - 2, 18, VisualsHook.PLACE_LABELS[ix], UiButton.Style.TAB, () -> {
            tab = ti;
            this.rebuildWidgets();
         }).selected(() -> tab == ti), 1);
         tx += tw;
      }
      if (TAB_X.value < 0.0F) {
         TAB_X.value = this.tabXs[tab];
         TAB_W.value = this.tabWs[tab];
      }
      y += 24;

      Placement p = b.place(VisualsHook.PLACES[tab]);
      int col = (this.midW - 8) / 3;
      this.slider(x, y, col, "Move X", " px", -100.0F, 100.0F, 0.5F, p.moveX, v -> p.moveX = v);
      this.slider(x + col + 4, y, col, "Move Y", " px", -100.0F, 100.0F, 0.5F, p.moveY, v -> p.moveY = v);
      this.slider(x + 2 * col + 8, y, col, "Move Z", " px", -100.0F, 100.0F, 0.5F, p.moveZ, v -> p.moveZ = v);
      y += 24;
      this.slider(x, y, col, "Turn X", "\u00b0", -180.0F, 180.0F, 1.0F, p.turnX, v -> p.turnX = v);
      this.slider(x + col + 4, y, col, "Turn Y", "\u00b0", -180.0F, 180.0F, 1.0F, p.turnY, v -> p.turnY = v);
      this.slider(x + 2 * col + 8, y, col, "Turn Z", "\u00b0", -180.0F, 180.0F, 1.0F, p.turnZ, v -> p.turnZ = v);
      y += 24;
      this.slider(x, y, col, "Size", "%", 0.0F, 400.0F, 1.0F, p.size, v -> p.size = v);
      this.add(UiButton.of(x + col + 4, y + 2, col, 18, "Reset this place", () -> {
         b.places.remove(VisualsHook.PLACES[tab]);
         this.changed();
         this.rebuildWidgets();
      }), 1);
      this.add(UiButton.chip(x + 2 * col + 8, y + 2, col, 18, "Enchant glint", () -> b.glint, () -> {
         b.glint = !b.glint;
         this.changed();
      }), 1);
      y += 26;
      this.add(UiButton.styled(x, y, col, 18, "Reset to default", UiButton.Style.DANGER, () -> {
         VisualsConfig.INSTANCE.bindings.remove(this.currentKey());
         this.changed();
         this.rebuildWidgets();
      }), 1);
   }

   private void addSlot(int x, int y, int slot) {
      this.add(new SlotWidget(x, y, slot, this::stack, s -> s == selectedSlot, this::slotBound, s -> {
         selectedSlot = s;
         this.rebuildWidgets();
      }), 0);
   }

   private void slider(int x, int y, int w, String label, String unit, float min, float max, float step, float cur, Consumer<Float> set) {
      this.add(new ValueSlider(x, y, w, 20, label, unit, min, max, step, cur, v -> {
         set.accept(v);
         this.changed();
      }), 1);
   }

   private UiButton animCycle(int x, int y, int w, String label, List<String> anims, String cur, Consumer<String> set) {
      int idx = Math.max(0, anims.indexOf(cur));
      boolean on = idx > 0;
      return UiButton.chip(x, y, w, 18, label + ": " + anims.get(idx), () -> on, () -> {
         set.accept(anims.get((idx + 1) % anims.size()));
         this.changed();
         this.rebuildWidgets();
      }).tip(label + " animation \u00b7 click to cycle");
   }

   private void moveBinding(String to) {
      String from = this.currentKey();
      if (to != null && from != null && !to.equals(from)) {
         Binding b = VisualsConfig.INSTANCE.bindings.remove(from);
         if (b == null) {
            b = new Binding();
         }
         VisualsConfig.INSTANCE.bindings.put(to, b);
         this.changed();
         this.rebuildWidgets();
      }
   }

   private void setModel(String model) {
      this.applyModel(model);
      this.rebuildWidgets();
   }

   private void applyModel(String model) {
      if (model.equals("none")) {
         Binding b = this.binding();
         if (b != null) {
            b.model = null;
         }
      } else {
         Binding b = this.ensureBinding();
         b.model = model;
         BBModel m = ModelLibrary.get(model);
         if (m != null) {
            autoAnims(b, m);
         }
      }
      this.changed();
   }

   private static void autoAnims(Binding b, BBModel m) {
      String idle = null;
      String right = null;
      String swing = null;
      String release = null;
      for (String n : m.animations.keySet()) {
         String l = n.toLowerCase(Locale.ROOT);
         if (idle == null && (l.contains("idle") || l.contains("always"))) {
            idle = n;
         }
         if (right == null && (l.contains("right") || l.contains("use") || l.contains("ability") || l.contains("cast") || l.contains("charge"))) {
            right = n;
         }
         if (swing == null && (l.contains("swing") || l.contains("attack"))) {
            swing = n;
         }
         if (release == null && (l.contains("release") || l.contains("shoot") || l.contains("fire"))) {
            release = n;
         }
      }
      if (!m.animations.containsKey(b.animAlways)) {
         b.animAlways = idle != null ? idle : "none";
      }
      if (!m.animations.containsKey(b.animRightClick)) {
         b.animRightClick = right != null ? right : "none";
      }
      if (!m.animations.containsKey(b.animSwing)) {
         b.animSwing = swing != null ? swing : "none";
      }
      if (!m.animations.containsKey(b.animRelease)) {
         b.animRelease = release != null ? release : "none";
      }
   }

   private void changed() {
      VisualsConfig.INSTANCE.dirty = true;
   }

   private static String shortType(String key) {
      String s = key.substring(key.indexOf(58) + 1);
      if (s.startsWith("minecraft:")) {
         s = s.substring(10);
      }
      return s.toLowerCase(Locale.ROOT);
   }

   private String trim(String s, int w) {
      if (this.font.width(s) <= w - 8) {
         return s;
      }
      while (s.length() > 1 && this.font.width("\u2026" + s) > w - 8) {
         s = s.substring(1);
      }
      return "\u2026" + s;
   }

   private float colProgress(int col) {
      float t = (Ui.now() - this.openedAt) / 420.0F;
      float delay = col == 2 ? 0.0F : col == 0 ? 0.12F : col == 1 ? 0.26F : 0.4F;
      return Ui.easeOut(t - delay);
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      float pTop = this.colProgress(2);
      float pLeft = this.colProgress(0);
      float pMid = this.colProgress(1);
      float pPrev = this.colProgress(3);
      for (Placed pl : this.placed) {
         float p = pl.col() == 0 ? pLeft : pl.col() == 1 ? pMid : pTop;
         pl.w().setY(pl.y() + Math.round((1.0F - p) * (pl.col() == 2 ? -10 : 14)));
      }

      // top bar
      Ui.alpha = pTop;
      Ui.gradientH(g, 0, 0, this.width, 20, 0xE0241A3A, 0xE0151222);
      Ui.rect(g, 0, 20, this.width, 21, 0x40FF7AB6);
      float glow = 0.75F + 0.25F * (float)Math.sin(Ui.now() / 400.0);
      Ui.text(g, this.font, "\u2726", 8, 6, Ui.mul(Ui.ACCENT, glow));
      Ui.text(g, this.font, "Clarity", 18, 6, Ui.TEXT);
      String sub = "your own Blockbench models on any item \u00b7 " + VisualsConfig.itemsDir().getFileName();
      int subX = 26 + this.font.width("Clarity");
      int room = this.width - 166 - subX;
      if (room > 40) {
         Ui.text(g, this.font, Ui.fit(this.font, sub, room), subX, 6, Ui.DIM);
      }

      // panels slide in
      int oL = Math.round((1.0F - pLeft) * 14);
      int oM = Math.round((1.0F - pMid) * 14);
      int oP = Math.round((1.0F - pPrev) * 14);
      Ui.alpha = pLeft;
      Ui.panel(g, 4, 24 + oL, this.midX - 6, this.height - 8 + oL);
      Ui.text(g, this.font, "Hotbar", 10, 30 + oL, Ui.DIM);
      Ui.text(g, this.font, "Inventory", 10, 64 + oL, Ui.DIM);
      Ui.text(g, this.font, "Armor & off hand", 10, 134 + oL, Ui.DIM);
      Ui.text(g, this.font, "Armor on you", 10, 216 + oL, Ui.DIM);
      Ui.alpha = pMid;
      Ui.panel(g, this.midX - 4, 24 + oM, this.prevX - 6, this.height - 8 + oM);
      Ui.alpha = pPrev;
      Ui.panel(g, this.prevX - 4, 24 + oP, this.width - 4, this.height - 8 + oP);

      ItemStack st = this.selected();
      int x = this.midX;
      Ui.alpha = pMid;
      if (st.isEmpty()) {
         Ui.text(g, this.font, "Pick an item on the left.", x, 30 + oM, Ui.TEXT);
         Ui.text(g, this.font, "Put .bbmodel files into config/clarity/items", x, 44 + oM, Ui.DIM);
      } else {
         g.text(this.font, st.getHoverName(), x, 28 + oM, Ui.mul(Ui.TEXT, pMid));
         ItemKeys.Keys k = ItemKeys.of(st);
         String info = (k.skyblockId() != null ? k.skyblockId() : ItemKeys.itemKey(st).substring(5))
            + (k.uuid() != null ? " \u00b7 UUID " + k.uuid().substring(0, Math.min(8, k.uuid().length())) + "\u2026" : "");
         Ui.text(g, this.font, info, x, 39 + oM, Ui.DIM);
         Ui.text(g, this.font, "Applies to", x, 57 + oM, Ui.DIM);
         Ui.text(g, this.font, "Model", x, 79 + oM, Ui.DIM);
         Binding b = this.binding();
         if (b != null && b.model != null) {
            BBModel m = ModelLibrary.get(b.model);
            Ui.text(g, this.font, "Anims", x, 101 + oM, Ui.DIM);
            Ui.text(g, this.font, "Switch", x, 123 + oM, Ui.DIM);
            String stats = m == null ? "failed to load" : m.faceCount + " faces \u00b7 " + m.animations.size() + " anims";
            int sw = this.font.width(stats) + 10;
            int sx = x + this.midW - sw;
            Ui.round(g, sx, 26 + oM, sx + sw, 38 + oM, 5, m == null ? 0x60FF6B6B : 0x40966EFF);
            Ui.text(g, this.font, stats, sx + 5, 28 + oM, m == null ? Ui.BAD : Ui.TEXT);
         }
      }

      // animated tab indicator
      if (this.tabY >= 0 && tab < this.tabXs.length) {
         float tx = TAB_X.update(this.tabXs[tab], 14.0F);
         float tw = TAB_W.update(this.tabWs[tab], 14.0F);
         Ui.round(g, Math.round(tx) + 6, this.tabY + 16 + oM, Math.round(tx + tw) - 6, this.tabY + 18 + oM, 1, Ui.ACCENT);
      }

      Ui.alpha = pPrev;
      Ui.text(g, this.font, "Live preview", this.prevX, 30 + oP, Ui.DIM);
      boolean live = Share.active();
      String foot = live ? "Sharing: " + Share.status() : "Others see it only if they have this mod + the same model.";
      Ui.alpha = pMid;
      int fx = this.midX;
      if (live) {
         float pulse = 0.6F + 0.4F * (float)Math.sin(Ui.now() / 300.0);
         Ui.round(g, fx, this.height - 20, fx + 6, this.height - 14, 3, Ui.mul(Ui.OK, pulse));
         fx += 10;
      }
      Ui.text(g, this.font, Ui.fit(this.font, foot, this.midW - (fx - this.midX)), fx, this.height - 20, Ui.DIM);

      Ui.alpha = Math.min(pLeft, pMid);
      super.extractRenderState(g, mouseX, mouseY, a);
      Ui.alpha = 1.0F;

      if (!st.isEmpty()) {
         float bob = (float)Math.sin(Ui.now() / 600.0) * 2.0F;
         float sc = 5.0F * (0.85F + 0.15F * pPrev);
         g.pose().pushMatrix();
         g.pose().translate(this.prevX + 60, 82 + oP + bob);
         g.pose().scale(sc, sc);
         g.item(st, -8, -8);
         g.pose().popMatrix();
      }
      if (this.minecraft.player != null && this.height > 200) {
         InventoryScreen.extractEntityInInventoryFollowsMouse(
            g, this.prevX, 140 + oP, this.width - 8, this.height - 30 + oP, 40, 0.0625F, mouseX, mouseY, this.minecraft.player
         );
      }
   }

   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      Ui.alpha = Ui.easeOut((Ui.now() - this.openedAt) / 250.0F);
      Ui.backdrop(g, this.width, this.height);
      Ui.alpha = 1.0F;
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void removed() {
      VisualsConfig.save();
      VisualsConfig.INSTANCE.dirty = false;
   }
}
