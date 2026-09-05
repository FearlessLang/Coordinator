package docBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import core.B;
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

  @Test void aRunnableExampleLineBetweenAProseDocAndItsDeclarationIsStillReachedAsAPureDoc(){
    var text= ""
      + "/// description of foo\n"
      + "//>   .check{foo.assertEq(1)}\n"
      + "Foo: Bar {\n"
      + "}\n";
    var docs= new SourceDocs(file, text);
    var before= docs.docsAt(Pos.of(file,3,1), true);
    assertEquals(List.of("description of foo","  .check{foo.assertEq(1)}"), before.stream().map(DocOcc::text).toList());
    assertFalse(before.get(0).example(), "the /// line is prose, not an example");
    assertTrue(before.get(1).example(), "the //> line is a runnable example");
  }

  @Test void severalRunnableExampleLinesAllReachTheDeclarationInSourceOrder(){
    var text= ""
      + "/// description of foo\n"
      + "//>   .check{foo.assertEq(1)}\n"
      + "//>   .checkDetErr({_ -> True}, {bar})\n"
      + "Foo: Bar {\n"
      + "}\n";
    var docs= new SourceDocs(file, text);
    var before= docs.docsAt(Pos.of(file,4,1), true);
    assertEquals(
      List.of("description of foo","  .check{foo.assertEq(1)}","  .checkDetErr({_ -> True}, {bar})"),
      before.stream().map(DocOcc::text).toList());
  }

  @Test void aTrailingInlineDocCommentOnTheDeclarationLineIsFound(){
    var text= "Foo: Bar { /// trailing doc\n";
    var docs= new SourceDocs(file, text);
    var found= docs.docsAt(Pos.of(file,1,1), false);
    assertEquals(1, found.size());
    assertEquals("trailing doc", found.get(0).text());
    assertTrue(found.get(0).inline(), "a doc comment following code on the same line must be inline, not pure");
  }

  //documentation that reaches no declaration is documentation nobody reads: the usual
  //cause is a /// or //> written with one mark too few, which ends the block silently.
  @Test void aDocCommentThatReachesNoDeclarationIsReported(){
    var text= ""
      + "/// orphaned by the line below\n"
      + "//  one slash short\n"
      + "/// attached\n"
      + "Foo: Bar {\n"
      + "}\n";
    var docs= new SourceDocs(file, text);
    docs.docsAt(Pos.of(file,4,1), true);
    var runs= docs.orphanRuns();
    assertEquals(1, runs.size(), "the run above the // line is attached to nothing");
    assertEquals(List.of("orphaned by the line below"), runs.getFirst().stream().map(DocOcc::text).toList());
  }

  @Test void aDocBlockThatReachesItsDeclarationLeavesNoOrphan(){
    var text= "/// attached\nFoo: Bar {\n}\n";
    var docs= new SourceDocs(file, text);
    docs.docsAt(Pos.of(file,2,1), true);
    assertTrue(docs.orphanRuns().isEmpty());
  }

  @Test void anEmptyLineInsideADocBlockEndsTheParagraphNotTheBlock(){
    var text= "/// first\n\n/// second\nFoo: Bar {\n}\n";
    var docs= new SourceDocs(file, text);
    var res= docs.docsAt(Pos.of(file,4,1), true);
    assertEquals(List.of("first","","second"), res.stream().map(DocOcc::text).toList());
    assertTrue(docs.orphanRuns().isEmpty(), "an empty line must not detach what is above it");
  }

  @Test void anEmptyLineBetweenRunnableExamplesReadsAsAnEmptyExample(){
    var text= "//> one\n\n//> two\nFoo: Bar {\n}\n";
    var docs= new SourceDocs(file, text);
    var res= docs.docsAt(Pos.of(file,4,1), true);
    assertEquals(3, res.size());
    assertTrue(res.get(1).text().isBlank() && res.get(1).example(),
      "between two //> lines the empty line is an example line, not prose");
  }

  //trimming an empty line off the end of a block is about what to show; the line was
  //still reached by the declaration and must not be reported as attached to nothing.
  @Test void anEmptyDocLineAtTheEndOfABlockIsTrimmedButStillAttached(){
    var text= "/// doc\n///\nFoo: Bar {\n}\n";
    var docs= new SourceDocs(file, text);
    var res= docs.docsAt(Pos.of(file,3,1), true);
    assertEquals(List.of("doc"), res.stream().map(DocOcc::text).toList());
    assertTrue(docs.orphanRuns().isEmpty());
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

  @Test void renderExamplesWrapsRunnableExampleLinesInAnExpandableDetailsBlock(){
    var sb= new StringBuilder();
    var renderer= new HtmlDocRenderer("pkg", java.util.Map.of(), List.of(), OtherPackages.empty(), java.util.Map.of());
    renderer.renderExamples(sb, List.of(".check{1.assertEq(1)}"));
    var rendered= sb.toString();
    assertTrue(rendered.contains("<details class=\"examples\">"), "examples must render as an expandable details block: "+rendered);
    assertTrue(rendered.contains("<pre class=\"example\">.check{1.assertEq(1)}</pre>"), rendered);
  }

  @Test void renderExamplesRendersNothingWhenThereAreNoExamples(){
    var sb= new StringBuilder();
    var renderer= new HtmlDocRenderer("pkg", java.util.Map.of(), List.of(), OtherPackages.empty(), java.util.Map.of());
    renderer.renderExamples(sb, List.of());
    assertEquals("", sb.toString());
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
  private static List<B> twoBounds(){
    return List.of(new B("X1",java.util.EnumSet.of(RC.imm)), new B("X2",java.util.EnumSet.of(RC.imm)));
  }
  private static M namedMethod(String selector, TName origin){
    var sig= new Sig(RC.imm, new MName(selector,0), List.of(), List.of(), retVoid(), origin, false, TSpan.fromPos(Pos.unknown,1));
    return new M(sig, List.of(), Optional.empty());
  }
  private static M methodTaking(String selector, TName origin, String paramName, TName paramType){
    var t= new T.RCC(RC.imm, new T.C(paramType, List.of()), TSpan.fromPos(Pos.unknown,1));
    var sig= new Sig(RC.imm, new MName(selector,1), List.of(), List.of(t), retVoid(), origin, false, TSpan.fromPos(Pos.unknown,1));
    return new M(sig, List.of(paramName), Optional.empty());
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

  @Test void inheritedMethodsListsEveryProviderAlongAnOverrideChainEvenTheShadowedOne(){
    var baseName= new TName("pkg.Base",0,Pos.unknown);
    var midName= new TName("pkg.Mid",0,Pos.unknown);
    var baseSame= fooMethod(RC.imm, baseName);
    var base= namedType("pkg.Base", List.of(), List.of(baseSame));
    var midSame= fooMethod(RC.imm, midName);
    var mid= namedType("pkg.Mid", List.of(new T.C(baseName, List.of())), List.of(midSame));
    var top= namedType("pkg.Top",
      List.of(new T.C(midName, List.of()), new T.C(baseName, List.of())), List.of());

    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), List.of(base, mid, top));
    var refs= builder.inheritedMethods(top, midSame);

    assertEquals(2, refs.size());
    assertTrue(refs.stream().anyMatch(r->r.method()==midSame));
    assertTrue(refs.stream().anyMatch(r->r.method()==baseSame));
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

  @Test void aBareTypeNameThatMatchesTwoLocalArityVariantsIsAmbiguous(){
    var one= namedType("pkg.Pair", List.of(), List.of());
    var two= new Literal(RC.imm, new TName("pkg.Pair",2,Pos.unknown), twoBounds(), List.of(), "this", List.of(), freshSrc(), false);
    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), List.of(one,two));
    builder.visitLiteral(one);
    builder.visitLiteral(two);
    var resolver= new DocResolver("pkg", builder.types, OtherPackages.empty());

    var link= resolver.resolveType(new DocRef.TypeName(Optional.empty(),"Pair",OptionalInt.empty()));

    assertTrue(link.isPresent());
    assertTrue(link.get() instanceof DocLink.Ambiguous);
    assertEquals(2, ((DocLink.Ambiguous)link.get()).options().size());
  }

  @Test void anExplicitArityResolvesAWouldBeAmbiguousTypeNamePrecisely(){
    var one= namedType("pkg.Pair", List.of(), List.of());
    var two= new Literal(RC.imm, new TName("pkg.Pair",2,Pos.unknown), twoBounds(), List.of(), "this", List.of(), freshSrc(), false);
    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), List.of(one,two));
    builder.visitLiteral(one);
    builder.visitLiteral(two);
    var resolver= new DocResolver("pkg", builder.types, OtherPackages.empty());

    var link= resolver.resolveType(new DocRef.TypeName(Optional.empty(),"Pair",OptionalInt.of(2)));

    assertTrue(link.get() instanceof DocLink.Resolved);
    assertEquals(two.name(), ((DocLink.Resolved)link.get()).hit().owner());
  }

  @Test void aBareSelectorDeclaredByTwoDifferentLocalTypesIsAmbiguous(){
    var aName= new TName("pkg.A",0,Pos.unknown);
    var bName= new TName("pkg.B",0,Pos.unknown);
    var a= namedType("pkg.A", List.of(), List.of(namedMethod(".bar",aName)));
    var b= namedType("pkg.B", List.of(), List.of(namedMethod(".bar",bName)));
    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), List.of(a,b));
    builder.visitLiteral(a);
    builder.visitLiteral(b);
    var resolver= new DocResolver("pkg", builder.types, OtherPackages.empty());

    var link= resolver.resolve(new DocRef.MethodName(Optional.empty(),".bar",OptionalInt.empty()), Scope.of(a));

    assertTrue(link.get() instanceof DocLink.Ambiguous);
    assertEquals(2, ((DocLink.Ambiguous)link.get()).options().size());
  }

  @Test void aQualifiedMethodOnAForeignTypeResolvesThroughAnInheritedSupertypeMethod(){
    var supName= new TName("other.Sup",0,Pos.unknown);
    var subName= new TName("other.Sub",0,Pos.unknown);
    var sup= namedType("other.Sup", List.of(), List.of(namedMethod(".bar",supName)));
    var sub= namedType("other.Sub", List.of(new T.C(supName,List.of())), List.of());
    var other= OtherPackages.start(Map.of(), List.of(sup,sub), 0L);
    var resolver= new DocResolver("pkg", List.of(), other);

    var link= resolver.resolve(new DocRef.MethodName(Optional.of(new DocRef.TypeName(Optional.of("other"),"Sub",OptionalInt.empty())),".bar",OptionalInt.empty()), Scope.of(sub));

    assertTrue(link.isPresent());
    assertTrue(link.get() instanceof DocLink.Resolved);
    assertEquals(subName, ((DocLink.Resolved)link.get()).hit().owner());
  }

  //"this" and the parameter names are what the doc author actually writes; they mean
  //the type being documented and the type of that parameter.
  @Test void thisNamesTheTypeBeingDocumented(){
    var owner= namedType("pkg.Holder", List.of(), List.of());
    var resolver= new DocResolver("pkg", List.of(), OtherPackages.empty());
    var link= resolver.resolve(new DocRef.LocalName("this"), Scope.of(owner));
    assertEquals(owner.name(), ((DocLink.Resolved)link.get()).hit().owner());
  }

  @Test void aParameterNameLinksToItsOwnType(){
    var ownerName= new TName("pkg.Holder",0,Pos.unknown);
    var paramType= new TName("pkg.Thing",0,Pos.unknown);
    var m= methodTaking(".take", ownerName, "elem", paramType);
    var owner= namedType("pkg.Holder", List.of(), List.of(m));
    var resolver= new DocResolver("pkg", List.of(), OtherPackages.empty());

    var link= resolver.resolve(new DocRef.LocalName("elem"), Scope.of(owner, m));

    assertEquals(paramType, ((DocLink.Resolved)link.get()).hit().owner());
  }

  @Test void aNameThatIsNotAParameterOfThisMethodIsUnresolved(){
    var ownerName= new TName("pkg.Holder",0,Pos.unknown);
    var m= methodTaking(".take", ownerName, "elem", new TName("pkg.Thing",0,Pos.unknown));
    var owner= namedType("pkg.Holder", List.of(), List.of(m));
    var resolver= new DocResolver("pkg", List.of(), OtherPackages.empty());
    assertTrue(resolver.resolve(new DocRef.LocalName("e"), Scope.of(owner, m)).isEmpty(),
      "a doc naming a parameter the signature does not declare must be reported");
  }

  @Test void aParameterIsOnlyInScopeForItsOwnMethod(){
    var ownerName= new TName("pkg.Holder",0,Pos.unknown);
    var m= methodTaking(".take", ownerName, "elem", new TName("pkg.Thing",0,Pos.unknown));
    var owner= namedType("pkg.Holder", List.of(), List.of(m));
    var resolver= new DocResolver("pkg", List.of(), OtherPackages.empty());
    assertTrue(resolver.resolve(new DocRef.LocalName("elem"), Scope.of(owner)).isEmpty(),
      "a parameter must not be in scope in the type level documentation");
  }

  @Test void aMethodOnThisResolvesAgainstTheTypeBeingDocumented(){
    var ownerName= new TName("pkg.Holder",0,Pos.unknown);
    var bar= namedMethod(".bar", ownerName);
    var owner= namedType("pkg.Holder", List.of(), List.of(bar));
    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), List.of(owner));
    builder.visitLiteral(owner);
    var resolver= new DocResolver("pkg", builder.types, OtherPackages.empty());

    var ref= new DocRef.MethodName(Optional.of(new DocRef.LocalName("this")),".bar",OptionalInt.empty());
    var link= resolver.resolve(ref, Scope.of(owner));

    assertEquals(ownerName, ((DocLink.Resolved)link.get()).hit().owner());
  }

  @Test void aMethodThatTheDocumentedTypeDoesNotHaveIsUnresolved(){
    var owner= namedType("pkg.Holder", List.of(), List.of());
    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), List.of(owner));
    builder.visitLiteral(owner);
    var resolver= new DocResolver("pkg", builder.types, OtherPackages.empty());
    var ref= new DocRef.MethodName(Optional.of(new DocRef.LocalName("this")),".nope",OptionalInt.empty());
    assertTrue(resolver.resolve(ref, Scope.of(owner)).isEmpty());
  }

  @Test void aReferenceThatMatchesNothingAnywhereIsUnresolved(){
    var resolver= new DocResolver("pkg", List.of(), OtherPackages.empty());
    assertTrue(resolver.resolveType(new DocRef.TypeName(Optional.empty(),"NowhereToBeFound",OptionalInt.empty())).isEmpty());
  }

  @Test void anUnqualifiedNameFoundOnlyInAThirdPackageStillResolves(){
    var other= OtherPackages.start(Map.of(), List.of(namedType("third.Widget", List.of(), List.of())), 0L);
    var resolver= new DocResolver("pkg", List.of(), other);
    var link= resolver.resolveType(new DocRef.TypeName(Optional.empty(),"Widget",OptionalInt.empty()));
    assertTrue(link.get() instanceof DocLink.Resolved);
  }

  @Test void aLocalTypeWinsOverASameNamedTypeInAnotherPackage(){
    var local= namedType("pkg.Widget", List.of(), List.of());
    var other= OtherPackages.start(Map.of(), List.of(namedType("third.Widget", List.of(), List.of())), 0L);
    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, other, List.of(local));
    builder.visitLiteral(local);
    var resolver= new DocResolver("pkg", builder.types, other);

    var link= resolver.resolveType(new DocRef.TypeName(Optional.empty(),"Widget",OptionalInt.empty()));

    assertTrue(link.get() instanceof DocLink.Resolved);
    assertEquals(local.name(), ((DocLink.Resolved)link.get()).hit().owner());
  }

  //Two ways a link can point at nothing: its disambiguation section was never emitted,
  //or it names a type/method the renderer skips as not visible. Both look identical to
  //a reader, who clicks and sees the start page, so both are checked over real output.
  @Test void everyInPageLinkHasSomethingToLandOn(){
    var html= renderedFixture();
    var linked= idsIn(html, "href=\"#");
    var ids= idsIn(html, "id=\"");
    assertFalse(linked.isEmpty(), "this fixture must actually produce in-page links");
    assertEquals(List.of(), linked.stream().distinct().filter(l->!ids.contains(l)).toList(),
      "every in-page link must have a matching id");
  }

  @Test void aDisambiguationPageNeverOffersAMethodOfATypeThatIsNotRendered(){
    var html= renderedFixture();
    assertFalse(html.contains("Hidden"), "an undocumented anonymous literal must not reach the page: "+html);
  }

  @Test void renderTextNeverContainsHtmlMarkup(){
    var text= renderedTextFixture();
    assertFalse(text.contains("<"), "the plain text doc must contain no markup at all: "+text);
  }

  @Test void renderTextNeverLeaksAHiddenAnonymousLiteral(){
    var text= renderedTextFixture();
    assertFalse(text.contains("Hidden"), "an undocumented anonymous literal must not reach the text doc either: "+text);
  }

  @Test void renderTextProducesAPlainSignatureLineWithNoPlaceholderForMissingDocs(){
    var ownerName= new TName("pkg.Holder",0,Pos.unknown);
    var bar= namedMethod(".bar", ownerName);
    var owner= namedType("pkg.Holder", List.of(), List.of(bar));
    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), List.of(owner));
    builder.visitLiteral(owner);

    var text= new HtmlDocRenderer("pkg", Map.of(), builder.types, OtherPackages.empty(), Map.of()).renderText();

    assertEquals("""
package pkg

Holder
  .bar:base.Void

""", text);
  }

  @Test void renderTextIndentsDocProseAndExamplesUnderTheirDeclaredMethod(){
    var ownerName= new TName("pkg.Holder",0,Pos.unknown);
    var bar= namedMethod(".bar", ownerName);
    var owner= namedType("pkg.Holder", List.of(), List.of());
    var typeDoc= new TypeDoc(owner, List.of());
    typeDoc.declared(Pos.of(file,9,1), bar, List.of(
      new DocOcc(file,9,1,4,"does the bar thing",true,false),
      new DocOcc(file,9,1,4,".check{bar.assertOk}",true,true)
    ), List.of());

    var text= new HtmlDocRenderer("pkg", Map.of(), List.of(typeDoc), OtherPackages.empty(), Map.of()).renderText();

    assertEquals("""
package pkg

Holder
  .bar:base.Void
    does the bar thing
    example:
      .check{bar.assertOk}

""", text);
  }

  @Test void renderTextShowsPlainFromProvenanceForAnInheritedMethod(){
    var supName= new TName("pkg.Sup",0,Pos.unknown);
    var subName= new TName("pkg.Sub",0,Pos.unknown);
    var supMut= fooMethod(RC.mut, supName);
    var sup= namedType("pkg.Sup", List.of(), List.of(supMut));
    var subMut= fooMethod(RC.mut, subName);
    var sub= namedType("pkg.Sub", List.of(new T.C(supName, List.of())), List.of(subMut));
    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), List.of(sup, sub));
    builder.visitLiteral(sup);
    builder.visitLiteral(sub);

    var text= new HtmlDocRenderer("pkg", Map.of(), builder.types, OtherPackages.empty(), Map.of()).renderText();

    assertTrue(text.contains("from: Sup.foo"), text);
    assertFalse(text.contains("<a "), text);
  }

  private static HtmlDocBuilder fixtureBuilder(){
    var aName= new TName("pkg.A",0,Pos.unknown);
    var bName= new TName("pkg.B",0,Pos.unknown);
    var hiddenName= new TName("pkg.Hidden",0,Pos.unknown);
    var a= namedType("pkg.A", List.of(), List.of(namedMethod(".bar",aName)));
    var b= namedType("pkg.B", List.of(), List.of(namedMethod(".bar",bName)));
    //an anonymous literal with no docs: never rendered, so never a link target
    var hidden= new Literal(RC.imm, hiddenName, List.of(), List.of(), "this",
      List.of(namedMethod(".bar",hiddenName)), freshSrc(), true);
    SourceOracle oracle= List::of;
    var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), List.of(a,b,hidden));
    builder.visitLiteral(a);
    builder.visitLiteral(b);
    builder.visitLiteral(hidden);
    return builder;
  }

  private static String renderedFixture(){
    var builder= fixtureBuilder();
    return new HtmlDocRenderer("pkg", Map.of(), builder.types, OtherPackages.empty(), Map.of()).render();
  }

  private static String renderedTextFixture(){
    var builder= fixtureBuilder();
    return new HtmlDocRenderer("pkg", Map.of(), builder.types, OtherPackages.empty(), Map.of()).renderText();
  }

  private static List<String> idsIn(String html, String prefix){
    var res= new java.util.ArrayList<String>();
    for (int i= html.indexOf(prefix); i >= 0; i= html.indexOf(prefix,i+1)){
      var from= i+prefix.length();
      res.add(html.substring(from, html.indexOf('"', from)));
    }
    return res;
  }
}
