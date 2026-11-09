package integrationTests;

import java.awt.EventQueue;
import java.awt.Window;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.LongStream;

import javax.management.ObjectName;

public final class GuiProbe{
  static final List<Integer> lengths= List.of(10240, 81920);
  static final Pattern row= Pattern.compile("\\s*\\d+:\\s+(\\d+)\\s+\\d+\\s+(\\S+).*");
  public static void premain(String mode){
    var t= new Thread(() -> run(mode), "GuiProbe");
    t.setDaemon(true);
    t.start();
  }
  static void run(String mode){
    try{
      var frame= frame();
      Thread.sleep(1000);
      EventQueue.invokeAndWait(() -> measure(mode, frame));
      System.out.flush();
      method(frame, "closeRequest").invoke(frame);
    }
    catch (Throwable t){
      t.printStackTrace();
      Runtime.getRuntime().halt(3);
    }
  }
  static Object frame() throws ReflectiveOperationException, InterruptedException{
    while (true){
      for (var w : Window.getWindows()){
        if (!w.getClass().getName().equals("_base.Stage$Win") || !w.isShowing()){ continue; }
        var owner= get(method(w, "stage").invoke(w), "owner");
        if ((boolean) get(owner, "started")){ return owner; }
      }
      Thread.sleep(100);
    }
  }
  static void measure(String mode, Object frame){
    try{
      var render= MethodHandles.lookup().unreflect(method(frame, "render")).asType(MethodType.methodType(void.class, Object.class));
      if (mode.equals("alloc")){ alloc(render, frame); return; }
      type(render, frame);
    }
    catch (Throwable t){ throw new Error(t); }
  }
  static void alloc(MethodHandle render, Object frame) throws Throwable{
    int n= 200;
    for (int i= 0; i < 1000; i += 1){ render.invokeExact(frame); }
    while (true){
      long gcs= collections();
      var before= histogram();
      for (int i= 0; i < n; i += 1){ render.invokeExact(frame); }
      var after= histogram();
      if (collections() != gcs){ continue; }
      var b= instances(before);
      instances(after).forEach((c, k) -> {
        long perFrame= Math.round((k - b.getOrDefault(c, 0L)) / (double) n);
        if (perFrame > 0){ System.out.println("GuiProbe "+c+" "+perFrame); }
      });
      return;
    }
  }
  static long collections(){ return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(g -> g.getCollectionCount()).sum(); }
  static String histogram() throws Exception{
    return (String) ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"),
      "gcClassHistogram", new Object[]{ new String[]{ "-all" } }, new String[]{ String[].class.getName() });
  }
  static TreeMap<String,Long> instances(String histogram){
    var res= new TreeMap<String,Long>();
    for (var l : histogram.lines().toList()){
      var m= row.matcher(l);
      if (m.matches()){ res.merge(m.group(2), Long.parseLong(m.group(1)), Long::sum); }
    }
    return res;
  }
  static void type(MethodHandle render, Object frame) throws Throwable{
    var areas= new ArrayList<Object>();
    edits(get(frame, "top"), areas);
    for (var warm : List.of(true, false)){
      for (var length : lengths){
        var text= "word ".repeat(length / 5);
        for (var a : areas){
          method(a, "program", int.class, int.class, String.class, int.class, int.class).invoke(a, 0, ((StringBuilder) get(a, "buf")).length(), text, 0, 0);
          method(a, "layout").invoke(a);
        }
        long r= fastest(10, () -> { render.invokeExact(frame); });
        if (!warm){ System.out.println("GuiProbe render "+length+" "+r); }
        for (int i= 0; i < areas.size(); i += 1){
          var a= areas.get(i);
          var clean= method(a, "clean", int.class, int.class, String.class);
          var edit= method(a, "edit", clean.getReturnType());
          long k= fastest(5, () -> edit.invoke(a, clean.invoke(a, 0, 0, "x")));
          if (!warm){ System.out.println("GuiProbe key "+i+" "+length+" "+k); }
        }
      }
    }
  }
  interface Step{ void run() throws Throwable; }
  static long fastest(int times, Step s) throws Throwable{
    var res= new long[times];
    for (int i= 0; i < times; i += 1){
      long start= System.nanoTime();
      s.run();
      res[i] = System.nanoTime() - start;
    }
    return LongStream.of(res).min().orElseThrow();
  }
  static void edits(Object w, ArrayList<Object> all) throws ReflectiveOperationException{
    if (isEdit(w.getClass())){ all.add(w); }
    for (var k : (List<?>) method(w, "children").invoke(w)){ edits(k, all); }
  }
  static boolean isEdit(Class<?> c){ return c != null && (c.getName().equals("_base.AEditText") || isEdit(c.getSuperclass())); }
  static Method method(Object o, String name, Class<?>... params) throws NoSuchMethodException{ return method(o.getClass(), name, params); }
  static Method method(Class<?> c, String name, Class<?>... params) throws NoSuchMethodException{
    try{
      var m= c.getDeclaredMethod(name, params);
      m.setAccessible(true);
      return m;
    }
    catch (NoSuchMethodException e){
      if (c.getSuperclass() == null){ throw e; }
      return method(c.getSuperclass(), name, params);
    }
  }
  static Object get(Object o, String name) throws ReflectiveOperationException{ return field(o.getClass(), name).get(o); }
  static Field field(Class<?> c, String name) throws NoSuchFieldException{
    try{
      var f= c.getDeclaredField(name);
      f.setAccessible(true);
      return f;
    }
    catch (NoSuchFieldException e){
      if (c.getSuperclass() == null){ throw e; }
      return field(c.getSuperclass(), name);
    }
  }
}
