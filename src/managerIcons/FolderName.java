package managerIcons;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import realSourceOracle.BuildWithZip;
import realSourceOracle.PathEntry;
import tools.Fs;
import userMessages.UserError;

public final class FolderName{
  private FolderName(){}
  static final String ext= ".fearless";
  public static final String content= "Fearless project: open this file to work on the folder it is in.\n";
  public static String compactName(Path folder){
    var f= folder.toAbsolutePath().normalize();
    var all= fearlessFiles(f);
    return all.size() == 1 ? stem(all.getFirst()) : f.getFileName().toString();
  }
  public static boolean isName(Path folder, String name){
    var kid= candidate(folder,name);
    if (kid.isEmpty() || BuildWithZip.isInvisible(kid.get())){ return false; }
    try { BuildWithZip.checkIndividualVisibleSegment(kid.get()); return true; }
    catch(UserError e){ return false; }
  }
  public static void makeUnique(Path folder, Set<String> taken, UnaryOperator<String> ask){
    if (!taken.contains(compactName(folder))){ return; }
    nameAs(folder, ask.apply(free(folder, folder.getFileName().toString(), taken)));
  }
  public static String free(Path folder, String wanted, Set<String> taken){
    var base= asName(folder, wanted);
    if (isFree(folder,base,taken)){ return base; }
    return IntStream.iterate(2,i->i+1).mapToObj(i->base+i)
      .filter(n->isFree(folder,n,taken))
      .findFirst().orElseThrow();
  }
  public static boolean isFree(Path folder, String name, Set<String> taken){
    return !taken.contains(name) && !hasMarker(folder,name) && !hasIcon(folder,name);
  }
  private static boolean hasMarker(Path folder, String name){ return Files.exists(folder.resolve(name+ext)); }
  private static boolean hasIcon(Path folder, String name){ return Files.isRegularFile(FolderIcon.iconFile(folder,name)); }
  private static void nameAs(Path folder, String name){
    assert isName(folder,name);
    var all= fearlessFiles(folder);
    assert all.size() <= 1;
    var oldName= compactName(folder);
    var target= folder.resolve(name+ext);
    assert !Files.exists(target);
    if (all.isEmpty()){ Fs.writeUtf8(target,content); }
    else { Fs.ofV(()->Files.move(all.getFirst(),target)); }
    moveIcon(folder,oldName,name);
  }
  private static void moveIcon(Path folder, String oldName, String newName){
    if (oldName.equals(newName)){ return; }
    var oldIcon= FolderIcon.iconFile(folder,oldName);
    if (!Files.isRegularFile(oldIcon)){ return; }
    var newIcon= FolderIcon.iconFile(folder,newName);
    assert !Files.exists(newIcon);
    Fs.ofV(()->Files.move(oldIcon,newIcon));
  }
  private static String asName(Path folder, String text){
    var res= text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+","_").replaceAll("_+","_");
    return isName(folder,res) ? res : "p"+res;
  }
  private static Optional<PathEntry> candidate(Path folder, String name){
    try {
      var local= Path.of(name+ext);
      if (local.isAbsolute() || local.getNameCount() != 1){ return Optional.empty(); }
      return Optional.of(new PathEntry(folder.toAbsolutePath().normalize(),local));
    }
    catch(InvalidPathException e){ return Optional.empty(); }
  }
  private static List<Path> fearlessFiles(Path folder){
    if (!Files.isDirectory(folder)){ return List.of(); }
    return Fs.of(()->{ try(var s= Files.list(folder)){ return s
      .filter(Files::isRegularFile)
      .filter(p->p.getFileName().toString().endsWith(ext))
      .sorted()
      .toList();
    }});
  }
  private static String stem(Path file){
    var name= file.getFileName().toString();
    return name.substring(0,name.length()-ext.length());
  }
}
