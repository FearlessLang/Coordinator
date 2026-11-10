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
        builder.complete(TName::s, false, Runnable::run);
        var renderer= new HtmlDocRenderer(DocNames.uses("p", core), new DocResolver("p", builder.types, other), Map.of(), Optional.empty());
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
  @Test void anExtraClosingBraceInAnExampleIsReportedAtThatBrace(@TempDir Path tmp){
    var oracle= SourceOracle.debugBuilder().putURI(file, a+"""
Calc:{
  //> .check{Calc.one}}
  .one: A -> A;
  //> .check{Calc.two}
  .two: A -> A;
  }
""").build();
    var fe= assertThrows(FearlessException.class, () -> new FrontendLogicMain().of("p", Map.of(), oracle.allFiles(), OtherPackages.empty(), core -> {
      var builder= new HtmlDocBuilder(oracle, OtherPackages.empty(), core, Optional.empty(), "p", tmp.resolve("p.html"), true);
      core.forEach(builder::visitAll);
      return builder.complete(TName::s, true, Runnable::run);
    }));
    utils.Err.strCmp("""
In file: fear:/_p/_rank_app.fear

003|   //> .check{Calc.one}}
   |                       ^

While inspecting groups of parenthesis
Unopened "}".
[###]""", fe.render(oracle));
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
    utils.Err.strCmp("[###]Maker\n\t.make:A\n    makes an A\n[###]", docs.text());
  }
}
