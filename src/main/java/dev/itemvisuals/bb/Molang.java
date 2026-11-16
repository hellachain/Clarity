package dev.itemvisuals.bb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class Molang {
   private static final ConcurrentHashMap<String, Molang.Expr> CACHE = new ConcurrentHashMap<>();
   private static final Molang.Expr ZERO = c -> 0.0;

   public static Molang.Expr parse(Object raw) {
      if (raw == null) {
         return ZERO;
      } else if (raw instanceof Number n) {
         double v = n.doubleValue();
         return c -> v;
      } else {
         String s = raw.toString().trim();
         if (s.isEmpty()) {
            return ZERO;
         }

         try {
            double v = Double.parseDouble(s);
            return c -> v;
         } catch (NumberFormatException var5) {
            return CACHE.computeIfAbsent(s, k -> {
               try {
                  return new Molang.Parser(k).parseAll();
               } catch (RuntimeException var2x) {
                  return ZERO;
               }
            });
         }
      }
   }

   private Molang() {
   }

   public static final class Ctx {
      public double animTime;
      public double lifeTime;
   }

   public interface Expr {
      double eval(Molang.Ctx var1);
   }

   private static final class Parser {
      private final String s;
      private int i;

      Parser(String s) {
         this.s = s.toLowerCase(Locale.ROOT).replace("return", "").replace(";", "");
      }

      Molang.Expr parseAll() {
         Molang.Expr e = this.ternary();
         this.ws();
         if (this.i < this.s.length()) {
            throw new IllegalStateException("trailing");
         } else {
            return e;
         }
      }

      void ws() {
         while (this.i < this.s.length() && Character.isWhitespace(this.s.charAt(this.i))) {
            this.i++;
         }
      }

      boolean eat(String t) {
         this.ws();
         if (this.s.startsWith(t, this.i)) {
            this.i = this.i + t.length();
            return true;
         } else {
            return false;
         }
      }

      Molang.Expr ternary() {
         Molang.Expr cond = this.or();
         if (this.eat("??")) {
            Molang.Expr b = this.ternary();
            return cond;
         } else if (this.eat("?")) {
            Molang.Expr a = this.ternary();
            Molang.Expr b = this.eat(":") ? this.ternary() : Molang.ZERO;
            return c -> cond.eval(c) != 0.0 ? a.eval(c) : b.eval(c);
         } else {
            return cond;
         }
      }

      Molang.Expr or() {
         Molang.Expr l = this.and();

         while (this.eat("||")) {
            Molang.Expr a = l;
            Molang.Expr b = this.and();
            l = c -> a.eval(c) == 0.0 && b.eval(c) == 0.0 ? 0.0 : 1.0;
         }

         return l;
      }

      Molang.Expr and() {
         Molang.Expr l = this.cmp();

         while (this.eat("&&")) {
            Molang.Expr a = l;
            Molang.Expr b = this.cmp();
            l = c -> a.eval(c) != 0.0 && b.eval(c) != 0.0 ? 1.0 : 0.0;
         }

         return l;
      }

      Molang.Expr cmp() {
         Molang.Expr l = this.add();

         while (true) {
            Molang.Expr a = l;
            if (this.eat("<=")) {
               Molang.Expr b = this.add();
               l = c -> a.eval(c) <= b.eval(c) ? 1.0 : 0.0;
            } else if (this.eat(">=")) {
               Molang.Expr b = this.add();
               l = c -> a.eval(c) >= b.eval(c) ? 1.0 : 0.0;
            } else if (this.eat("==")) {
               Molang.Expr b = this.add();
               l = c -> a.eval(c) == b.eval(c) ? 1.0 : 0.0;
            } else if (this.eat("!=")) {
               Molang.Expr b = this.add();
               l = c -> a.eval(c) != b.eval(c) ? 1.0 : 0.0;
            } else if (this.eat("<")) {
               Molang.Expr b = this.add();
               l = c -> a.eval(c) < b.eval(c) ? 1.0 : 0.0;
            } else {
               if (!this.eat(">")) {
                  return l;
               }

               Molang.Expr b = this.add();
               l = c -> a.eval(c) > b.eval(c) ? 1.0 : 0.0;
            }
         }
      }

      Molang.Expr add() {
         Molang.Expr l = this.mul();

         while (true) {
            Molang.Expr a = l;
            if (this.eat("+")) {
               Molang.Expr b = this.mul();
               l = c -> a.eval(c) + b.eval(c);
            } else {
               if (!this.eat("-")) {
                  return l;
               }

               Molang.Expr b = this.mul();
               l = c -> a.eval(c) - b.eval(c);
            }
         }
      }

      Molang.Expr mul() {
         Molang.Expr l = this.unary();

         while (true) {
            Molang.Expr a = l;
            if (this.eat("*")) {
               Molang.Expr b = this.unary();
               l = c -> a.eval(c) * b.eval(c);
            } else if (this.eat("/")) {
               Molang.Expr b = this.unary();
               l = c -> {
                  double d = b.eval(c);
                  return d == 0.0 ? 0.0 : a.eval(c) / d;
               };
            } else {
               if (!this.eat("%")) {
                  return l;
               }

               Molang.Expr b = this.unary();
               l = c -> {
                  double d = b.eval(c);
                  return d == 0.0 ? 0.0 : a.eval(c) % d;
               };
            }
         }
      }

      Molang.Expr unary() {
         if (this.eat("-")) {
            Molang.Expr e = this.unary();
            return c -> -e.eval(c);
         } else if (this.eat("+")) {
            return this.unary();
         } else if (this.eat("!")) {
            Molang.Expr e = this.unary();
            return c -> e.eval(c) == 0.0 ? 1.0 : 0.0;
         } else {
            return this.primary();
         }
      }

      Molang.Expr primary() {
         this.ws();
         if (this.eat("(")) {
            Molang.Expr e = this.ternary();
            this.eat(")");
            return e;
         }

         int st = this.i;
         if (this.i >= this.s.length() || !Character.isDigit(this.s.charAt(this.i)) && this.s.charAt(this.i) != '.') {
            while (
               this.i < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '_' || this.s.charAt(this.i) == '.')
            ) {
               this.i++;
            }

            String name = this.s.substring(st, this.i);
            if (name.isEmpty()) {
               throw new IllegalStateException("bad token at " + this.i);
            }

            List<Molang.Expr> args = new ArrayList<>();
            if (!this.eat("(")) {
               return this.variable(name);
            }

            if (!this.eat(")")) {
               do {
                  args.add(this.ternary());
               } while (this.eat(","));

               this.eat(")");
            }

            return this.func(name, args);
         } else {
            while (this.i < this.s.length() && (Character.isDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '.')) {
               this.i++;
            }

            double v = Double.parseDouble(this.s.substring(st, this.i));
            if (this.i < this.s.length() && this.s.charAt(this.i) == 'f') {
               this.i++;
            }

            return c -> v;
         }
      }

      Molang.Expr arg(List<Molang.Expr> a, int n) {
         return n < a.size() ? a.get(n) : Molang.ZERO;
      }

      Molang.Expr func(String name, List<Molang.Expr> a) {
         String f = name.startsWith("math.") ? name.substring(5) : name;
         Molang.Expr x = this.arg(a, 0);
         Molang.Expr y = this.arg(a, 1);
         Molang.Expr z = this.arg(a, 2);

         return switch (f) {
            case "sin" -> c -> Math.sin(Math.toRadians(x.eval(c)));
            case "cos" -> c -> Math.cos(Math.toRadians(x.eval(c)));
            case "abs" -> c -> Math.abs(x.eval(c));
            case "sqrt" -> c -> Math.sqrt(Math.max(0.0, x.eval(c)));
            case "floor" -> c -> Math.floor(x.eval(c));
            case "ceil" -> c -> Math.ceil(x.eval(c));
            case "round" -> c -> Math.round(x.eval(c));
            case "trunc" -> c -> (long)x.eval(c);
            case "exp" -> c -> Math.exp(x.eval(c));
            case "ln" -> c -> Math.log(x.eval(c));
            case "pow" -> c -> Math.pow(x.eval(c), y.eval(c));
            case "min" -> c -> Math.min(x.eval(c), y.eval(c));
            case "max" -> c -> Math.max(x.eval(c), y.eval(c));
            case "mod" -> c -> {
               double d = y.eval(c);
               return d == 0.0 ? 0.0 : x.eval(c) % d;
            };
            case "clamp" -> c -> Math.max(y.eval(c), Math.min(z.eval(c), x.eval(c)));
            case "lerp" -> c -> {
               double t = z.eval(c);
               return x.eval(c) + (y.eval(c) - x.eval(c)) * t;
            };
            case "atan" -> c -> Math.toDegrees(Math.atan(x.eval(c)));
            case "atan2" -> c -> Math.toDegrees(Math.atan2(x.eval(c), y.eval(c)));
            case "asin" -> c -> Math.toDegrees(Math.asin(x.eval(c)));
            case "acos" -> c -> Math.toDegrees(Math.acos(x.eval(c)));
            case "random", "random_integer" -> c -> {
               double lo = x.eval(c);
               double hi = y.eval(c);
               return lo + Math.random() * (hi - lo);
            };
            default -> Molang.ZERO;
         };
      }

      Molang.Expr variable(String name) {
         return switch (name) {
            case "query.anim_time", "q.anim_time" -> c -> c.animTime;
            case "query.life_time", "q.life_time", "query.time_stamp" -> c -> c.lifeTime;
            case "math.pi" -> c -> Math.PI;
            case "true" -> c -> 1.0;
            default -> Molang.ZERO;
         };
      }
   }
}
