package coordinator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import core.E.Literal;
import core.OtherPackages;
import utils.Bug;

public interface OutputOracle{
  Path rootDir();
  default long lastModified(List<String> path){ throw Bug.todo(); }//returns -1 if file not exists
  default long baseApiStamp(){ return lastModified(List.of("base.json")); }
  default long mapStamp(){ return lastModified(List.of("_map.json")); }
  default long pkgApiStamp(String pkg){ return lastModified(List.of(pkg+".json")); }
  
  default void write(String path, Consumer<Consumer<String>> dataProducer){
    try (BufferedWriter writer = Files.newBufferedWriter(rootDir().resolve(path))){
      dataProducer.accept(content -> {
        try { writer.write(content); }
        catch (IOException e){ throw new UncheckedIOException(e); }
      });
    }
    catch (IOException e){ throw new UncheckedIOException(e); }
  }
  default OtherPackages addCachedPkgApi(OtherPackages other, String pkg){ throw Bug.todo(); }//READS the pkg info and adds to other; Does not update the disk. Just reads info
  default long commitPkgApi(String pkg, List<Literal> core, long minExclusiveMillis){ throw Bug.todo(); }
  //commitPkgApi only write if different from the old, and in that case it will bumps mtime strictly above minExclusiveMillis
  default long commitMap(Map<String,Map<String,String>> map, long minExclusiveMillis){ throw Bug.todo(); }
  //commitMap only write if different from the old, and in that case it will bumps mtime strictly above minExclusiveMillis
}