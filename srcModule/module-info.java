module Coordinator {
  requires transitive Commons;
  requires FearlessFrontend;
  requires java.desktop;
  exports mainCoordinator;
  exports manager;
  exports managerRun;
}