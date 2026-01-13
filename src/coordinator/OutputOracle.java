package coordinator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import core.E.Literal;
import core.OtherPackages;
import utils.Bug;

public interface OutputOracle{
  Path pathOf(List<String> path);
  default Path rootDir(){ return pathOf(List.of()); }
  default long lastModified(List<String> path){ throw Bug.todo(); }//returns -1 if file not exists
  default long baseApiStamp(){ return lastModified(List.of("base.json")); }
  default long mapStamp(){ return lastModified(List.of("_map.json")); }
  default long pkgApiStamp(String pkg){ return lastModified(List.of(pkg+".json")); }

  default boolean exists(List<String> path){ throw Bug.todo(); }
  default String readString(List<String> path){ throw Bug.todo(); }
  default void writeStringAtomicIfDifferentBump(List<String> path,String content,long minExclusiveMillis){ throw Bug.todo(); }
  // (bumps mtime strictly above minExclusiveMillis)
  
  default void write(List<String> path, Consumer<Consumer<String>> dataProducer){
    try (BufferedWriter writer = Files.newBufferedWriter(pathOf(path))){
      dataProducer.accept(content -> {
        try { writer.write(content); }
        catch (IOException e){ throw new UncheckedIOException(e); }
      });
    }
    catch (IOException e){ throw new UncheckedIOException(e); }
  }
  default OtherPackages addCachedPkgApi(OtherPackages other, String pkg){ throw Bug.todo(); }
  default long commitPkgApi(String pkg, List<Literal> core, long maxIn){ throw Bug.todo(); }
}