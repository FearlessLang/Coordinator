package managerInfo;

import java.awt.BorderLayout;
import java.awt.Dialog.ModalityType;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import managerData.ManagerData;
import managerIcons.FolderIcon;
import managerIcons.FolderName;
import tools.Fs;
import utils.Join;

public final class FolderInfo{
  private static final DateTimeFormatter when= DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
  private static final int iconSize= 48;
  private final ManagerData data;
  private final Path folder;
  private final Runnable onChange;
  private final JPanel root= new JPanel(new BorderLayout(8,8));
  private Runnable close= ()->{};
  public FolderInfo(ManagerData data, Path folder, Runnable onChange){
    this.data= data;
    this.folder= folder;
    this.onChange= onChange;
    root.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
    rebuild();
  }
  public JPanel panel(){ return root; }
  public void showIn(Window owner){
    var dialog= new JDialog(owner,FolderName.compactName(folder),ModalityType.APPLICATION_MODAL);
    close= dialog::dispose;
    dialog.setContentPane(root);
    dialog.pack();
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }
  private void rebuild(){
    var facts= FolderFacts.of(folder);
    var entry= data.registered().stream().filter(e->e.folder().equals(facts.folder())).findFirst().orElseThrow();
    root.removeAll();
    root.add(header(),BorderLayout.NORTH);
    root.add(details(facts,entry),BorderLayout.CENTER);
    root.add(buttons(facts),BorderLayout.SOUTH);
    root.revalidate();
    root.repaint();
  }
  private JPanel header(){
    var res= new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
    res.add(new JLabel(new ImageIcon(FolderIcon.image(folder,iconSize))));
    var texts= new JPanel();
    texts.setLayout(new BoxLayout(texts,BoxLayout.Y_AXIS));
    var name= new JLabel(FolderName.compactName(folder));
    name.setFont(name.getFont().deriveFont(Font.BOLD,18f));
    texts.add(name);
    texts.add(new JLabel(folder.toString()));
    res.add(texts);
    return res;
  }
  private JScrollPane details(FolderFacts facts, ManagerData.Entry entry){
    var text= new JTextArea(String.join("\n",lines(facts,entry)));
    text.setEditable(false);
    text.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    var res= new JScrollPane(text);
    res.setBorder(BorderFactory.createTitledBorder("This project folder"));
    return res;
  }
  private static List<String> lines(FolderFacts facts, ManagerData.Entry entry){
    return List.of(
      row("Files",facts.files()+""),
      row("Total size",bytes(facts.bytes())),
      row("Last modified",stamp(facts.modified())),
      row("Package data",stamp(facts.jsonStamp())),
      row("Compiled cache",facts.cacheUpToDate() ? "up to date" : "needs compiling"),
      row("Last compile",stamp(entry.compiled())),
      row("Last run",stamp(entry.run())),
      row("Packages",Join.of(facts.pkgs(),""," ","","<none>")),
      row("File names",facts.valid() ? "valid" : "broken"));
  }
  private static String row(String name, String value){ return "%-16s%s".formatted(name,value); }
  private static String stamp(long millis){ return millis < 0 ? "never" : when.format(Instant.ofEpochMilli(millis)); }
  private static String bytes(long size){
    if (size < 1024){ return size+" bytes"; }
    if (size < 1024*1024){ return "%.1f kB".formatted(size/1024.0); }
    return "%.1f MB".formatted(size/(1024.0*1024));
  }
  private JPanel buttons(FolderFacts facts){
    var res= new JPanel(new FlowLayout(FlowLayout.RIGHT));
    res.add(button("Compile",facts.valid() && !facts.cacheUpToDate(),this::compile));
    res.add(button("Run",facts.valid() && facts.cacheUpToDate(),this::run));
    res.add(button("Clear cache",facts.hasCache(),this::clearCache));
    res.add(button("Browse files",true,this::browse));
    res.add(button("Error report",!facts.valid(),()->report(facts)));
    res.add(Box.createHorizontalStrut(16));
    res.add(button("Forget",true,this::forget));
    return res;
  }
  private static JButton button(String text, boolean enabled, Runnable action){
    var res= new JButton(text);
    res.setEnabled(enabled);
    res.addActionListener(_->action.run());
    return res;
  }
  private void compile(){ changed(d->d.setCompiled(folder,System.currentTimeMillis())); }
  private void run(){ changed(d->d.setRun(folder,System.currentTimeMillis())); }
  private void clearCache(){
    Fs.rmTree(folder.resolve(FolderFacts.outDir));
    changed(_->{});
  }
  private void changed(Consumer<ManagerData> action){
    action.accept(data);
    rebuild();
    onChange.run();
  }
  private void forget(){
    data.removeRegisteredFolder(folder);
    onChange.run();
    close.run();
  }
  private void browse(){
    var chooser= new JFileChooser(folder.toFile());
    chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    chooser.setDialogTitle("Files of "+FolderName.compactName(folder));
    chooser.showOpenDialog(root);
  }
  private void report(FolderFacts facts){
    var text= new JTextArea(facts.problem().orElseThrow(),24,90);
    text.setEditable(false);
    text.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    JOptionPane.showMessageDialog(root,new JScrollPane(text),"Names that Fearless cannot accept",JOptionPane.ERROR_MESSAGE);
  }
}
