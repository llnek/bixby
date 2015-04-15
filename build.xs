gantBuildDir=./alchemy.dir/${gantProjectName}
cljBuildDir=./alchemy.dir/clojure.org

ivyLibDir=${gantBuildDir}/lib
ivyRoot=/wdrive/.ivy

ivyLCacheDir=${ivyRoot}/cache
ivyLRepoDir=${ivyRoot}/repos

buildVersion=0.9.0-SNAPSHOT
buildDebug=true

distribDir=${gantBuildDir}/distrib
buildDir=${gantBuildDir}/build

libDir=${gantBuildDir}/lib
qaDir=${gantBuildDir}/test

testDir=${basedir}/src/test
srcDir=${basedir}/src/main

packDir=${gantBuildDir}/pack

reportTestDir=${qaDir}/reports
buildTestDir=${qaDir}/classes





