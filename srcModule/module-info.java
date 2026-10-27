module Coordinator{
  requires transitive Commons;
  requires transitive FearlessFrontend;
  requires transitive java.desktop;
  requires java.management;
  requires java.net.http;
  exports mainCoordinator;
  exports coordinator;
  exports docBuilder;
  exports fileSupport;
  exports naiveBackend;
  exports realSourceOracle;
  exports userMessages;
}
