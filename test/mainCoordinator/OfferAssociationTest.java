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
  private final List<Main.Held> refusals= new ArrayList<>();
  private final List<Boolean> answers= new ArrayList<>();
  private void hold(){
    try{
      Main.hold(taken -> {
        reconciled.add(taken);
        if (!refusals.isEmpty()){ throw refusals.removeFirst(); }
      }, h -> { asked.add(Violation.associationQuestion(h, e -> "", "ends Fearless")); return answers.removeFirst(); }, new ArrayList<>());
    }
    catch (Main.Held h){ throw h.error(e -> ""); }
  }
  @Test void fooHoldingFearlessMakesThePortableAskOnceAndAnAcceptedQuestionTakesFearless(){
    refusals.add(new Main.Held(Map.of(".fearless", "Foo"), true));
    answers.add(true);
    hold();
    assertEquals(List.of(List.of(), List.of(".fearless")), reconciled);
    assertEquals(List.of(question), asked);
  }
  @Test void aRejectedQuestionEndsThePortableWithAnErrorNamingFearlessAndFoo(){
    refusals.add(new Main.Held(Map.of(".fearless", "Foo"), true));
    answers.add(false);
    var error= assertThrows(UserError.class, this::hold);
    assertEquals("""
Fearless needs the desktop to open the files with the extensions listed below with Fearless.
The desktop opens those files with other programs:
  ".fearless" is opened with "Foo\"""", error.getMessage());
    assertEquals(List.of(List.of()), reconciled);
    assertEquals(List.of(question), asked);
  }
  @Test void fearlessThatCanNotBeTakenAfterTheQuestionEndsThePortableWithAnErrorNamingWhatStopsIt(){
    refusals.add(new Main.Held(Map.of(".fearless", "Foo"), true));
    refusals.add(new Main.Held(Map.of(".fearless", "/home/me/.config/mimeapps.list"), false));
    answers.add(true);
    var error= assertThrows(UserError.class, this::hold);
    assertEquals("""
Fearless needs the desktop to open the files with the extensions listed below with Fearless.
The desktop does not let Fearless change the program opening those files:
  ".fearless" needs a change of
    /home/me/.config/mimeapps.list""", error.getMessage());
    assertEquals(List.of(List.of(), List.of(".fearless")), reconciled);
    assertEquals(List.of(question), asked);
  }
  @Test void aRefusalForAnotherExtensionAfterAnAcceptedQuestionAsksAboutThatExtensionOnly(){
    refusals.add(new Main.Held(Map.of(".fearless", "Foo"), true));
    refusals.add(new Main.Held(Map.of(".ffile001", "Bar"), true));
    answers.addAll(List.of(true, true));
    hold();
    assertEquals(List.of(List.of(), List.of(".fearless"), List.of(".fearless", ".ffile001")), reconciled);
    assertEquals(List.of(question, question.replace("\".fearless\" is opened with \"Foo\"", "\".ffile001\" is opened with \"Bar\"")), asked);
  }
}
