package docBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
        var renderer= new HtmlDocRenderer(Map.of(), new DocResolver("p", builder.types, other, TName::s), Optional.empty());
        var suites= renderer.suites();
        if (!suites.isEmpty()){ tests.addAll(renderer.renderTests(suites, TName::s)); }
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

group public
  A
  Cases
  Named

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
  @Test void aNamedLiteralInsideALambdaStartingOnTheSameLineTakesTheDocBlockAboveThatLine(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Mk:{ #: A }
Use:{ .k(f: Mk): A -> f#; }
Maker:{
  .make: A -> Use
    ///Doc of Inner.
    .k {Inner: A{ .m -> A }};
  }
""");
    utils.Err.strCmp("""
package p

group public
  A
  Inner
  Maker
  Mk
  Use

A
\t.m:A

Inner : A
  Doc of Inner.
\t.m:A
    from: A.m

Maker
\t.make:A

Mk
\t#:A

Use
\t.k(f:Mk):A

""", docs.text());
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
  @Test void aTrailingDocAfterAnUntypedAnonymousLiteralOnItsOwnLineDocumentsThatLiteral(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Maker:{
  .make: A ->
    { .m -> A }; /// makes an A
  }
""");
    utils.Err.strCmp("[###]anonymous literal at [###]\n  makes an A\n[###]", docs.text());
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
  @Test void aTypeParameterOfTheEnclosingTypeIsANameInScopeInTheDocOfAnAnonymousLiteralMethod(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Box[X:imm]:{
  .wrap(x: X): A -> A{
    ///Ignores the `X`.
    .m -> A;
    };
  }
""");
    utils.Err.strCmp("[###]\t.m:A\n    Ignores the `X`.[###]", docs.text());
  }
  @Test void aMethodOfAParameterWhoseTypeIsATypeParameterIsToldThatATypeParameterHasNoMethods(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
B:{
  ///Calls `x.nonexistent` on its argument.
  .f[X:imm](x: X): A -> A;
  }
"""));
    utils.Err.strCmp("Broken reference in a doc comment.[###]`x.nonexistent`[###]The type of \"x\" is a type parameter, and a type parameter has no methods.[###]", ex.getMessage());
  }
  @Test void aMethodAnAnonymousLiteralInheritsIsANameInScopeThroughItsSelfName(@TempDir Path tmp){
    var docs= docs(tmp, """
A:{ .m: A -> this; .n: A -> this; }
Maker:{
  .make: A -> A{'self
    ///Same as `self.m`.
    .n -> self.m;
    };
  }
""");
    utils.Err.strCmp("[###]\t.n:A\n    Same as `self.m`.[###]", docs.text());
  }
  @Test void aFenceAroundAnExampleIsACodeBlockNeverClosed(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Usage:
///```
//> .check{ A.m }
///```
///See `A`.
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("""
Broken backticks in a doc comment.

In file: fear:/_p/_rank_app.fear

003| ///```
   |    ^^^
[###]This code block is never closed.[###]""", ex.getMessage());
  }
  @Test void methodsOfOneNameAreListedByArityAsANumber(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
B:{
  .f(a1: A, a2: A): A -> a1;
  .f(a1: A, a2: A, a3: A, a4: A, a5: A, a6: A, a7: A, a8: A, a9: A, a10: A): A -> a1;
  }
""");
    utils.Err.strCmp("[###]B\n\t.f(a1:A,a2:A):A\n\t.f(a1:A,a2:A,a3:A,a4:A,a5:A,a6:A,a7:A,a8:A,a9:A,a10:A):A", docs.text());
  }
  @Test void anonymousLiteralsOfOneFileAreListedByLineAsANumber(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Maker:{
  .early: A -> A{
    ///Early.
    .m -> A;
    };
  //
  //
  //
  //
  .late: A -> A{
    ///Late.
    .m -> A;
    };
  }
""");
    utils.Err.strCmp("[###]anonymous literal at fear:/_p/_rank_app.fear:3:[###]Early.[###]anonymous literal at fear:/_p/_rank_app.fear:11:[###]Late.[###]", docs.text());
  }
  @Test void aNumberWrittenAsAReferenceIsReportedAsNotAName(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Counts from `0`.
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("Broken reference in a doc comment.[###]`0`[###]This is written as a reference, but it is not a name.[###]", ex.getMessage());
  }
  @Test void aReferenceToTheSelfNameOfAnUndocumentedEnclosingLiteralLinksToSomethingOnThePage(@TempDir Path tmp){
    var html= docs(tmp, """
A:{ .m: A -> this; .n: A -> this; }
Maker:{
  .make: A -> A{'outer
    .m -> A{
      ///Same as `outer.n`.
      .m -> outer.n;
      };
    .n -> A;
    };
  }
""").html();
    var ids= Pattern.compile(" id=\"([^\"]*)\"").matcher(html).results().map(m -> m.group(1)).collect(Collectors.toSet());
    var linked= Pattern.compile("href=\"#([^\"]*)\"").matcher(html).results().map(m -> m.group(1)).distinct().toList();
    assertEquals(List.of(), linked.stream().filter(l -> !ids.contains(l)).toList());
  }
  @Test void aDocAboveALineThatDeclaresNothingDoesNotBlameAPlainCommentLine(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
B:{
  .m: A ->
    ///Calls m on an A.
    A.m;
  }
"""));
    utils.Err.strCmp("""
Misplaced doc comment.[###]
This documentation is attached to nothing.
A /// or //> block documents the declaration right below it, but no declaration
starts right below this block. Move this block right above the declaration it
documents, or turn it into a plain // comment.""", ex.getMessage());
  }
  @Test void aDocAboveAPlainCommentLineIsToldThatThePlainCommentEndsTheBlock(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Gives an A.
// a plain comment
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("Misplaced doc comment.[###]A line starting with // ends the block, so check whether[###]", ex.getMessage());
  }
  @Test void aDisambiguationPageOnAnAnonymousLiteralIsTitledByTheNameTheListShows(@TempDir Path tmp){
    var html= docs(tmp, """
A:{ .m: A -> this; .m(a: A): A -> a; }
Maker:{
  .make: A -> A{'self
    ///Same as `self.m`.
    .m -> self.m(A);
    };
  }
""").html();
    utils.Err.strCmp("[###]<h2>Several match Anon@_rank_app:3.m</h2>[###]", html);
  }
  @Test void aTypeInTheListOfTypesMayWrapAfterEachComma(@TempDir Path tmp){
    var docs= docs(tmp, "Pair[X:imm,Y:imm]:{}\n");
    utils.Err.strCmp("[###]<li><a href=\"#type-p_2e_Pair-2\">Pair[X:imm,<wbr>Y:imm]</a></li>[###]", docs.html());
  }
  @Test void aDisambiguationPageListsItsOptionsByTypeNameThenByArity(@TempDir Path tmp){
    var html= docs(tmp, a+"""
Zed:{ .go: A -> A; .go(a: A): A -> a; }
Ace:{ .go(a: A): A -> a; .go: A -> A; }
///Calls `.go`.
User:{ .m: A -> A; }
""").html();
    utils.Err.strCmp("""
[###]<h2>Several match .go</h2>
<ul class="options">
<li><a href="#method-p_2e_Ace-0-imm-_2e_go-0">Ace.go()</a></li>
<li><a href="#method-p_2e_Ace-0-imm-_2e_go-1">Ace.go(_)</a></li>
<li><a href="#method-p_2e_Zed-0-imm-_2e_go-0">Zed.go()</a></li>
<li><a href="#method-p_2e_Zed-0-imm-_2e_go-1">Zed.go(_)</a></li>
</ul>[###]""", html);
  }
  @Test void anEmptyLineBetweenTwoDocBlocksLeavesTheUpperOneAttachedToNothing(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///First paragraph.

///Second paragraph.
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("""
Misplaced doc comment.

In file: fear:/_p/_rank_app.fear

002| ///First paragraph.
   | ^^^^^^^^^^^^^^^^^^^

While inspecting the documentation of package p
This documentation is attached to nothing.
A /// or //> block documents the declaration right below it, and an empty line
ends the block. Delete the empty line below this block, or write /// on it to
keep an empty line in the documentation.""", ex.getMessage());
  }
  @Test void anEmptyDocLineBetweenTwoParagraphsKeepsThemInOneBlock(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
///First paragraph.
///
///Second paragraph.
B:{ .m: A -> A; }
""");
    utils.Err.strCmp("[###]<p>First paragraph.</p>\n<p>Second paragraph.</p>[###]", docs.html());
  }
  @Test void anOrphanBlockSplitByAnEmptyLineIsReportedAsOneBlock(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
B:{
  .m: A -> A;
  ///First note.

  ///Second note.
  }
"""));
    utils.Err.strCmp("""
Misplaced doc comment.

In file: fear:/_p/_rank_app.fear

004|   ///First note.
[###]006|   ///Second note.

While inspecting the documentation of package p
This documentation is attached to nothing.
A /// or //> block documents the declaration right below it, but no declaration
starts right below this block. Move this block right above the declaration it
documents, or turn it into a plain // comment.""", ex.getMessage());
  }
  @Test void aTrailingDocAfterTheBraceClosingItsTypeDocumentsTheMethodStartingOnItsLine(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
B:{
  .k: A -> A;
  .j: A -> A; } /// the j
""");
    utils.Err.strCmp("[###]B\n\t.j:A\n    the j\n\t.k:A[###]", docs.text());
  }
  @Test void aBareSelectorNeverOffersAPrivateTypeOfAnotherPackage(@TempDir Path tmp){
    var q= compiled("q", """
Thing:{ .orValue(a: Thing): Thing -> a; }
_Hidden:{ .orValue(a: _Hidden): _Hidden -> a; }
""", OtherPackages.empty());
    var docs= docs(tmp, OtherPackages.start(Map.of(), q, 0L), """
///Same as `.orValue`.
User:{ .m: User -> this; }
""");
    utils.Err.strCmp("[###]<p>Same as <code><a href=\"q.html#method-q_2e_Thing-0-imm-_2e_orValue-1\">.orValue</a></code>.</p>[###]", docs.html());
  }
  @Test void aLongSignatureMayWrapAfterEachComma(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
B:{ .f[X:imm,Y:imm](a1: A, a2: A): A -> a1; }
""");
    utils.Err.strCmp("[###]<span class=\"sig\">.f[X:imm,<wbr>Y:imm](a1:<a href=\"#type-p_2e_A-0\">A</a>,<wbr>a2:<a href=\"#type-p_2e_A-0\">A</a>):<a href=\"#type-p_2e_A-0\">A</a></span>[###]", docs.html());
  }
  @Test void aBareSelectorReachesAMethodThatAPublicTypeOfAnotherPackageInheritsFromAPrivateOne(@TempDir Path tmp){
    var q= compiled("q", """
A:{}
_Base:{ .reset: A -> A; }
Thing:_Base{}
""", OtherPackages.empty());
    var docs= docs(tmp, OtherPackages.start(Map.of(), q, 0L), """
///Same as `.reset`.
User:{ .m: User -> this; }
""");
    utils.Err.strCmp("[###]<p>Same as <code><a href=\"q.html#method-q_2e_[###]-0-imm-_2e_reset-0\">.reset</a></code>.</p>[###]", docs.html());
  }
  @Test void aBareSelectorDeclaredInThisPackageWinsOverASameNamedMethodOfAnotherPackage(@TempDir Path tmp){
    var q= compiled("q", "Thing:{ .reset(a: Thing): Thing -> a; }\n", OtherPackages.empty());
    var docs= docs(tmp, OtherPackages.start(Map.of(), q, 0L), """
///Call `.reset` first.
User:{ .reset: User -> this; }
""");
    utils.Err.strCmp("[###]<p>Call <code><a href=\"#method-p_2e_User-0-imm-_2e_reset-0\">.reset</a></code> first.</p>[###]", docs.html());
  }
  @Test void aBaseTypeIsShownQualifiedWhereAUseGivesItsSimpleNameToAnotherType(@TempDir Path tmp){
    var base= compiled("base", "Map:{}\n", OtherPackages.empty());
    var q= compiled("q", "Map:{}\n", OtherPackages.empty());
    var docs= docs(tmp, OtherPackages.start(Map.of(), Stream.concat(base.stream(), q.stream()).toList(), 0L), """
use q.Map as Map;
///Holds a `Map`.
User:{ .mine: Map; .theirs: base.Map; }
""");
    utils.Err.strCmp("[###]\t.theirs:base.Map", docs.text());
  }
  @Test void aSingleBacktickThatNothingClosesIsAnError(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Returns ``A`` otherwise .empty` otherwise.
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("""
Broken backticks in a doc comment.

In file: fear:/_p/_rank_app.fear

002| ///Returns ``A`` otherwise .empty` otherwise.
   |                                  ^

While inspecting the documentation of package p
This code span is never closed.[###]""", ex.getMessage());
  }
  @Test void twoBackticksThatNothingClosesAreAnError(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Returns 0 if the parameter is ``
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("""
Broken backticks in a doc comment.

In file: fear:/_p/_rank_app.fear

002| ///Returns 0 if the parameter is ``
   |                                  ^^

While inspecting the documentation of package p
This code span is never closed.[###]""", ex.getMessage());
  }
  @Test void backticksPairedWithTheWrongPartnerLeaveOneThatNothingCloses(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///``x**y for ``x < 0.0``, ``y`` all return ``nan`
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("[###]This code span is never closed.[###]", ex.getMessage());
  }
  @Test void aBacktickInsideACodeSpanOfTwoBackticksIsShownAsText(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
///Shows `` a ` `` and `A`.
B:{ .m: A -> A; }
""");
    utils.Err.strCmp("[###]<p>Shows <code> a ` </code> and <code><a href=\"#type-p_2e_A-0\">A</a></code>.</p>[###]", docs.html());
  }
  @Test void aCodeSpanOfTwoBackticksMayContinueOnTheNextLine(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
///Calls ``this.m
///  .m`` on `A`.
B:{ .m: A -> A; }
""");
    utils.Err.strCmp("[###]<p>Calls <code>this.m\n  .m</code> on <code><a href=\"#type-p_2e_A-0\">A</a></code>.</p>[###]", docs.html());
  }
  @Test void aCodeSpanDoesNotContinueAfterAnEmptyDocLine(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Calls ``this.m
///
///.m`` on A.
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("[###]002| ///Calls ``this.m\n   |          ^^[###]004| ///.m`` on A.\n   |      ^^[###]", ex.getMessage());
  }
  @Test void aReferenceThatContinuesOnTheNextLineIsAnError(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Same as `A
///.m`.
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("""
Broken backticks in a doc comment.

In file: fear:/_p/_rank_app.fear

002| ///Same as `A
003| ///.m`.

While inspecting the documentation of package p
This reference does not end on the line where it starts.[###]""", ex.getMessage());
  }
  @Test void aReferenceOnTheSecondLineOfAParagraphIsReportedOnThatLine(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Calls ``this.m
///  .m`` on `Nope`.
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("""
Broken reference in a doc comment.

In file: fear:/_p/_rank_app.fear

003| ///  .m`` on `Nope`.
   |               ^^^^

While inspecting the documentation of package p
This names no type, no method, and nothing in scope where it is written.[###]""", ex.getMessage());
  }
  @Test void aCodeBlockThatNothingClosesIsAnError(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Usage:
///```
///A.m
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("""
Broken backticks in a doc comment.

In file: fear:/_p/_rank_app.fear

003| ///```
   |    ^^^

While inspecting the documentation of package p
This code block is never closed.[###]""", ex.getMessage());
  }
  @Test void aCodeBlockThatAnExampleLineInterruptsIsNeverClosed(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Shape of a use:
///```
///Foo: { .bar: Foo -> this; }
//>A.m
///Foo.bar
///```
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("""
Broken backticks in a doc comment.

In file: fear:/_p/_rank_app.fear

003| ///```
   |    ^^^

While inspecting the documentation of package p
This code block is never closed.
A doc line starting with ``` opens a code block, and the next such line closes
it, within the same run of /// lines: a //> line, a //- line and the end of the
documentation all end that run. Close this code block with a ``` line before them.""", ex.getMessage());
  }
  @Test void aCodeBlockThatATestOnlyLineInterruptsIsNeverClosed(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///```
///Foo: { .bar: Foo -> this; }
//-A.m
///```
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("""
Broken backticks in a doc comment.

In file: fear:/_p/_rank_app.fear

002| ///```
   |    ^^^
[###]""", ex.getMessage());
  }
  @Test void theFirstParagraphOnASeveralMatchPageLinksAReferenceOnItsSecondLine(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
B:{
  ///Calls ``this
  ///.m`` on `A`.
  .f: A -> A;
  }
C:{ .f: A -> A; }
///See `.f`.
D:{ .m: A -> A; }
""");
    utils.Err.strCmp("[###]<span class=\"opt-doc\">— Calls <code>this .m</code> on <code><a href=\"#type-p_2e_A-0\">A</a></code>.</span>[###]", docs.html());
  }
  @Test void twoAnonymousLiteralsStartingOnOneLineReadDifferentlyInTheListOfTypes(@TempDir Path tmp){
    var html= docs(tmp, a+"""
Both:{ #(a: A, b: A): A -> a; }
Maker:{
  .two: A ->
    ///First.
    Both#(A{ .m -> A }, A{
      ///Second.
      .m -> A
      });
  }
""").html();
    var listed= Pattern.compile("<li><a href=\"#literal-[^>]*>([^<]*)</a>").matcher(html).results().map(m -> m.group(1)).toList();
    assertEquals(listed.stream().distinct().toList(), listed);
  }
  @Test void aDocAboveAnEmptyLineWithNoDeclarationBelowIsNotToldToDeleteTheEmptyLine(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
B:{
  .m: A -> A;
  ///Last note.

  }
"""));
    utils.Err.strCmp("""
Misplaced doc comment.[###]
This documentation is attached to nothing.
A /// or //> block documents the declaration right below it, but no declaration
starts right below this block. Move this block right above the declaration it
documents, or turn it into a plain // comment.""", ex.getMessage());
  }
  @Test void twoOptionsForTheInheritedMethodsOfAnAnonymousLiteralShowTheirReceiverCapabilities(@TempDir Path tmp){
    var html= docs(tmp, a+"""
B:{ read .n: A -> A; mut .n: A -> A; .k: A; }
Maker:{
  .make: B -> {'self
    ///Uses `self.n`.
    .k -> self.n;
    };
  }
""").html();
    utils.Err.strCmp("""
[###]<h2>Several match Anon@_rank_app:4.n</h2>
<ul class="options">
<li><a href="#method-p_2e_B-0-read-_2e_n-0">read B.n()</a></li>
<li><a href="#method-p_2e_B-0-mut-_2e_n-0">mut B.n()</a></li>
</ul>[###]""", html);
  }
  @Test void theWelcomeTextCountsOneTypeAndDoesNotPlaceTheListOfTypesOnTheLeftWhenANarrowWindowShowsItAbove(@TempDir Path tmp){
    var html= docs(tmp, a).html();
    utils.Err.strCmp("[###]@media (max-width:48em){\n  .layout{display:block}[###]", html);
    utils.Err.strCmp("[###]<p id=\"welcome\" class=\"welcome\">Types: 1. Pick one from the list.</p>[###]", html);
  }
  @Test void aReferenceWithAnArityTheMethodDoesNotHaveIsToldTheArityTheMethodHas(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Same as `A.m(_)`.
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("""
Broken reference in a doc comment.

In file: fear:/_p/_rank_app.fear

002| ///Same as `A.m(_)`.
   |             ^^^^^^

While inspecting the documentation of package p
Type "A" has no method ".m(_)".
There is a method "A.m()",
but with a different number of arguments.[###]""", ex.getMessage());
  }
  @Test void aReferenceToAMethodTheTypeDoesNotHaveIsToldTheTypeHasNoSuchMethod(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Same as `A.k`.
B:{ .m: A -> A; }
"""));
    utils.Err.strCmp("""
Broken reference in a doc comment.

In file: fear:/_p/_rank_app.fear

002| ///Same as `A.k`.
   |             ^^^

While inspecting the documentation of package p
Type "A" has no method ".k".[###]""", ex.getMessage());
  }
  @Test void aParameterThatAPatternDestructuresIsShownWithNoNameAndTheOthersKeepTheirNames(@TempDir Path tmp){
    var base= compiled("base", """
Destruct:{ #[A:**,B:**,R:**](a: A, b: B, f: mut MF[A,B,R]): R -> f#(a,b); }
MF[A:**,B:**,R:**]:{ mut #(a: A, b: B): R; }
""", OtherPackages.empty());
    var docs= docs(tmp, OtherPackages.start(Map.of(), base, 0L), a+"""
B:{
  ///Keeps `x`.
  .k(x: A, {.m}: A): A -> x;
  }
""");
    utils.Err.strCmp("[###]B\n\t.k(x:A,A):A\n    Keeps `x`.[###]", docs.text());
  }
  @Test void theParameterThatDoubleColonIntroducesIsShownWithNoName(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
B:{ .k(a: A): A; }
C:B{ .k -> ::.m; }
""");
    utils.Err.strCmp("[###]C : B\n\t.k(A):A\n[###]", docs.text());
  }
  @Test void anAnonymousLiteralWhoseOnlyDocLinesAreTestOnlyIsNotListedButItsTestRuns(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Maker:{
  .make: A -> A{
    //- A.m
    .m -> A;
    };
  }
""");
    utils.Err.strCmp("package p\n\ngroup public\n  A\n  Maker\n\nA\n\t.m:A\n\nMaker\n\t.make:A\n\n", docs.text());
    assertEquals(2, docs.tests().size());
  }
  @Test void aBareSelectorWithAnArityNoMethodHasIsToldTheArityTheMethodHas(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Same as `.m(_)`.
B:{ .k: A -> A; }
"""));
    utils.Err.strCmp("""
Broken reference in a doc comment.[###]
002| ///Same as `.m(_)`.
   |             ^^^^^

While inspecting the documentation of package p
No type has a method ".m(_)".
There is a method "A.m()",
but with a different number of arguments.[###]""", ex.getMessage());
  }
  @Test void aTypeReferenceWithAnArityTheTypeDoesNotHaveIsToldTheArityTheTypeHas(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
///Holds an `A[_]`.
B:{ .k: A -> A; }
"""));
    utils.Err.strCmp("""
Broken reference in a doc comment.[###]
002| ///Holds an `A[_]`.
   |              ^^^^

While inspecting the documentation of package p
There is no type "A[_]".
There is a type "A",
but with a different number of type parameters.[###]""", ex.getMessage());
  }
  @Test void aMethodOnATypeParameterIsToldThatATypeParameterHasNoMethods(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
B:{
  ///Calls `X.m` on its argument.
  .f[X:imm](x: X): A -> A;
  }
"""));
    utils.Err.strCmp("Broken reference in a doc comment.[###]`X.m`[###]\"X\" is a type parameter, and a type parameter has no methods.[###]", ex.getMessage());
  }
  @Test void aReferenceToAMethodOfAnotherPackageWithTwoReceiverCapabilitiesOffersBoth(@TempDir Path tmp){
    var q= compiled("q", "Thing:{ read .n: read Thing -> this; mut .n: mut Thing -> this; }\n", OtherPackages.empty());
    var html= docs(tmp, OtherPackages.start(Map.of(), q, 0L), """
///Same as `q.Thing.n`.
User:{ .m: User -> this; }
""").html();
    utils.Err.strCmp("[###]<li><a href=\"q.html#method-q_2e_Thing-0-read-_2e_n-0\">read q.Thing.n()</a></li>[###]", html);
    utils.Err.strCmp("[###]<li><a href=\"q.html#method-q_2e_Thing-0-mut-_2e_n-0\">mut q.Thing.n()</a></li>[###]", html);
  }
  @Test void anInheritedGenericMethodKeepsTheTypeParameterNameItsSourceWrites(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
P:{ .pipe[R:imm](r: R): R -> r; }
Q:P{ .own[R:imm](r: R): R -> r; }
""");
    utils.Err.strCmp("[###]Q : P\n\t.own[R:imm](r:R):R\n\t.pipe[R:imm](R):R\n    from: P.pipe[###]", docs.text());
  }
  @Test void threeMethodsWithOtherAritiesAreListedWithCommasAndAFinalAnd(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, """
A:{ .m: A -> this; .m(a: A): A -> a; .m(a: A, b: A): A -> a; }
///Same as `A.m(_,_,_)`.
B:{ .k: A -> A; }
"""));
    utils.Err.strCmp("""
[###]Type "A" has no method ".m(_,_,_)".
There are methods "A.m()", "A.m(_)" and "A.m(_,_)",
but with a different number of arguments.[###]""", ex.getMessage());
  }
  @Test void aMethodMissingOnATypeOfAnotherPackageQuotesTheTypeAsTheReferenceWritesIt(@TempDir Path tmp){
    var q= compiled("q", "Thing:{ .n: Thing -> this; }\n", OtherPackages.empty());
    var ex= assertThrows(UserError.class, () -> docs(tmp, OtherPackages.start(Map.of(), q, 0L), """
///Same as `q.Thing.k`.
User:{ .m: User -> this; }
"""));
    utils.Err.strCmp("[###]Type \"q.Thing\" has no method \".k\".[###]", ex.getMessage());
  }
  @Test void anInheritedGenericMethodKeepsAFreshNameWhereTheSourceNameIsATypeParameterOfTheInheritingType(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
P:{ .pipe[R:imm](r: R): R -> r; }
Q[R:imm]:P{ .own(r: R): R -> r; }
""");
    utils.Err.strCmp("[###]\t.pipe[_AR:imm](_AR):_AR\n[###]", docs.text());
  }
  @Test void anInheritedGenericMethodKeepsAFreshNameWhereTheSourceNameIsATypeOfTheInheritingPackage(@TempDir Path tmp){
    var q= compiled("q", "P:{ .pipe[A:imm](a: A): A -> a; }\n", OtherPackages.empty());
    var docs= docs(tmp, OtherPackages.start(Map.of(), q, 0L), a+"""
Q:q.P{ .own: A -> A; }
""");
    utils.Err.strCmp("[###]Q : q.P\n\t.own:A\n\t.pipe[_AA:imm](_AA):_AA\n[###]", docs.text());
  }
  @Test void aSeveralMatchPageShowsTheFirstParagraphOfADocStartingWithACodeBlock(@TempDir Path tmp){
    var html= docs(tmp, a+"""
B:{
  ///```
  ///B.f
  ///```
  ///Gives an A.
  .f: A -> A;
  }
C:{ .f: A -> A; }
///See `.f`.
D:{ .m: A -> A; }
""").html();
    utils.Err.strCmp("[###]<li><a href=\"#method-p_2e_B-0-imm-_2e_f-0\">B.f()</a> <span class=\"opt-doc\">[###] Gives an A.</span></li>[###]", html);
  }
  @Test void aTrailingDocAfterTwoTypesOnOneLineFollowsMoreThanOneDeclaration(@TempDir Path tmp){
    var ex= assertThrows(UserError.class, () -> docs(tmp, a+"""
B:{ .b: A -> A; } C:{ .c: A -> A; } /// about C
"""));
    utils.Err.strCmp("Misplaced doc comment.[###]This documentation follows more than one declaration on the same line.[###]", ex.getMessage());
  }
  @Test void aNamedLiteralAfterAnAnonymousLiteralStartingOnTheSameLineTakesTheDocBlockAboveThatLine(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Both:{ #(a: A, b: A): A -> a; }
Maker:{
  .two: A ->
    ///Doc of Named.
    Both#(A{ .m -> A }, Named: A{ .m -> A });
  }
""");
    utils.Err.strCmp("[###]\nNamed : A\n  Doc of Named.\n\t.m:A\n[###]", docs.text());
  }
  @Test void aTrailingDocAfterALambdaHoldingANamedLiteralDocumentsTheNamedLiteral(@TempDir Path tmp){
    var docs= docs(tmp, a+"""
Mk:{ #: A }
Use:{ .k(f: Mk): A -> f#; }
Maker:{
  .make: A -> Use
    .k {Inner: A{ .m -> A }}; /// doc of Inner
  }
""");
    utils.Err.strCmp("[###]\nInner : A\n  doc of Inner\n\t.m:A\n[###]", docs.text());
  }
}
