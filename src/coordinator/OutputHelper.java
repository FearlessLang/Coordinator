package coordinator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import apiJson.ApiJson;
import core.E.Literal;
import tools.Fs;

/// The text of a `<pkg>.json`: {"inputsSnapshot":"...","manifest":"...","types":[...]}, the manifest as an Info text.
final class OutputHelper{
  static String apiJson(List<Literal> core, String inputsSnapshot, MainsInfo manifest){
    return "{\"inputsSnapshot\":"+q(inputsSnapshot)+",\n\"manifest\":"+q(manifest.print())+",\n\"types\":"+ApiJson.toJson(core)+"}\n";
  }
  private static String q(String s){ return "\""+s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")+"\""; }
  static Optional<String> read(Path p){
    if (!Fs.exists(p)){ return Optional.empty(); }
    return Optional.of(new String(Fs.of(() -> Fs.readAllBytes(p)), StandardCharsets.UTF_8));
  }
}
