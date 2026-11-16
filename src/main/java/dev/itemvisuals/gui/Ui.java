package dev.itemvisuals.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Shared theme + drawing helpers for Clarity's screens (rounded panels, gradients, easing). */
public final class Ui {
   public static final int PANEL = 0xD81C182A;
   public static final int PANEL_HI = 0xE6272140;
   public static final int FIELD = 0xE0120F1C;
   public static final int LINE = 0x30FFFFFF;
   public static final int TEXT = 0xFFF0ECFA;
   public static final int DIM = 0xFFB9B1D4;
   public static final int MUTED = 0xFF7E7698;
   public static final int ACCENT = 0xFFFF7AB6;
   public static final int ACCENT_DARK = 0xFFC9508E;
   public static final int ACCENT2 = 0xFF966EFF;
   public static final int OK = 0xFF6EDCA0;
   public static final int BAD = 0xFFFF6B6B;
   public static final int HEAD_COL = 0xFFFFAA5A;
   public static final int HAND_COL = 0xFF78BEFF;

   /** Global fade multiplier for the frame being drawn (set by screens during the open animation). */
   public static float alpha = 1.0F;

   private Ui() {
   }

   public static long now() {
      return System.nanoTime() / 1000000L;
   }

   public static float easeOut(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      float u = 1.0F - t;
      return 1.0F - u * u * u;
   }

   public static float approach(float cur, float target, float speed, float dt) {
      return cur + (target - cur) * (1.0F - (float)Math.exp(-speed * dt));
   }

   public static int mul(int argb, float a) {
      int al = Math.round((argb >>> 24) * Mth.clamp(a, 0.0F, 1.0F));
      return al << 24 | argb & 16777215;
   }

   public static int lerp(int c1, int c2, float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      int a = (int)((c1 >>> 24) + ((c2 >>> 24) - (c1 >>> 24)) * t);
      int r = (int)((c1 >> 16 & 255) + ((c2 >> 16 & 255) - (c1 >> 16 & 255)) * t);
      int g = (int)((c1 >> 8 & 255) + ((c2 >> 8 & 255) - (c1 >> 8 & 255)) * t);
      int b = (int)((c1 & 255) + ((c2 & 255) - (c1 & 255)) * t);
      return a << 24 | r << 16 | g << 8 | b;
   }

   public static void rect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int c) {
      c = mul(c, alpha);
      if (c >>> 24 != 0 && x2 > x1 && y2 > y1) {
         g.fill(x1, y1, x2, y2, c);
      }
   }

   /** Filled rectangle with pixel-rounded corners (radius 0..5). */
   public static void round(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int r, int c) {
      r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
      if (r <= 0) {
         rect(g, x1, y1, x2, y2, c);
      } else {
         for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int)Math.round(Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            rect(g, x1 + inset, y1 + i, x2 - inset, y1 + i + 1, c);
            rect(g, x1 + inset, y2 - i - 1, x2 - inset, y2 - i, c);
         }
         rect(g, x1, y1 + r, x2, y2 - r, c);
      }
   }

   public static void outline(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int r, int border, int fill) {
      round(g, x1, y1, x2, y2, r, border);
      round(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, r - 1), fill);
   }

   /** Glass panel: soft shadow, translucent body, faint top highlight. */
   public static void panel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
      round(g, x1 - 1, y1 + 1, x2 + 1, y2 + 3, 5, 0x40000000);
      outline(g, x1, y1, x2, y2, 5, LINE, PANEL);
      rect(g, x1 + 5, y1 + 1, x2 - 5, y1 + 2, 0x18FFFFFF);
   }

   public static void gradientV(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int top, int bottom) {
      int h = Math.max(1, y2 - y1);
      for (int y = y1; y < y2; y += 2) {
         rect(g, x1, y, x2, Math.min(y + 2, y2), lerp(top, bottom, (float)(y - y1) / h));
      }
   }

   public static void gradientH(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int left, int right) {
      int w = Math.max(1, x2 - x1);
      for (int x = x1; x < x2; x += 2) {
         rect(g, x, y1, Math.min(x + 2, x2), y2, lerp(left, right, (float)(x - x1) / w));
      }
   }

   /** Screen backdrop: dark vertical gradient + soft accent glows. */
   public static void backdrop(GuiGraphicsExtractor g, int w, int h) {
      gradientV(g, 0, 0, w, h, 0xE0170F26, 0xF00A0912);
      for (int i = 0; i < 6; i++) {
         int s = 40 + i * 22;
         round(g, w - s * 2, -s, w + s, s, 5, 0x07FF7AB6);
         round(g, -s, h - s, s * 2, h + s, 5, 0x07966EFF);
      }
   }

   public static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int c) {
      c = mul(c, alpha);
      if (c >>> 24 > 8) {
         g.text(f, s, x, y, c);
      }
   }

   public static void textCentered(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int c) {
      text(g, f, s, cx - f.width(s) / 2, y, c);
   }

   public static String fit(Font f, String s, int w) {
      if (f.width(s) <= w) {
         return s;
      }
      while (s.length() > 1 && f.width(s + "\u2026") > w) {
         s = s.substring(0, s.length() - 1);
      }
      return s + "\u2026";
   }

   /** Small exponential smoother driven by wall-clock time. */
   public static final class Smooth {
      public float value;
      private long last = Ui.now();

      public Smooth(float start) {
         this.value = start;
      }

      public float update(float target, float speed) {
         long t = Ui.now();
         float dt = Math.min(0.1F, (t - this.last) / 1000.0F);
         this.last = t;
         this.value = Ui.approach(this.value, target, speed, dt);
         if (Math.abs(this.value - target) < 0.001F) {
            this.value = target;
         }
         return this.value;
      }
   }
}
