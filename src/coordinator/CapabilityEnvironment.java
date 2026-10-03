package coordinator;

import java.util.Map;

import realSourceOracle.SourceOracleWithAutoload;

public record CapabilityEnvironment(Map<String,SourceOracleWithAutoload.Triple> autoloadedAssets){}
