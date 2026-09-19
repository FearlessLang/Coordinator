This folder holds jars needed only to compile and run the test suite -
never bundled into a deployed Fearless application. externalJars, next to
this folder, is the opposite: only what a running Fearless program needs,
which is why these two must stay separate. For now, the list is:

## JUnit 6 (jupiter-api, platform-commons, platform-console-standalone) and its
## transitive dependencies (opentest4j, apiguardian-api, jspecify)
To find the latest version of each, open:
https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter-api/maven-metadata.xml
(and the equivalent path for the other org.junit.platform/org.opentest4j/
org.apiguardian/org.jspecify artifacts) and search for
<versioning>...<release>????</release>

## JMH 1.37 (jmh-core, jmh-generator-annprocess) and its transitive
## dependencies (jopt-simple, commons-math3)
Used only by Controllers/benchmarks, built and run by
Coordinator/Build/src/scripts/RunBenchmarks.java. To find the latest version:
https://repo.maven.apache.org/maven2/org/openjdk/jmh/jmh-core/maven-metadata.xml
