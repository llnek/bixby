gantBuildDir=/wdrive/dev/builds/${gantProjectName}
scalaLibDir=/wdrive/opt/lang/scala/lib
clojureDir=/wdrive/opt/lang/clojure/lib
tpclDir=/wdrive/localrepo/tpcl/lib
ivyLibDir=${gantBuildDir}/lib

ivyRoot=/wdrive/.ivy
ivyLCacheDir=${ivyRoot}/cache
ivyLRepoDir=${ivyRoot}/repos

buildVersion=0.9.0-SNAPSHOT
buildDebug=true

distribDir=${gantBuildDir}/distrib
libDir=${gantBuildDir}/lib
buildDir=${gantBuildDir}/build
qaDir=${gantBuildDir}/test
packDir=${gantBuildDir}/pack

testDir=${basedir}/src/test
srcDir=${basedir}/src/main

reportTestDir=${qaDir}/reports
buildTestDir=${qaDir}/classes





