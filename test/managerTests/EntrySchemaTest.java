package managerTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import managerData.EntrySchema;
import managerData.InfoParser;
import managerData.Kind;
import managerData.ManagerData.Entry;
import userMessages.UserError;

final class EntrySchemaTest{
  private static final URI uri= URI.create("test:projects.info");
  private static final String root= Path.of("").toAbsolutePath().getRoot().toString().replace('\\','/');
  private static List<Entry> entries(String text){ return EntrySchema.fromInfo(text,InfoParser.parse(text,uri)); }
  private static UserError fails(String text){ return assertThrows(UserError.class,()->entries(text)); }
  @Test void anEmptyFileIsNoProjects(){
    assertEquals(List.of(), entries("{}"));
  }
  @Test void aMinimalProjectDefaultsToIdleWithNoMainsReadsOrEdits(){
    var e= entries("{\"someproject\":{\"path\":\""+root+"abs/someproject\"}}").getFirst();
    assertEquals(new Entry("someproject",Path.of(root+"abs/someproject"),Kind.idle,List.of(),Map.of(),Map.of(),-1,-1),e);
  }
  @Test void aFullCodeProjectParsesEveryField(){
    var text= """
      {"someproject":{
        "path":"%sabs/someproject",
        "kind":"code",
        "mains":["some.Main1","some.Main2"],
        "reads":{"publicfiles":["Data1"]},
        "edits":{"publicdata":["Data2","Data3"]}
      }}""".formatted(root);
    var e= entries(text).getFirst();
    assertEquals(new Entry("someproject",Path.of(root+"abs/someproject"),Kind.code,
      List.of("some.Main1","some.Main2"),Map.of("publicfiles",List.of("Data1")),Map.of("publicdata",List.of("Data2","Data3")),-1,-1),e);
  }
  @Test void allFourKindsParse(){
    assertEquals(Kind.idle, entries("{\"a\":{\"path\":\""+root+"a\",\"kind\":\"idle\"}}").getFirst().kind());
    assertEquals(Kind.code, entries("{\"a\":{\"path\":\""+root+"a\",\"kind\":\"code\"}}").getFirst().kind());
    assertEquals(Kind.dataReadOnly, entries("{\"a\":{\"path\":\""+root+"a\",\"kind\":\"data:readOnly\"}}").getFirst().kind());
    assertEquals(Kind.dataReadWrite, entries("{\"a\":{\"path\":\""+root+"a\",\"kind\":\"data:readWrite\"}}").getFirst().kind());
  }
  @Test void theWholeFileMustBeAnObject(){
    assertTrue(fails("\"not an object\"").getMessage().contains("must be an object mapping each project's name"));
  }
  @Test void aProjectNameMustBeShapedLikeAFolderName(){
    assertTrue(fails("{\"Not Valid\":{\"path\":\""+root+"a\"}}").getMessage().contains("is not a valid project name"));
  }
  @Test void anUnknownAttributeIsRejected(){
    assertTrue(fails("{\"a\":{\"path\":\""+root+"a\",\"bogus\":\"1\"}}").getMessage().contains("Unknown project attribute \"bogus\""));
  }
  @Test void aMissingPathIsRejected(){
    assertTrue(fails("{\"a\":{\"kind\":\"idle\"}}").getMessage().contains("missing its \"path\""));
  }
  @Test void aRelativePathIsRejected(){
    assertTrue(fails("{\"a\":{\"path\":\"relative/path\"}}").getMessage().contains("must be an absolute path"));
  }
  @Test void anUnknownKindIsRejectedWithTheFourValidOptionsListed(){
    var msg= fails("{\"a\":{\"path\":\""+root+"a\",\"kind\":\"nonsense\"}}").getMessage();
    assertTrue(msg.contains("\"idle\", \"code\", \"data:readOnly\" or \"data:readWrite\""), msg);
  }
  @Test void repeatedMainsAreRejected(){
    assertTrue(fails("{\"a\":{\"path\":\""+root+"a\",\"mains\":[\"a.Main1\",\"a.Main1\"]}}").getMessage().contains("is repeated in \"mains\""));
  }
  @Test void aLowercaseMainIsNotAValidMainName(){
    assertTrue(fails("{\"a\":{\"path\":\""+root+"a\",\"mains\":[\"lowercase\"]}}").getMessage().contains("not a valid Fearless main name"));
  }
  @Test void mainsAreThePackageQualifiedNamesTheCompilerReports(){
    var e= entries("{\"a\":{\"path\":\""+root+"a\",\"mains\":[\"hello.Hello1\",\"hello.Hello3\"]}}").getFirst();
    assertEquals(List.of("hello.Hello1","hello.Hello3"), e.mains());
  }
  @Test void aMainWithoutAPackageIsRejected(){
    assertTrue(fails("{\"a\":{\"path\":\""+root+"a\",\"mains\":[\"Hello1\"]}}").getMessage().contains("not a valid Fearless main name"));
  }
  @Test void aMainWithAnUppercasePackageIsRejected(){
    assertTrue(fails("{\"a\":{\"path\":\""+root+"a\",\"mains\":[\"Hello.Hello1\"]}}").getMessage().contains("not a valid Fearless main name"));
  }
  @Test void aMainWithALowercaseTypeIsRejected(){
    assertTrue(fails("{\"a\":{\"path\":\""+root+"a\",\"mains\":[\"hello.hello1\"]}}").getMessage().contains("not a valid Fearless main name"));
  }
  @Test void aPackageQualifiedNameIsRejectedInReads(){
    assertTrue(fails("{\"a\":{\"path\":\""+root+"a\",\"reads\":{\"b\":[\"hello.Data1\"]}}}").getMessage().contains("not a valid Fearless type name"));
  }
  @Test void repeatedNamesInsideAReadsTargetAreRejected(){
    assertTrue(fails("{\"a\":{\"path\":\""+root+"a\",\"reads\":{\"b\":[\"Data1\",\"Data1\"]}}}").getMessage().contains("is repeated in \"reads\".\"b\""));
  }
  @Test void aBadlyShapedReadsTargetNameIsRejected(){
    assertTrue(fails("{\"a\":{\"path\":\""+root+"a\",\"reads\":{\"Not Valid\":[]}}}").getMessage().contains("is not a valid project name"));
  }
  @Test void identicalPathsAreRejected(){
    var msg= fails("{\"a\":{\"path\":\""+root+"same\"},\"b\":{\"path\":\""+root+"same\"}}").getMessage();
    assertTrue(msg.contains("\"b\" has the same path as \"a\""), msg);
  }
  @Test void nestedPathsAreRejected(){
    var msg= fails("{\"a\":{\"path\":\""+root+"parent\"},\"b\":{\"path\":\""+root+"parent/child\"}}").getMessage();
    assertTrue(msg.contains("\"b\" has the same path as \"a\""), msg);
  }
  @Test void toInfoThenFromInfoRoundTripsAnEntry(){
    var e= new Entry("someproject",Path.of(root+"abs/someproject"),Kind.code,
      List.of("some.Main1"),Map.of("publicfiles",List.of("Data1")),Map.of(),999,999);
    var text= managerData.InfoPrinter.print(EntrySchema.toInfo(List.of(e)));
    var back= entries(text).getFirst();
    assertEquals("someproject",back.alias());
    assertEquals(Path.of(root+"abs/someproject"),back.path());
    assertEquals(Kind.code,back.kind());
    assertEquals(List.of("some.Main1"),back.mains());
    assertEquals(Map.of("publicfiles",List.of("Data1")),back.reads());
    assertEquals(Map.of(),back.edits());
    assertEquals(-1,back.compiled());
    assertEquals(-1,back.run());
  }
}
