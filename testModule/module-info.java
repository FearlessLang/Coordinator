module Coordinator {
  requires transitive Commons;
  requires org.junit.jupiter.api;
  requires FearlessFrontend;
  requires java.desktop;
  exports mainCoordinator;
}