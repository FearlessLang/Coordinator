package mainCoordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import userMessages.UserError;
import userMessages.Violation;

final class OfferAssociationTest{
  private static final String heldByFoo= Violation.associationNotOurs(Map.of(".fearless", "Foo"));
  private final List<String> asked= new ArrayList<>();
  private final List<List<String>> reconciled= new ArrayList<>();
  private final List<Main.Refusal> refusals= new ArrayList<>();
  private final List<Main.Answer> answers= new ArrayList<>();
  private static Main.Refusal notOurs(String ext, String holder){ return new Main.Refusal(Violation.associationNotOurs(Map.of(ext, holder)), Violation.tickToHold, List.of(ext)); }
  private static Main.Answer taken(String... exts){ return new Main.Answer(List.of(exts), List.of()); }
  private static Main.Answer givenUp(String... exts){ return new Main.Answer(List.of(), List.of(exts)); }
  private void hold(){
    Main.hold(taken -> {
      reconciled.add(taken);
      return refusals.isEmpty() ? Optional.empty() : Optional.of(refusals.removeFirst());
    }, r -> { asked.add(r.getMessage()); return answers.removeFirst(); }, (r, _) -> { throw r.error(); });
  }
  @Test void fooHoldingFearlessMakesThePortableAskOnceAndAnAcceptedQuestionTakesFearless(){
    refusals.add(notOurs(".fearless", "Foo"));
    answers.add(taken(".fearless"));
    hold();
    assertEquals(List.of(List.of(), List.of(".fearless")), reconciled);
    assertEquals(List.of(heldByFoo), asked);
  }
  @Test void aRejectedQuestionEndsThePortableWithAnErrorNamingFearlessAndFoo(){
    refusals.add(notOurs(".fearless", "Foo"));
    answers.add(givenUp(".fearless"));
    var error= assertThrows(UserError.class, this::hold);
    assertEquals("""
Fearless needs the desktop to open the files with the extensions listed below with Fearless.
The desktop opens those files with other programs:
  ".fearless" is opened with "Foo\"""", error.getMessage());
    assertEquals(List.of(List.of()), reconciled);
    assertEquals(List.of(heldByFoo), asked);
  }
  @Test void fearlessThatCanNotBeTakenAfterTheQuestionAndIsGivenUpEndsThePortableWithAnErrorNamingWhatStopsIt(){
    refusals.add(notOurs(".fearless", "Foo"));
    var blocked= Violation.associationNotTaken(Map.of(".fearless", "/home/me/.config/mimeapps.list"));
    refusals.add(new Main.Refusal(blocked, Violation.tickToTake, List.of(".fearless")));
    answers.addAll(List.of(taken(".fearless"), givenUp(".fearless")));
    var error= assertThrows(UserError.class, this::hold);
    assertEquals("""
Fearless needs the desktop to open the files with the extensions listed below with Fearless.
The desktop does not let Fearless change the program opening those files:
  ".fearless" needs a change of
    /home/me/.config/mimeapps.list""", error.getMessage());
    assertEquals(List.of(List.of(), List.of(".fearless")), reconciled);
    assertEquals(List.of(heldByFoo, blocked), asked);
  }
  @Test void aRefusalForAnotherExtensionAfterAnAcceptedQuestionAsksAboutThatExtensionAndKeepsTakingTheFirst(){
    refusals.add(notOurs(".fearless", "Foo"));
    refusals.add(notOurs(".ffile001", "Bar"));
    answers.addAll(List.of(taken(".fearless"), taken(".ffile001")));
    hold();
    assertEquals(List.of(List.of(), List.of(".fearless"), List.of(".fearless", ".ffile001")), reconciled);
    assertEquals(List.of(heldByFoo, Violation.associationNotOurs(Map.of(".ffile001", "Bar"))), asked);
  }
  @Test void aRefusalWithNothingGivenUpIsAskedAgainAndARefusalStillComingAfterTheLastAskIsTheError(){
    var ambiguous= Violation.associationsAmbiguous("fearlessBin0_001\nfearlessManaged0_002");
    refusals.addAll(Collections.nCopies(Main.asks + 1, new Main.Refusal(ambiguous, Violation.removeAllButOne, List.of())));
    answers.addAll(Collections.nCopies(Main.asks, givenUp()));
    var error= assertThrows(UserError.class, this::hold);
    assertEquals(ambiguous, error.getMessage());
    assertEquals(Collections.nCopies(Main.asks + 1, List.of()), reconciled);
    assertEquals(Collections.nCopies(Main.asks, ambiguous), asked);
  }
}
