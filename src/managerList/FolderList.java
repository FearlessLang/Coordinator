package managerList;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

import managerData.ManagerData;
import managerIcons.BadgeIcon;
import managerIcons.FolderIcon;
import managerIcons.FolderName;
import managerInfo.FolderFacts;

public final class FolderList extends JPanel{
  public enum State{ running, upToDate, needsCompiling;
    BadgeIcon.Mark mark(){ return switch(this){
      case running -> BadgeIcon.Mark.running;
      case upToDate -> BadgeIcon.Mark.none;
      case needsCompiling -> BadgeIcon.Mark.attention;
    };}
    String text(){ return switch(this){
      case running -> "running";
      case upToDate -> "up to date";
      case needsCompiling -> "needs compiling";
    };}
  }
  public record Row(ManagerData.Entry entry, String name, Icon icon, long modified, State state){}
  public enum Sort{
    Name, Modified, Compiled, Run;
    Comparator<Row> comparator(){
      return switch(this){
        case Name -> Comparator.comparing(Row::name,String.CASE_INSENSITIVE_ORDER);
        case Modified -> Comparator.comparingLong(Row::modified).reversed();
        case Compiled -> Comparator.<Row>comparingLong(r->r.entry().compiled()).reversed();
        case Run -> Comparator.<Row>comparingLong(r->r.entry().run()).reversed();
      };
    }
  }
  private static final int iconSize= 48;
  private final ManagerData data;
  private final Consumer<Optional<Path>> onOpen;
  private final Predicate<Path> isRunning;
  private final DefaultListModel<Row> model= new DefaultListModel<>();
  private final JList<Row> list= new JList<>(model);
  private final JComboBox<Sort> sort= new JComboBox<>(Sort.values());
  private final Timer spinner= new Timer(80,_->list.repaint());
  public FolderList(ManagerData data, Predicate<Path> isRunning, Consumer<Optional<Path>> onOpen){
    super(new BorderLayout());
    this.data= data;
    this.isRunning= isRunning;
    this.onOpen= onOpen;
    list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
    list.setVisibleRowCount(-1);
    list.setFixedCellWidth(128);
    list.setFixedCellHeight(88);
    list.setCellRenderer(new Tile());
    list.addMouseListener(new MouseAdapter(){
      @Override public void mouseClicked(MouseEvent e){ open(e.getPoint()); }
    });
    sort.addActionListener(_->refresh());
    var top= new JPanel(new FlowLayout(FlowLayout.LEFT));
    top.add(new JLabel("Order by"));
    top.add(sort);
    add(top,BorderLayout.NORTH);
    add(new JScrollPane(list),BorderLayout.CENTER);
    refresh();
  }
  public void refresh(){
    var rows= data.registered().stream().map(this::row).sorted(selected().comparator()).toList();
    model.clear();
    rows.forEach(model::addElement);
    var anyRunning= rows.stream().anyMatch(r->r.state() == State.running);
    if (anyRunning && !spinner.isRunning()){ spinner.start(); }
    if (!anyRunning && spinner.isRunning()){ spinner.stop(); }
  }
  public void sortBy(Sort order){ sort.setSelectedItem(order); }
  public void select(Path folder){
    for(int i= 0; i < model.size(); i+= 1){
      if (!model.get(i).entry().folder().equals(folder)){ continue; }
      list.setSelectedIndex(i);
      list.ensureIndexIsVisible(i);
      onOpen.accept(Optional.of(folder));
      return;
    }
  }
  private Sort selected(){ return (Sort)sort.getSelectedItem(); }
  private Row row(ManagerData.Entry e){
    var modified= FolderFacts.modified(e.folder());
    var state= stateOf(e.folder(),modified);
    return new Row(e,FolderName.compactName(e.folder()),
      new BadgeIcon(FolderIcon.image(e.folder(),iconSize),iconSize,state.mark()),modified,state);
  }
  private State stateOf(Path folder, long modified){
    if (isRunning.test(folder)){ return State.running; }
    return FolderFacts.cacheUpToDate(folder,modified) ? State.upToDate : State.needsCompiling;
  }
  private void open(Point p){
    var i= list.locationToIndex(p);
    if (i < 0 || !list.getCellBounds(i,i).contains(p)){ list.clearSelection(); onOpen.accept(Optional.empty()); return; }
    onOpen.accept(Optional.of(model.get(i).entry().folder()));
  }
  private static final class Tile extends DefaultListCellRenderer{
    @Override public Component getListCellRendererComponent(JList<?> l, Object value, int i, boolean selected, boolean focus){
      var res= (JLabel)super.getListCellRendererComponent(l,value,i,selected,focus);
      var row= (Row)value;
      res.setText(row.name());
      res.setIcon(row.icon());
      res.setHorizontalAlignment(CENTER);
      res.setHorizontalTextPosition(CENTER);
      res.setVerticalTextPosition(BOTTOM);
      res.setToolTipText(row.entry().folder()+" - "+row.state().text());
      return res;
    }
  }
}
