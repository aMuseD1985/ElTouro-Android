import java.lang.reflect.*;
public class Runner {
  public static void main(String[] a) throws Exception {
    int ok = 0, bad = 0;
    for (String cn : a) {
      Class<?> c = Class.forName(cn);
      for (Method m : c.getDeclaredMethods()) {
        if (!m.isAnnotationPresent(org.junit.Test.class)) continue;
        Object o = c.getDeclaredConstructor().newInstance();
        try { m.invoke(o); ok++; System.out.println("  ok   " + c.getSimpleName() + "." + m.getName()); }
        catch (InvocationTargetException e) { bad++; System.out.println("  FAIL " + c.getSimpleName() + "." + m.getName() + ": " + e.getCause()); }
      }
    }
    System.out.println(ok + " passed, " + bad + " failed");
    System.exit(bad == 0 ? 0 : 1);
  }
}
