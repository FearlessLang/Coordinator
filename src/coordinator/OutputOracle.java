package coordinator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public interface OutputOracle{
  Path pathOf(List<String> path);
  default void write(List<String> path, Consumer<Consumer<String>> dataProducer){
    try (BufferedWriter writer = Files.newBufferedWriter(pathOf(path))){
      dataProducer.accept(content -> {
        try { writer.write(content); }
        catch (IOException e){ throw new UncheckedIOException(e); }
      });
    }
    catch (IOException e){ throw new UncheckedIOException(e); }
  }
}