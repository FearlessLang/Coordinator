package managerInfo;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import managerData.ManagerData;
import managerIcons.FolderIcon;
import managerIcons.FolderName;
import managerRun.ProjectSession;
import tools.Fs;
import utils.Join;

public final class FolderInfo{
  private static final DateTimeFormatter when= DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
  private static final int iconSize= 48;
  private final ManagerData data;
  private final Path folder;
  private final Runnable onChange;
  private final ProjectSession session;
  private final JPanel root= new JPanel(new BorderLayout(8,8));
  private final JTextArea output= new JTextArea(10,60);
  private final JScrollPane outputScroll= new JScrollPane(output);
  private final JTextArea details= new JTextArea(9,40);
  private final JPanel mainsBox= new JPanel();
  private final JScrollPane mainsScroll= new JScrollPane(mainsBox);
  private final JPanel tools= new JPanel(new FlowLayout(FlowLayout.LEFT));
  private final List<JCheckBox> boxes= new ArrayList<>();
  private FolderFacts facts;
  public FolderInfo(ManagerData data, Path folder, Executor worker, Runnable onChange){
    this.data= data;
    this.folder= folder;
    this.onChange= onChange;
    this.session= new ProjectSession(folder, worker, this::append, this::refreshLater);
    this.facts= FolderFacts.of(folder);
    output.setEditable(false);
    output.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    details.setEditable(false);
    details.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    mainsBox.setLayout(new BoxLayout(mainsBox,BoxLayout.Y_AXIS));
    outputScroll.setBorder(BorderFactory.createTitledBorder("Output"));
    root.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
    root.add(top(),BorderLayout.NORTH);
    root.add(outputScroll,BorderLayout.CENTER);
    session.refresh();
    refresh();
  }
  public JPanel panel(){ return root; }
  public Path folder(){ return folder; }
  public ProjectSession session(){ return session; }
  public FolderFacts facts(){ return facts; }
  private JPanel top(){
    var res= new JPanel();
    res.setLayout(new BoxLayout(res,BoxLayout.Y_AXIS));
    res.add(header());
    res.add(tools);
    res.add(new Collapsible("Information",new JScrollPane(details),true));
    res.add(new Collapsible("Mains to run",mainsScroll,true,small("All",()->setAll(true)),small("None",()->setAll(false))));
    return res;
  }
  private static JButton small(String text, Runnable action){
    var res= button(text,true,action);
    res.setMargin(new Insets(0,6,0,6));
    return res;
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
  private void append(String text){
    SwingUtilities.invokeLater(()->{
      var bar= outputScroll.getVerticalScrollBar();
      var following= bar.getValue()+bar.getVisibleAmount() >= bar.getMaximum()-16;
      output.append(text);
      if (following){ output.setCaretPosition(output.getDocument().getLength()); }
    });
  }
  private void refreshLater(){ SwingUtilities.invokeLater(()->{ refresh(); onChange.run(); }); }
  public String status(){
    if (!session.busy()){ return ""; }
    return session.current()+"   "+format(session.elapsed().toSeconds());
  }
  private static String format(long seconds){
    return "%02d:%02d:%02d".formatted(seconds/3600, (seconds/60)%60, seconds%60);
  }
  private void refresh(){
    facts= FolderFacts.of(folder);
    var entry= data.registered().stream().filter(e->e.folder().equals(facts.folder())).findFirst().orElseThrow();
    details.setText(String.join("\n",lines(facts,entry)));
    details.setCaretPosition(0);
    fillMains();
    fillTools();
    root.revalidate();
    root.repaint();
  }
  private void setAll(boolean on){ boxes.forEach(b->b.setSelected(on)); }
  private void fillMains(){
    var known= session.mains();
    var chosen= selected();
    mainsBox.removeAll();
    boxes.clear();
    if (known.isEmpty()){
      mainsBox.add(new JLabel("<needs compiling>"));
      return;
    }
    for(var main: known.get()){
      var box= new JCheckBox(main, chosen.contains(main));
      box.setEnabled(!session.busy());
      boxes.add(box);
      mainsBox.add(box);
    }
    mainsScroll.setPreferredSize(new Dimension(0,Math.min(6,boxes.size())*26+8));
  }
  private List<String> selected(){
    return boxes.stream().filter(JCheckBox::isSelected).map(JCheckBox::getText).toList();
  }
  private void fillTools(){
    var busy= session.busy();
    tools.removeAll();
    tools.add(button("Compile",facts.valid() && !busy,this::compile));
    tools.add(button("Run selected",session.mains().isPresent() && !busy,this::run));
    tools.add(button("Terminate",session.running().isPresent(),session::terminate));
    tools.add(button("Clear output",true,this::clearOutput));
  }
  private static JButton button(String text, boolean enabled, Runnable action){
    var res= new JButton(text);
    res.setEnabled(enabled);
    res.addActionListener(_->action.run());
    return res;
  }
  private void clearOutput(){ output.setText(""); }
  private void compile(){
    changed(d->d.setCompiled(folder,System.currentTimeMillis()));
    session.compile();
  }
  private void run(){
    var chosen= selected();
    changed(d->d.setRun(folder,System.currentTimeMillis()));
    session.run(chosen);
  }
  public void clearCache(){
    Fs.rmTree(folder.resolve(FolderFacts.outDir));
    session.refresh();
    changed(_->{});
  }
  private void changed(Consumer<ManagerData> action){
    action.accept(data);
    refresh();
    onChange.run();
  }
  public void forget(){
    data.removeRegisteredFolder(folder);
    onChange.run();
  }
  public void browse(){
    var chooser= new JFileChooser(folder.toFile());
    chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    chooser.setDialogTitle("Files of "+FolderName.compactName(folder));
    chooser.showOpenDialog(root);
  }
  public void report(){
    var text= new JTextArea(facts.problem().orElseThrow(),24,90);
    text.setEditable(false);
    text.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));
    JOptionPane.showMessageDialog(root,new JScrollPane(text),"Names that Fearless cannot accept",JOptionPane.ERROR_MESSAGE);
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
}
