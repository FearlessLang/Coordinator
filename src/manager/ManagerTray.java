package manager;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;

import javax.swing.SwingUtilities;

//- no tray support at all (some Linux desktops) is NOT an error;
//- a failure while adding the icon is NOT an error either.
//Resource policy (see ManagerMain): the icon lives for the whole
//process life and is removed from the tray by process death. Quit from the
//tray menu goes through the same Stop as the window button, so there is
//exactly one termination path.
final class ManagerTray {
  private final TrayIcon icon;
  static void install(ManagerGui gui, Runnable onQuit){
    SwingUtilities.invokeLater(() -> tryInstall(gui,onQuit));//SystemTray and TrayIcon are AWT state: mutate them only on the event thread
  }
  private static void tryInstall(ManagerGui gui, Runnable onQuit){
    if (!SystemTray.isSupported()){ return; }
    try { new ManagerTray(gui,onQuit).addTo(SystemTray.getSystemTray()); }
    catch(AWTException|UnsupportedOperationException|SecurityException e){}//see the tray policy above
  }
  private ManagerTray(ManagerGui gui, Runnable onQuit){
    var show= new MenuItem("Show manager");
    show.addActionListener(_ -> gui.showManager());
    var quit= new MenuItem("Quit manager");
    quit.addActionListener(_ -> onQuit.run());
    var menu= new PopupMenu();
    menu.add(show);
    menu.addSeparator();
    menu.add(quit);
    icon= new TrayIcon(FearlessIcon.image(), "Fearless Manager", menu);
    icon.setImageAutoSize(true);//we hand over a large image; the tray scales it down to its own size (crisper than upscaling a small one)
    icon.addActionListener(_ -> gui.showManager());//double click on windows and most linux; mac opens the menu instead, which has Show manager
  }
  private void addTo(SystemTray tray) throws AWTException { tray.add(icon); }
}
