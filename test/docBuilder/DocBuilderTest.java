package docBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import core.E.Literal;
import core.M;
import core.MName;
import core.OtherPackages;
import core.RC;
import core.Sig;
import core.Src;
import core.T;
import core.TName;
import core.TSpan;
import tools.SourceOracle;
import utils.Pos;

final class DocBuilderTest{
  private static final URI file= URI.create("fear:/_pkg/a.fear");

  @Test void plainLineCommentWithAStrayQuoteDoesNotSwallowALaterDocComment(){
    var text= ""
      + "// a comment mentioning a quote: \"\n"
      + "/// this is meant to be a doc comment\n"
      + "Foo: Bar {\n"
      + "}\n";
    var docs= new SourceDocs(file, text);
    var atLine2= docs.docsByLine.getOrDefault(2, List.of());
    assertEquals(1, atLine2.size(), "the /// doc comment on line 2 must be recognized as a doc comment");
    assertEquals("this is meant to be a doc comment", atLine2.get(0).text());
  }

  @Test void plainLineCommentWithAStrayBacktickAlsoSwallowsALaterDocComment(){
    var text= ""
      + "// a comment mentioning a backtick: `\n"
      + "/// this doc should still be found\n"
      + "Foo: Bar {\n"
      + "}\n";
    var docs= new SourceDocs(file, text);
    var atLine2= docs.docsByLine.getOrDefault(2, List.of());
    assertEquals(1, atLine2.size(), "the /// doc comment on line 2 must be recognized as a doc comment");
    assertEquals("this doc should still be found", atLine2.get(0).text());
  }

  @Test void aBlockCommentIsNeverTreatedAsADocCommentEvenIfItContainsTripleSlashLookingText(){
    var text= ""
      + "/* /// not a doc, just text inside a block comment */\n"
      + "Foo: Bar {\n"
      + "}\n";
    var docs= new SourceDocs(file, text);
    assertTrue(docs.docsByLine.isEmpty(), "a block comment must never register a doc occurrence: "+docs.docsByLine);
  }

  @Test void consecutivePureDocLinesAboveADeclarationAreCollectedInSourceOrder(){
    var text= ""
      + "/// first line of doc\n"
      + "/// second line of doc\n"
      + "Foo: Bar {\n"
      + "}\n";
    var docs= new SourceDocs(file, text);
    var before= docs.docsAt(Pos.of(file,3,1), true);
    assertEquals(List.of("first line of doc","second line of doc"), before.stream().map(DocOcc::text).toList());
  }

  @Test void aTrailingInlineDocCommentOnTheDeclarationLineIsFound(){
    var text= "Foo: Bar { /// trailing doc\n";
    var docs= new SourceDocs(file, text);
    var found= docs.docsAt(Pos.of(file,1,1), false);
    assertEquals(1, found.size());
    assertEquals("trailing doc", found.get(0).text());
    assertTrue(found.get(0).inline(), "a doc comment following code on the same line must be inline, not pure");
  }

  private static Literal literal(String name, RC rc){
    return new Literal(rc, new TName(name, 0, Pos.unknown), List.of(), List.of(), "this", List.of(), Src.syntetic, false);
  }

  @Test void aLocalTypeThatSharesABaseNameShadowsIt(){
    var core= List.of(literal("mypkg.List", RC.imm));
    var uses= DocNames.uses("mypkg", core);
    assertFalse(uses.containsKey("base.List"), "a package-local List must shadow base.List, not alias it");
    assertTrue(uses.containsKey("base.Str"), "unshadowed base names must still be offered for shortening");
  }

  @Test void aSameNamedTypeInADifferentPackageDoesNotShadowABaseName(){
    var core= List.of(literal("otherpkg.List", RC.imm));
    var uses= DocNames.uses("mypkg", core);
    assertTrue(uses.containsKey("base.List"), "a same-named type declared in a DIFFERENT package must not shadow base.List");
  }

  @Test void hEscapesEveryHtmlSpecialCharacterUsedInThisRenderer(){
    assertEquals("&amp;", HtmlDocRenderer.h("&"));
    assertEquals("&lt;", HtmlDocRenderer.h("<"));
    assertEquals("&gt;", HtmlDocRenderer.h(">"));
    assertEquals("&quot;", HtmlDocRenderer.h("\""));
  }

  @Test void hDoesNotDoubleEscapeAnAmpersandIntroducedByEscapingAnotherCharacter(){
    assertEquals("&lt;", HtmlDocRenderer.h("<"));
  }

  @Test void aDocCommentThatLooksLikeAScriptTagIsFullyNeutralized(){
    var rendered= HtmlDocRenderer.h("<script>alert(1)</script>");
    assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", rendered);
  }

  @Test void idPassesThroughPlainAsciiIdentifiersUnchanged(){
    assertEquals("fooBar123", HtmlDocRenderer.id("fooBar123"));
  }

  @Test void idEscapesEveryNonAlphanumericCharacterIncludingUnderscoreItself(){
    assertEquals("_5f_", HtmlDocRenderer.id("_"));
  }

  @Test void idProducesDistinctIdsForDistinctOperatorSelectors(){
    assertNotEquals(HtmlDocRenderer.id("+"), HtmlDocRenderer.id("-"));
    assertNotEquals(HtmlDocRenderer.id("=="), HtmlDocRenderer.id("!="));
  }

  private static Src freshSrc(){
    return new Src(new Src.SrcObj(){
      @Override public Pos pos(){ return Pos.unknown; }
      @Override public TSpan span(){ return TSpan.fromPos(Pos.unknown,1); }
    });
  }
  private static T retVoid(){
    return new T.RCC(RC.imm, new T.C(new TName("base.Void",0,Pos.unknown), List.of()), TSpan.fromPos(Pos.unknown,1));
  }
  private static M fooMethodAt(RC rc, TName origin, TSpan span){
    var sig= new Sig(rc, new MName(".foo",0), List.of(), List.of(), retVoid(), origin, false, span);
    return new M(sig, List.of(), Optional.empty());
  }
  private static M fooMethod(RC rc, TName origin){
    return fooMethodAt(rc, origin, TSpan.fromPos(Pos.unknown,1));
  }
  private static Literal namedType(String name, List<T.C> cs, List<M> ms){
    return new Literal(RC.imm, new TName(name,0,Pos.unknown), List.of(), cs, "this", ms, freshSrc(), false);
  }

  @Test void inheritedMethodsOnlyMatchesTheSupertypeMethodWithTheSameReceiverCapability(){
    var supName= new TName("pkg.Sup",0,Pos.unknown);
    var subName= new TName("pkg.Sub",0,Pos.unknown);
    var supMut= fooMethod(RC.mut, supName);
    var supImm= fooMethod(RC.imm, supName);
    var sup= namedType("pkg.Sup", List.of(), List.of(supMut, supImm));
    var subMut= fooMethod(RC.mut, subName);
    var sub= namedType("pkg.Sub", List.of(new T.C(supName, List.of())), List.of(subMut));

    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), List.of(sup, sub));
    var refs= builder.inheritedMethods(sub, subMut);

    assertEquals(1, refs.size());
    assertTrue(refs.getFirst().method()==supMut);
  }

  @Test void aSingleDeclarationWithNoExplicitRcIsDuplicatedPerRequiredRcAndEachVariantMatchesItsOwnSupertypeCounterpart(){
    var supName= new TName("pkg.Sup",0,Pos.unknown);
    var subName= new TName("pkg.Sub",0,Pos.unknown);
    var supMut= fooMethod(RC.mut, supName);
    var supImm= fooMethod(RC.imm, supName);
    var sup= namedType("pkg.Sup", List.of(), List.of(supMut, supImm));

    var subMut= fooMethod(RC.mut, subName);
    var subImm= fooMethod(RC.imm, subName);
    var sub= namedType("pkg.Sub", List.of(new T.C(supName, List.of())), List.of(subMut, subImm));

    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), List.of(sup, sub));
    builder.visitLiteral(sup);
    builder.visitLiteral(sub);

    var doc= builder.type(sub).methods.getFirst();
    assertEquals(1, builder.type(sub).methods.size());
    assertEquals(2, doc.variants.size());
    assertEquals(2, doc.inheritedFrom.size());
    assertTrue(doc.inheritedFrom.stream().anyMatch(r->r.method()==supMut));
    assertTrue(doc.inheritedFrom.stream().anyMatch(r->r.method()==supImm));
  }
}
