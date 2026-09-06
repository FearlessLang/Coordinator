module Coordinator {
  requires transitive Commons;
  requires org.junit.jupiter.api;
  requires FearlessFrontend;
  requires java.desktop;
  requires jdk.httpserver;
  exports mainCoordinator;
  exports manager;
}