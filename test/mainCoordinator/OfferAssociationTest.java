package mainCoordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import userMessages.UserError;
import userMessages.Violation;

final class OfferAssociationTest{
  private static final String question= """
The desktop opens the files with the extensions listed below with other programs.
Fearless needs the desktop to open those files with Fearless:
  ".fearless" is opened with "Foo"
Let the desktop open the files with these extensions with Fearless?
Answering No ends Fearless.""";
  private final List<String> asked= new ArrayList<>();
  private final List<List<String>> reconciled= new ArrayList<>();
  private void hold(boolean userAccepts){
    Main.hold(taken -> {
      reconciled.add(taken);
      if (taken.isEmpty() || !userAccepts){ throw new Main.Held(Map.of(".fearless", "Foo")); }
    }, h -> { asked.add(Violation.associationQuestion(h, e -> "", "ends Fearless")); return userAccepts; }, h -> Violation.associationNotOurs(h, e -> ""));
  }
  @Test void fooHoldingFearlessMakesThePortableAskOnceAndAnAcceptedQuestionTakesFearless(){
    hold(true);
    assertEquals(List.of(List.of(), List.of(".fearless")), reconciled);
    assertEquals(List.of(question), asked);
  }
  @Test void aRejectedQuestionEndsThePortableWithAnErrorNamingFearlessAndFoo(){
    var error= assertThrows(UserError.class, () -> hold(false));
    assertEquals("""
Fearless needs the desktop to open the files with the extensions listed below with Fearless.
The desktop opens those files with other programs:
  ".fearless" is opened with "Foo\"""", error.getMessage());
    assertEquals(List.of(List.of()), reconciled);
    assertEquals(List.of(question), asked);
  }
}
