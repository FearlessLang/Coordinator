module Coordinator {
  requires transitive Commons;
  requires transitive FearlessFrontend;
  requires transitive java.desktop;
  exports mainCoordinator;
  exports coordinator;
  exports docBuilder;
  exports fileSupport;
  exports naiveBackend;
  exports realSourceOracle;
  exports userMessages;
}