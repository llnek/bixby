old__gantBuildDir=./alchemy.dir/${gantProjectName}

cljDocJar=./artifacts/lib/autodoc-0.9.0.jar
bldDir=alchemy.dir
prj=_
cljBuildDir=./${bldDir}/clojure.org
gantBuildDir=./${bldDir}/${prj}

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





