module Coordinator {
  requires transitive Commons;
  requires FearlessFrontend;
  requires java.desktop;
  requires java.sql;
  requires org.xerial.sqlitejdbc;
  exports mainCoordinator;
}