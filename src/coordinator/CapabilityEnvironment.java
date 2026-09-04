package coordinator;

import java.util.List;

import realSourceOracle.SourceOracleWithAutoload;

public record CapabilityEnvironment(List<SourceOracleWithAutoload.Triple> autoloadedAssets){}
