package coordinator;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import resources.ResolveResource;
import tools.Fs;
import utils.Join;

final class RuntimeImageTest{
  static{ utils.Err.setUp(AssertionFailedError.class, Assertions::assertEquals, Assertions::assertTrue); }
  static final Pattern requires= Pattern.compile("requires (?:transitive )?([\\w.]+);");
  static final Pattern imports= Pattern.compile("(?m)^import (?:static )?([\\w.]+);");
  static final Map<String,String> moduleOf= ModuleFinder.ofSystem().findAll().stream()
    .map(ModuleReference::descriptor)
    .flatMap(d -> d.packages().stream().map(p -> Map.entry(p, d.name())))
    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  static Stream<String> requiredPlatform(Path moduleInfo){
    return requires.matcher(Fs.readUtf8(moduleInfo)).results().map(r -> r.group(1)).filter(n -> ModuleFinder.ofSystem().find(n).isPresent());
  }
  static Set<String> image(Path... moduleInfos){
    var roots= Stream.of(moduleInfos).flatMap(RuntimeImageTest::requiredPlatform).collect(Collectors.toUnmodifiableSet());
    return Configuration.empty().resolve(ModuleFinder.ofSystem(), ModuleFinder.of(), roots).modules().stream().map(ResolvedModule::name).collect(Collectors.toUnmodifiableSet());
  }
  static Optional<String> module(String imported){
    for (var n= imported; n.indexOf('.') >= 0; n = n.substring(0, n.lastIndexOf('.'))){
      var m= moduleOf.get(n);
      if (m != null){ return Optional.of(m); }
    }
    return Optional.empty();
  }
  static String missing(Set<String> image){
    var rt= Fs.walk(ResolveResource.stLibRTPath, s -> s.filter(p -> p.toString().endsWith(".java")).toList());
    var needed= rt.stream()
      .flatMap(f -> imports.matcher(Fs.readUtf8(f)).results().map(r -> r.group(1)))
      .flatMap(i -> module(i).stream())
      .filter(m -> !image.contains(m))
      .distinct().sorted();
    return Join.of(needed, "", "\n", "", "");
  }
  @Test void thePortableImageHasEveryModuleTheRuntimeImports(){
    utils.Err.strCmp("", missing(image(
      ResolveResource.commonsSrc.resolve("module-info.java"),
      ResolveResource.frontendSrcModule.resolve("module-info.java"),
      ResolveResource.coordinatorSrcModule.resolve("module-info.java"))));
  }
  @Test void theManagedImageHasEveryModuleTheRuntimeImports(){
    utils.Err.strCmp("", missing(image(
      ResolveResource.commonsSrc.resolve("module-info.java"),
      ResolveResource.frontendSrcModule.resolve("module-info.java"),
      ResolveResource.coordinatorSrcModule.resolve("module-info.java"),
      ResolveResource.controllerSrcModule.resolve("module-info.java"))));
  }
}
