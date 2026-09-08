package mainCoordinator;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.PrintStream;
import java.nio.file.Path;

import java.util.List;

import coordinator.CapabilityEnvironment;
import coordinator.Coordinator;
import core.OtherPackages;
import core.E.Literal;
import docBuilder.HtmlDocBuilder;
import naiveBackend.BackendTools;
import realSourceOracle.RealSourceOracleWithZip;
import userMessages.UserError;
import tools.SourceOracle;
import tools.Utf8Sink;

public record ProgrammaticMain(StringBuilder out, StringBuilder err,String fName, String code, Path stdLib, Path stdRt, Path dest){
  static public void runFearless(Path projectPath, Path base, Path rt) throws Throwable {
    try{ Main.run(projectPath, base,rt); }
    catch(UserError e){ System.err.print(e.getMessage()); }
    catch (InterruptedException e){ throw e; }
    catch(Throwable t){
      System.err.println(t.getClass().getCanonicalName());
      System.err.print(UserError.crash(t));
    }
  }
  public void runFearless(){
    var oldOut= System.out;
    var oldErr= System.err;
    try{ _runFearless(); }
    catch(UserError e){ System.err.print(e.getMessage()); }
    catch(Throwable t){ System.err.print(UserError.crash(t)); }
    finally{
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }
  private void _runFearless() throws InterruptedException{
    System.setOut(new PrintStream(new Utf8Sink(out::append), true, UTF_8));
    System.setErr(new PrintStream(new Utf8Sink(err::append), true, UTF_8));
    var oracle= SourceOracle.debugBuilder().put(fName,code).build();
    var c= new Coordinator(){
      @Override public SourceOracle sourceOracle(Path path){ return oracle; }
      @Override public BackendTools backendTools(String pkgName, SourceOracle o, OtherPackages other, List<Literal> core, Path rootDir, CapabilityEnvironment capabilities){
        var docs= new HtmlDocBuilder(o,other,core,baseCachePath().map(p->p.resolve("base.html")));
        docs.packageLocation(pkgName, rootDir.resolve("gen_java",pkgName+".html"), rootDir.getParent().resolve("auto_tests","_"+pkgName,pkgName+"_test.fear"));
        return BackendTools.of(pkgName, core, rootDir, docs, stdRt, capabilities);
      }
    };
    c.main(dest, new RealSourceOracleWithZip(stdLib));
  }
}
