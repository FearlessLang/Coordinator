package coordinator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import apiJson.ApiJson;
import core.E.Literal;
import core.RC;
import core.Src;
import core.TName;
import utils.Pos;

// FAILING-BY-DESIGN: apiJson.ApiJson.typeJ never serializes Literal.infName (the
// flag that marks an anonymous/"inferred name" literal, such as a `#{...}`
// expression nested inside a method body), and LimitedJsonParser.typeLit
// hardcodes it back to false on every reload, no matter the original value.
// A package's public API cache (<pkg>.json, read by coordinator.OutputOracle
// for every downstream package) is built from AllLs.of(core), which walks
// into nested literal expressions and so DOES include infName=true literals;
// once round-tripped through the cache they silently come back as
// infName=false. message.Err/TypeSystemErrors/CompactPrinter branch on
// infName() to decide whether to print a type's own name or describe it as
// an anonymous object literal, so the same type can be described two
// different ways depending on whether its defining package was just
// compiled or served from this cache.
final class ApiJsonRoundTripTest{
  @Test void anInferredNameLiteralLosesItsInfNameFlagAcrossTheApiCache(){
    var name= new TName("Anon", 0, Pos.unknown);
    var original= new Literal(RC.imm, name, List.of(), List.of(), "this", List.of(), Src.syntetic, true);
    assertTrue(original.infName());

    var json= ApiJson.toJSon(List.of(original));
    var reloaded= new LimitedJsonParser(json, Path.of("dummy")).apiJsonToMap().get(name);

    assertFalse(reloaded.infName());//pinned wrong behavior: the cache silently flips this to false
  }
}
