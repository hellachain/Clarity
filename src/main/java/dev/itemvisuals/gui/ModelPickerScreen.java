
package dev.itemvisuals.gui;

import dev.itemvisuals.config.VisualsConfig;
import dev.itemvisuals.render.ModelLibrary;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ModelPickerScreen extends Screen {
   private static final int ACCENT = -4616961;
   private static final int TEXT = -1646347;
   private static final int DIM = -6647627;
   private static final int PANEL = -1072426983;
   private static final int HEAD_COL = -8400641;
   private static final int HAND_COL = -20368;
   private static final int ROW = 14;
   private static final int TOP = 50;
   private static final String[] SORTS = new String[]{"Name", "Newest", "Category"};
   private static final String[] SHOWS = new String[]{"All", "Helmets", "Weapons"};
   private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd");
   private static String query = "";
   private static String folder = "";
   private static int sort = 0;
   private static int show = 0;
   private final Screen parent;
   private final ItemStack stack;
   private final String original;
   private final Consumer<String> apply;
   private String current;
   private final List<ModelLibrary.Info> shown = new ArrayList<>();
   private final List<String[]> folders = new ArrayList<>();
   private double scroll;
   private double folderScroll;
   private boolean scrollToCurrent = true;
   private int foldX;
   private int foldW;
   private int listX;
   private int listW;
   private int listH;
   private int prevX;
   private EditBox search;
   private final long openedAt = Ui.now();
   private final Ui.Smooth selY = new Ui.Smooth(-1.0F);
   private final Ui.Smooth hovY = new Ui.Smooth(-1.0F);
   private final Ui.Smooth hovA = new Ui.Smooth(0.0F);
   private final Ui.Smooth foldSelY = new Ui.Smooth(-1.0F);

   public ModelPickerScreen(Screen parent, ItemStack stack, String current, Consumer<String> apply) {
      super(Component.literal("Choose a model"));
      this.parent = parent;
      this.stack = stack;
      this.original = current;
      this.current = current;
      this.apply = apply;
      ModelLibrary.rescan();
   }

   protected void init() {
      this.foldX = 8;
      this.foldW = Math.max(90, Math.min(150, this.width / 5));
      this.prevX = this.width >= 460 ? this.width - 126 : this.width;
      this.listX = this.foldX + this.foldW + 10;
      this.listW = this.prevX - 10 - this.listX;
      this.listH = this.height - 30 - 50;
      int sw = Math.max(80, this.listW - 176);
      this.search = new EditBox(this.font, this.listX + 6, 31, sw - 12, 12, Component.literal("Search"));
      this.search.setBordered(false);
      this.search.setMaxLength(100);
      this.search.setValue(query);
      this.search.setResponder(s -> {
         query = s;
         this.scroll = 0.0;
         this.refilter();
      });
      this.addRenderableWidget(this.search);
      int bx = this.listX + sw + 4;
      int bw = (this.listX + this.listW - bx) / 2;
      this.addRenderableWidget(UiButton.of(bx, 26, bw - 2, 18, "Sort: " + SORTS[sort], () -> {
         sort = (sort + 1) % SORTS.length;
         this.scroll = 0.0;
         this.rebuildWidgets();
      }).tip("Name \u00b7 Newest = date added \u00b7 Category = helmets first, then weapons"));
      this.addRenderableWidget(UiButton.of(bx + bw, 26, this.listX + this.listW - bx - bw, 18, "Show: " + SHOWS[show], () -> {
         show = (show + 1) % SHOWS.length;
         this.scroll = 0.0;
         this.rebuildWidgets();
      }).tip("All models \u00b7 only helmets/hats \u00b7 only weapons and other held items"));
      int by = this.height - 24;
      this.addRenderableWidget(UiButton.styled(this.listX, by, 64, 18, "Done", UiButton.Style.PRIMARY, this::onClose));
      this.addRenderableWidget(UiButton.of(this.listX + 66, by, 64, 18, "Cancel", () -> {
         this.pick(this.original);
         this.onClose();
      }));
      this.addRenderableWidget(UiButton.of(this.listX + 132, by, 64, 18, "No model", () -> this.pick("none")));
      this.buildFolders();
      this.refilter();
      if (this.scrollToCurrent) {
         this.scrollToCurrent = false;

         for (int i = 0; i < this.shown.size(); i++) {
            if (this.shown.get(i).path().equals(this.current)) {
               this.ensureVisible(i);
               break;
            }
         }
      }

      this.setInitialFocus(this.search);
   }

   private void buildFolders() {
      this.folders.clear();
      TreeMap<String, Integer> counts = new TreeMap<>(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT).replace('/', '\u0001')));
      int top = 0;
      List<ModelLibrary.Info> all = ModelLibrary.infos();

      for (ModelLibrary.Info i : all) {
         String f = i.folder();
         if (f.isEmpty()) {
            top++;
         } else {
            int idx = f.indexOf(47);

            while (true) {
               counts.merge(idx < 0 ? f : f.substring(0, idx), 1, Integer::sum);
               if (idx < 0) {
                  break;
               }

               idx = f.indexOf(47, idx + 1);
            }
         }
      }

      this.folders.add(new String[]{"", "All folders", String.valueOf(all.size())});
      if (top > 0 && !counts.isEmpty()) {
         this.folders.add(new String[]{"/", "(no folder)", String.valueOf(top)});
      }

      for (Entry<String, Integer> e : counts.entrySet()) {
         String p = e.getKey();
         int depth = (int)p.chars().filter(c -> c == 47).count();
         this.folders.add(new String[]{p, " ".repeat(depth * 2) + p.substring(p.lastIndexOf(47) + 1), String.valueOf(e.getValue())});
      }

      if (this.folders.stream().noneMatch(f -> f[0].equals(folder))) {
         folder = "";
      }
   }

   private void refilter() {
      this.shown.clear();
      String[] terms = query.toLowerCase(Locale.ROOT).replace('_', ' ').trim().split("\\s+");

      for (ModelLibrary.Info i : ModelLibrary.infos()) {
         if ((folder.equals("/") ? i.folder().isEmpty() : folder.isEmpty() || i.folder().equals(folder) || i.folder().startsWith(folder + "/"))
            && (show != 1 || i.head())
            && (show != 2 || !i.head())) {
            String hay = (i.folder() + "/" + i.name()).toLowerCase(Locale.ROOT).replace('_', ' ');
            boolean ok = true;

            for (String t : terms) {
               if (!t.isEmpty() && !hay.contains(t)) {
                  ok = false;
                  break;
               }
            }

            if (ok) {
               this.shown.add(i);
            }
         }
      }

      Comparator<ModelLibrary.Info> byName = Comparator.<ModelLibrary.Info, String>comparing(ix -> ix.name().toLowerCase(Locale.ROOT))
         .thenComparing(ModelLibrary.Info::path);

      this.shown.sort(switch (sort) {
         case 1 -> Comparator.comparingLong(ModelLibrary.Info::added).reversed().thenComparing(byName);
         case 2 -> Comparator.<ModelLibrary.Info, Boolean>comparing(ix -> !ix.head()).thenComparing(byName);
         default -> byName;
      });
      this.clampScroll();
   }

   private void pick(String path) {
      this.current = path;
      this.apply.accept(path);
   }

   private void ensureVisible(int i) {
      if (i * ROW < this.scroll) {
         this.scroll = i * ROW;
      } else if ((i + 1) * ROW > this.scroll + this.listH) {
         this.scroll = (i + 1) * ROW - this.listH;
      }

      this.clampScroll();
   }

   private int folderTop() {
      return 42;
   }

   private int folderH() {
      return this.height - 10 - this.folderTop();
   }

   private void clampScroll() {
      this.scroll = Math.max(0.0, Math.min(this.scroll, Math.max(0, this.shown.size() * ROW - this.listH)));
      this.folderScroll = Math.max(0.0, Math.min(this.folderScroll, Math.max(0, this.folders.size() * ROW - this.folderH())));
   }

   public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
      if (super.mouseClicked(e, doubleClick)) {
         return true;
      }

      double mx = e.x();
      double my = e.y();
      if (mx >= this.listX - 4 && mx < this.listX + this.listW + 4 && my >= 50.0 && my < 50 + this.listH) {
         int i = (int)((my - 50.0 + this.scroll) / ROW);
         if (i >= 0 && i < this.shown.size()) {
            this.pick(this.shown.get(i).path());
            if (doubleClick) {
               this.onClose();
            } else {
               this.setFocused(this.search);
            }

            return true;
         }
      }

      int ft = this.folderTop();
      if (mx >= this.foldX - 4 && mx < this.foldX + this.foldW + 4 && my >= ft && my < ft + this.folderH()) {
         int i = (int)((my - ft + this.folderScroll) / ROW);
         if (i >= 0 && i < this.folders.size()) {
            folder = this.folders.get(i)[0];
            this.scroll = 0.0;
            this.refilter();
            this.setFocused(this.search);
            return true;
         }
      }

      return false;
   }

   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (mx < this.listX - 4) {
         this.folderScroll -= sy * ROW * 3.0;
      } else {
         this.scroll -= sy * ROW * 3.0;
      }

      this.clampScroll();
      return true;
   }

   public boolean keyPressed(KeyEvent e) {
      int k = e.key();
      if (k != 264 && k != 265) {
         if (k != 257 && k != 335) {
            return super.keyPressed(e);
         }

         this.onClose();
         return true;
      } else {
         if (!this.shown.isEmpty()) {
            int i = -1;

            for (int j = 0; j < this.shown.size(); j++) {
               if (this.shown.get(j).path().equals(this.current)) {
                  i = j;
                  break;
               }
            }

            i = i < 0 ? 0 : Math.max(0, Math.min(this.shown.size() - 1, i + (k == 264 ? 1 : -1)));
            this.pick(this.shown.get(i).path());
            this.ensureVisible(i);
         }

         return true;
      }
   }

   private String fit(String s, int w) {
      return Ui.fit(this.font, s, w);
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      float p = Ui.easeOut((Ui.now() - this.openedAt) / 380.0F);
      int o = Math.round((1.0F - p) * 12);
      Ui.alpha = p;
      Ui.gradientH(g, 0, 0, this.width, 20, 0xE0241A3A, 0xE0151222);
      Ui.rect(g, 0, 20, this.width, 21, 0x40FF7AB6);
      float glow = 0.75F + 0.25F * (float)Math.sin(Ui.now() / 400.0);
      Ui.text(g, this.font, "\u2726", 8, 6, Ui.mul(Ui.ACCENT, glow));
      Ui.text(g, this.font, "Choose a model", 18, 6, Ui.TEXT);
      int hw = 26 + this.font.width("Choose a model");
      String count = this.shown.size() + " of " + ModelLibrary.infos().size() + " models \u00b7 \u2191\u2193 to flip through, double-click to choose";
      Ui.text(g, this.font, this.fit(count, this.width - 12 - hw), hw, 6, Ui.DIM);

      Ui.panel(g, this.foldX - 4, 24 + o, this.foldX + this.foldW + 4, this.height - 6 + o);
      Ui.panel(g, this.listX - 4, 48 + o, this.listX + this.listW + 4, 50 + this.listH + 2 + o);
      if (this.prevX < this.width) {
         Ui.panel(g, this.prevX - 4, 24 + o, this.width - 4, this.height - 6 + o);
      }
      // search field
      int sx = this.listX;
      int sw = this.search.getWidth() + 12;
      boolean foc = this.search.isFocused();
      Ui.outline(g, sx, 26, sx + sw, 44, 4, foc ? Ui.ACCENT : 0x40FFFFFF, Ui.FIELD);
      if (foc) {
         Ui.round(g, sx - 1, 25, sx + sw + 1, 45, 5, Ui.mul(Ui.ACCENT, 0.18F));
      }

      Ui.text(g, this.font, "Folders", this.foldX, 30 + o, Ui.DIM);
      int ft = this.folderTop();
      int fh = this.folderH();
      g.enableScissor(this.foldX - 4, ft, this.foldX + this.foldW + 4, ft + fh);
      int selFolder = -1;
      for (int i = 0; i < this.folders.size(); i++) {
         if (this.folders.get(i)[0].equals(folder)) {
            selFolder = i;
         }
      }
      if (selFolder >= 0) {
         float target = ft + selFolder * ROW - (float)this.folderScroll;
         if (this.foldSelY.value < 0.0F) {
            this.foldSelY.value = target;
         }
         int fy = Math.round(this.foldSelY.update(target, 16.0F));
         Ui.round(g, this.foldX - 3, fy, this.foldX + this.foldW + 3, fy + ROW, 4, 0xC05A3C8C);
         Ui.rect(g, this.foldX - 3, fy + 3, this.foldX - 1, fy + ROW - 3, Ui.ACCENT);
      }
      for (int i = 0; i < this.folders.size(); i++) {
         int y = ft + i * ROW - (int)this.folderScroll;
         if (y + ROW >= ft && y <= ft + fh) {
            String[] f = this.folders.get(i);
            boolean sel = i == selFolder;
            boolean hov = mouseX >= this.foldX - 4 && mouseX < this.foldX + this.foldW + 4 && mouseY >= Math.max(y, ft) && mouseY < Math.min(y + ROW, ft + fh);
            if (hov && !sel) {
               Ui.round(g, this.foldX - 3, y, this.foldX + this.foldW + 3, y + ROW, 4, 0x30FFFFFF);
            }
            Ui.text(g, this.font, this.fit(f[1], this.foldW - this.font.width(f[2]) - 10), this.foldX + 2, y + 3, sel ? Ui.TEXT : Ui.DIM);
            int cw = this.font.width(f[2]) + 6;
            int cx = this.foldX + this.foldW - cw;
            Ui.round(g, cx, y + 2, cx + cw, y + ROW - 2, 4, sel ? 0x60FF7AB6 : 0x30FFFFFF);
            Ui.text(g, this.font, f[2], cx + 3, y + 3, Ui.TEXT);
         }
      }
      g.disableScissor();

      ModelLibrary.Info tip = null;
      int hoverRow = -1;
      int selRow = -1;
      for (int i = 0; i < this.shown.size(); i++) {
         if (this.shown.get(i).path().equals(this.current)) {
            selRow = i;
            break;
         }
      }
      g.enableScissor(this.listX - 4, 50, this.listX + this.listW + 4, 50 + this.listH);
      if (mouseX >= this.listX - 4 && mouseX < this.listX + this.listW + 4 && mouseY >= 50 && mouseY < 50 + this.listH) {
         int hr = (int)((mouseY - 50 + this.scroll) / ROW);
         if (hr >= 0 && hr < this.shown.size()) {
            hoverRow = hr;
         }
      }
      float ha = this.hovA.update(hoverRow >= 0 && hoverRow != selRow ? 1.0F : 0.0F, 14.0F);
      if (hoverRow >= 0) {
         float target = 50 + hoverRow * ROW - (float)this.scroll;
         if (this.hovY.value < 0.0F) {
            this.hovY.value = target;
         }
         this.hovY.update(target, 22.0F);
      }
      if (ha > 0.01F) {
         int hy = Math.round(this.hovY.value);
         Ui.round(g, this.listX - 3, hy, this.listX + this.listW + 3, hy + ROW, 4, Ui.mul(0x38FFFFFF, ha));
      }
      if (selRow >= 0) {
         float target = 50 + selRow * ROW - (float)this.scroll;
         if (this.selY.value < 0.0F) {
            this.selY.value = target;
         }
         int sy = Math.round(this.selY.update(target, 18.0F));
         Ui.round(g, this.listX - 3, sy, this.listX + this.listW + 3, sy + ROW, 4, 0xD05A3C8C);
         Ui.round(g, this.listX - 4, sy - 1, this.listX + this.listW + 4, sy + ROW + 1, 5, Ui.mul(Ui.ACCENT, 0.25F));
         Ui.rect(g, this.listX - 3, sy + 3, this.listX - 1, sy + ROW - 3, Ui.ACCENT);
      }
      for (int ix = (int)(this.scroll / ROW); ix < this.shown.size(); ix++) {
         int y = 50 + ix * ROW - (int)this.scroll;
         if (y > 50 + this.listH) {
            break;
         }
         ModelLibrary.Info inf = this.shown.get(ix);
         boolean sel = ix == selRow;
         if (ix == hoverRow) {
            tip = inf;
         }
         // staggered fade-in on open
         float rp = Ui.easeOut((Ui.now() - this.openedAt - 120 - Math.min(12, ix - (int)(this.scroll / ROW)) * 25) / 260.0F);
         float keep = Ui.alpha;
         Ui.alpha = Math.min(keep, rp);
         int ro = Math.round((1.0F - rp) * 8);
         String badge = inf.head() ? "helmet" : "weapon";
         int bw = this.font.width(badge) + 8;
         String date = sort == 1 ? DATE.format(new Date(inf.added())) : "";
         int dw = date.isEmpty() ? 0 : this.font.width(date) + 6;
         int bx = this.listX + this.listW - bw;
         Ui.round(g, bx, y + 2, bx + bw, y + ROW - 2, 4, Ui.mul(inf.head() ? Ui.HEAD_COL : Ui.HAND_COL, 0.85F));
         Ui.text(g, this.font, badge, bx + 4, y + 3, 0xFF15121F);
         if (!date.isEmpty()) {
            Ui.text(g, this.font, date, bx - dw, y + 3, Ui.MUTED);
         }
         int room = this.listW - bw - dw - 8;
         String dir = inf.folder().isEmpty() ? "" : this.fit(inf.folder() + "/", Math.max(20, room / 3));
         int dirW = this.font.width(dir);
         int tx = this.listX + 3 + ro;
         if (!dir.isEmpty()) {
            Ui.text(g, this.font, dir, tx, y + 3, Ui.MUTED);
         }
         Ui.text(g, this.font, this.fit(inf.name(), room - dirW), tx + dirW, y + 3, sel ? 0xFFFFFFFF : Ui.TEXT);
         Ui.alpha = keep;
      }
      g.disableScissor();
      if (this.shown.isEmpty()) {
         String msg = ModelLibrary.infos().isEmpty() ? "No .bbmodel files in config/clarity/items yet" : "Nothing found";
         Ui.textCentered(g, this.font, msg, this.listX + this.listW / 2, 50 + this.listH / 2 - 4, Ui.DIM);
      }
      int total = this.shown.size() * ROW;
      if (total > this.listH) {
         int bh = Math.max(14, this.listH * this.listH / total);
         int byy = 50 + (int)((this.listH - bh) * this.scroll / (total - this.listH));
         Ui.round(g, this.listX + this.listW + 1, byy, this.listX + this.listW + 4, byy + bh, 1, 0xA0FF7AB6);
      }

      super.extractRenderState(g, mouseX, mouseY, a);
      if (this.search.getValue().isEmpty()) {
         Ui.text(g, this.font, "Search name or folder\u2026", this.search.getX(), this.search.getY(), Ui.MUTED);
      }
      if (this.prevX < this.width) {
         Ui.text(g, this.font, "Preview", this.prevX, 30 + o, Ui.DIM);
         String name = this.current != null && !this.current.equals("none")
            ? this.current.substring(this.current.lastIndexOf(47) + 1).replace(".bbmodel", "")
            : "no model";
         Ui.text(g, this.font, this.fit(name, this.width - 10 - this.prevX), this.prevX, 42 + o, Ui.TEXT);
         float bob = (float)Math.sin(Ui.now() / 600.0) * 2.0F;
         g.pose().pushMatrix();
         g.pose().translate(this.prevX + 58, 96.0F + o + bob);
         g.pose().scale(4.5F, 4.5F);
         g.item(this.stack, -8, -8);
         g.pose().popMatrix();
         if (this.minecraft.player != null && this.height > 200) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
               g, this.prevX, 140 + o, this.width - 8, this.height - 10 + o, 34, 0.0625F, mouseX, mouseY, this.minecraft.player
            );
         }
      }
      Ui.alpha = 1.0F;
      if (tip != null) {
         g.setComponentTooltipForNextFrame(
            this.font,
            List.of(
               Component.literal(tip.path()),
               Component.literal("added " + DATE.format(new Date(tip.added())) + " \u00b7 " + (tip.head() ? "helmet / hat" : "weapon / held item"))
                  .withStyle(ChatFormatting.GRAY)
            ),
            mouseX,
            mouseY
         );
      }
   }

   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      Ui.alpha = Ui.easeOut((Ui.now() - this.openedAt) / 250.0F);
      Ui.backdrop(g, this.width, this.height);
      Ui.alpha = 1.0F;
   }

   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   public void removed() {
      VisualsConfig.save();
   }

   public boolean isPauseScreen() {
      return false;
   }
}
