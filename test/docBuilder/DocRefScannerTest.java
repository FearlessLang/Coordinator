package docBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

final class DocRefScannerTest{

  private static Optional<DocRef> only(String text){
    var spans= DocRefScanner.refSpans(text);
    assertEquals(1, spans.size(), "expected exactly one single-backtick span in: "+text);
    return DocRefScanner.wholeRef(text, spans.getFirst());
  }

  @Test void aBareTypeNameIsAReference(){
    assertEquals(Optional.of(new DocRef.TypeName(Optional.empty(),"Foo",OptionalInt.empty())), only("see `Foo` here"));
  }

  @Test void aPackageQualifiedTypeNameIsAReference(){
    assertEquals(Optional.of(new DocRef.TypeName(Optional.of("base"),"Foo",OptionalInt.empty())), only("`base.Foo`"));
  }

  @Test void anExplicitTypeArityNarrowsTheReference(){
    assertEquals(Optional.of(new DocRef.TypeName(Optional.empty(),"Foo",OptionalInt.of(2))), only("`Foo[_,_]`"));
  }

  @Test void aBareSelectorIsAReference(){
    assertEquals(Optional.of(new DocRef.MethodName(Optional.empty(),".foo",OptionalInt.empty())), only("`.foo`"));
  }

  @Test void aSelectorArityNarrowsTheReference(){
    assertEquals(Optional.of(new DocRef.MethodName(Optional.empty(),".foo",OptionalInt.of(2))), only("`.foo(_,_)`"));
  }

  @Test void aZeroArityMarkerIsDistinctFromNoMarkerAtAll(){
    assertEquals(OptionalInt.of(0), ((DocRef.MethodName)only("`.foo()`").get()).arity());
  }

  @Test void aQualifiedMethodCarriesBothItsOwnerAndSelector(){
    var ref= (DocRef.MethodName)only("`Foo.bar`").get();
    assertEquals(Optional.of(new DocRef.TypeName(Optional.empty(),"Foo",OptionalInt.empty())), ref.owner());
    assertEquals(".bar", ref.selector());
  }

  @Test void aQualifiedMethodCanNarrowBothTheOwnerArityAndItsOwnArity(){
    var ref= (DocRef.MethodName)only("`Foo[_].bar(_,_)`").get();
    assertEquals(OptionalInt.of(1), ((DocRef.TypeName)ref.owner().get()).arity());
    assertEquals(OptionalInt.of(2), ref.arity());
  }

  //an operator method is a method like any other: "Foo++(_,_)" is the two argument
  //++ of Foo, exactly as "Foo.foo(_,_)" is its two argument .foo
  @Test void anOperatorMethodOfATypeIsAReference(){
    var ref= (DocRef.MethodName)only("`Foo++(_,_)`").get();
    assertEquals(Optional.of(new DocRef.TypeName(Optional.empty(),"Foo",OptionalInt.empty())), ref.owner());
    assertEquals("++", ref.selector());
    assertEquals(OptionalInt.of(2), ref.arity());
  }

  @Test void aBareOperatorIsAReference(){
    assertEquals(Optional.of(new DocRef.MethodName(Optional.empty(),">>=+",OptionalInt.empty())), only("`>>=+`"));
  }

  @Test void anOperatorOnAReceiverOrAPackageQualifiedTypeIsAReference(){
    assertEquals(Optional.of(new DocRef.LocalName("this")), ((DocRef.MethodName)only("`this++`").get()).owner());
    var q= (DocRef.MethodName)only("`base.Foo++`").get();
    assertEquals(Optional.of(new DocRef.TypeName(Optional.of("base"),"Foo",OptionalInt.empty())), q.owner());
    assertEquals("++", q.selector());
  }

  @Test void anOperatorAfterATypeArityStillReadsAsTheSelector(){
    var ref= (DocRef.MethodName)only("`Foo[_,_]++`").get();
    assertEquals(OptionalInt.of(2), ((DocRef.TypeName)ref.owner().get()).arity());
    assertEquals("++", ref.selector());
  }

  @Test void aCharacterRunThatIsNotAMethodNameIsNotAReference(){
    assertTrue(only("`Foo//`").isEmpty(), "// cannot be a method name");
  }

  //the whole span is the reference: a span that merely contains a name is code, and
  //the author is expected to mark it as such with two backticks.
  @Test void aCallExpressionIsNotAReferenceEvenThoughItContainsAMethodName(){
    assertTrue(only("`this.aluOr(x)`").isEmpty());
  }

  @Test void aReceiverIsAReferenceOnItsOwn(){
    assertEquals(Optional.of(new DocRef.LocalName("this")), only("`this`"));
    assertEquals(Optional.of(new DocRef.LocalName("x")), only("`x`"));
  }

  @Test void aReceiverQualifiedSelectorIsAReference(){
    var ref= (DocRef.MethodName)only("`this.void`").get();
    assertEquals(Optional.of(new DocRef.LocalName("this")), ref.owner());
    assertEquals(".void", ref.selector());
  }

  @Test void aReceiverQualifiedSelectorCanNarrowItsArity(){
    assertEquals(OptionalInt.of(1), ((DocRef.MethodName)only("`x.foo(_)`").get()).arity());
  }

  @Test void anOperatorExpressionIsNotAReference(){
    assertTrue(only("`this ** x`").isEmpty());
  }

  @Test void aRealTypeArgumentListIsNotAReferenceBecauseItIsNotAnArityMarker(){
    assertTrue(only("`Foo[Int]`").isEmpty());
  }

  @Test void aTypeNameFollowedByTrailingTextIsNotAReference(){
    assertTrue(only("`Foo and more`").isEmpty());
  }

  @Test void unbacktickedTextIsNeverARefSpan(){
    assertTrue(DocRefScanner.refSpans("Foo .foo Foo.bar").isEmpty());
  }

  @Test void aDoubleBacktickSpanIsCodeToShowAndIsNeverAReference(){
    assertTrue(DocRefScanner.refSpans("see ``Foo`` and ``this.foo(x)``").isEmpty());
  }

  @Test void codeSpansStillReportsTheFenceLengthOfAWideFence(){
    var spans= DocRefScanner.codeSpans("`` `Foo`.u ``");
    assertEquals(1, spans.size());
    assertEquals(2, spans.getFirst().fence());
  }

  @Test void twoSingleBacktickSpansOnTheSameLineAreBothReferences(){
    var text= "`Foo` and `Bar`";
    var spans= DocRefScanner.refSpans(text);
    assertEquals(2, spans.size());
    assertEquals(
      List.of(new DocRef.TypeName(Optional.empty(),"Foo",OptionalInt.empty()),
              new DocRef.TypeName(Optional.empty(),"Bar",OptionalInt.empty())),
      spans.stream().map(sp->DocRefScanner.wholeRef(text,sp).get()).toList());
  }

  //a signature is generated code, so every type in it is offered and nothing in it
  //can be an error.
  @Test void aSignatureOffersEveryTypeNameItContains(){
    var names= DocRefScanner.signatureTypes("read .foo(read Bar):Baz").stream()
      .map(f->((DocRef.TypeName)f.ref()).simpleName()).toList();
    assertEquals(List.of("Bar","Baz"), names);
  }

  @Test void aSignatureDoesNotSeeATypeInsideALongerWord(){
    assertTrue(DocRefScanner.signatureTypes("read .ieeeEq(x)").stream()
      .noneMatch(f->((DocRef.TypeName)f.ref()).simpleName().equals("Eq")));
  }
}
