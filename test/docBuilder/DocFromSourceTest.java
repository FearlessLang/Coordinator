package docBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import core.E.Literal;
import core.FearlessException;
import core.OtherPackages;
import core.TName;
import main.FrontendLogicMain;
import main.FrontendLogicMain.TestSource;
import tools.Fs;
import tools.SourceOracle;
import userMessages.UserError;

final class DocFromSourceTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  private static final URI file= URI.create("fear:/_p/_rank_app.fear");
  private static final String a= "A:{ .m: A -> this; }\n";
  record Docs(String html, String text, List<TestSource> tests){}
  static Docs docs(Path tmp, OtherPackages other, String source){
    var oracle= SourceOracle.debugBuilder().putURI(file, source).build();
    var html= tmp.resolve("p.html");
    var tests= new ArrayList<TestSource>();
    try{
      new FrontendLogicMain().of("p", Map.of(), oracle.allFiles(), other, core -> {
        var builder= new HtmlDocBuilder(oracle, other, core, Optional.empty(), "p", html, true);
        core.forEach(builder::visitAll);
        builder.complete(new FrontendLogicMain().names("p", Map.of(), oracle.allFiles(), other), false, Runnable::run);
        var renderer= new HtmlDocRenderer(DocNames.uses("p", core), new DocResolver("p", builder.types, other, TName::s), Map.of(), Optional.empty());
        if (!renderer.testableTypes().isEmpty()){ tests.addAll(renderer.renderTests(TName::s)); }
        return List.of();
      });
    }
    catch (FearlessException fe){ throw new AssertionFailedError(fe.render(oracle)); }
    return new Docs(Fs.readUtf8(html), Fs.readUtf8(html.resolveSibling("p.txt")), List.copyOf(tests));
  }
  static Docs docs(Path tmp, String source){ return docs(tmp, OtherPackages.empty(), source); }
  static List<Literal> compiled(String pkg, String source, OtherPackages other){
    var o= SourceOracle.debugBuilder().putURI(URI.create("fear:/_"+pkg+"/_rank_"+pkg+".fear"), source).build();
    return new FrontendLogicMain().of(pkg, Map.of(), o.allFiles(), other);
  }
  @Test void aNamedLiteralOnTheLineOfADocumentedMethodTakesNeitherItsDocumentationNorItsExamples(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Cases:{
  ///Gives an A.
  //> .check{Cases.none}
  .none: A -> Named: A{ .m -> A };
  }
""");
    utils.Err.strCmp("""
package p

A
\t.m:A

Cases
\t.none:A
    Gives an A.
    example:
      .check{Cases.none}

Named : A
\t.m:A
    from: A.m

""", docs.text());
    assertEquals(List.of("_Cases_Examples: base.Test {::", "AllAutoTests_p: base.UnitTests {::"),
      docs.tests().stream().map(t -> t.text().lines().findFirst().orElseThrow()).toList());
  }
  @Test void aNamedLiteralOnTheLineOfADocumentedTypeTakesNoneOfItsDocumentation(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
///Holds an Inner.
Outer:{ .get: A -> Inner: A{ .m -> A }; }
""");
    utils.Err.strCmp("""
[###]
Inner : A
\t.m:A
    from: A.m

Outer
  Holds an Inner.
\t.get:A
""", docs.text());
  }
  @Test void aReferenceMayNameATypeByTheNameAUseGivesIt(@TempDir Path tmp){
    var q= compiled("q", "Thing:{}\n", OtherPackages.empty());
    var docs= docs(tmp, OtherPackages.start(Map.of(), q, 0L), """
use q.Thing as Alias;
///Wraps an `Alias`.
Wrap:{ .get: Alias; }
""");
    utils.Err.strCmp("[###]Wrap\n  Wraps an `Alias`.\n[###]", docs.text());
  }
  @Test void aReferenceToAUsedTypeIsNotAmbiguousWithASameNamedTypeOfAnotherPackage(@TempDir Path tmp){
    var q= compiled("q", "Widget:{}\n", OtherPackages.empty());
    var r= compiled("r", "Widget:{}\n", OtherPackages.empty());
    var docs= docs(tmp, OtherPackages.start(Map.of(), Stream.concat(q.stream(), r.stream()).toList(), 0L), """
use q.Widget as Widget;
///Wraps a `Widget`.
Wrap:{ .get: Widget; }
""");
    utils.Err.strCmp("[###]<p>Wraps a <code><a href=\"q.html#type-q_2e_Widget-0\">Widget</a></code>.</p>[###]", docs.html());
  }
  @Test void aTypeParameterIsANameInScopeWhereTheCommentIsWritten(@TempDir Path tmp){
    var docs= docs(tmp, """
///Holds one `X`.
Box[X:imm]:{ .get: X; }
""");
    utils.Err.strCmp("[###]Box[X:imm]\n  Holds one `X`.\n[###]", docs.text());
  }
  @Test void aDocBlockRightAboveAnAnonymousLiteralDocumentsThatLiteral(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Maker:{
  .make: A ->
    ///Doc of the anonymous A
    A{ .m -> A };
  }
""");
    utils.Err.strCmp("[###]anonymous literal at [###]\n  Doc of the anonymous A\n[###]", docs.text());
  }
  @Test void aTrailingDocAfterAMethodReturningATypedAnonymousLiteralDocumentsTheMethod(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Maker:{
  .make: A -> A{ .m -> A }; /// makes an A
  }
""");
    utils.Err.strCmp("[###]Maker\n\t.make:A\n    makes an A[###]", docs.text());
  }
  @Test void aCommentLineOfSlashesOrDashesIsAPlainComment(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
////////////////////////////
B:{ .m: A -> A.m
  //---------- then once more
  .m; }
""");
    utils.Err.strCmp("[###]\n\nB\n\t.m:A", docs.text());
    assertEquals(List.of(), docs.tests());
  }
  @Test void aFencedBlockWrittenWithNoSpaceAfterTheSlashesKeepsItsIndentation(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
///Example:
///```
///Foo:{
///  .m
///  }
///```
B:{ .m: A -> A; }
""");
    utils.Err.strCmp("[###]<pre class=\"fenced\">Foo:{\n  .m\n  }</pre>[###]", docs.html());
  }
  @Test void aFenceNamingItsLanguageIsAFence(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
///Example:
///```fearless
///B.m `raw`
///```
B:{ .m: A -> A; }
""");
    utils.Err.strCmp("[###]<pre class=\"fenced\">B.m `raw`</pre>[###]", docs.html());
  }
  @Test void aParameterOfTheEnclosingMethodIsANameInScopeInTheDocOfAnAnonymousLiteralMethod(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Maker:{
  .make(seed: A): A -> A{
    ///Gives back `seed`.
    .m -> seed;
    };
  }
""");
    utils.Err.strCmp("[###]\t.m:A\n    Gives back `seed`.[###]", docs.text());
  }
  @Test void aParameterOfAnAbstractMethodIsANameInScope(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
B:{
  ///Gives back `x`.
  .f(x: A): A;
  }
""");
    utils.Err.strCmp("[###]\t.f([###]):A\n    Gives back `x`.[###]", docs.text());
  }
  @Test void aTrailingDocAttachedToNothingIsExplainedAsATrailingDoc(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
B:{
  .m: A ->
    A; /// returns an A
  }
"""));
    utils.Err.strCmp("Misplaced doc comment.[###]same line[###]", ex.getMessage());
  }
  @Test void proseBetweenTwoExamplesStaysBetweenThem(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
///Sorting:
//> .sorted
///Reversing:
//> .reversed
B:{ .m: A -> A; }
""");
    utils.Err.strCmp("[###]<p>Sorting:</p>[###].sorted[###]<p>Reversing:</p>[###].reversed[###]", docs.html());
  }
  @Test void aTypeInTheListOfTypesMayWrapAfterEachComma(@TempDir Path tmp){
    var docs= docs(tmp, "Pair[X:imm,Y:imm]:{}\n");
    utils.Err.strCmp("[###]<li><a href=\"#type-p_2e_Pair-2\">Pair[X:imm,<wbr>Y:imm]</a></li>[###]", docs.html());
  }
}
