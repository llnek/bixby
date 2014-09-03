skaroHome=@@SKAROHOME@@

ivyRoot=${skaroHome}/.ivyroot
ivyLCacheDir=${ivyRoot}/cache
ivyLRepoDir=${ivyRoot}/repos

buildDir=${basedir}/build.output.folder
reportDir=${buildDir}/reports
podDir=${basedir}/POD-INF

buildVersion=0.0.1-SNAPSHOT
buildDebug=true
buildType=@@APPTYPE@@

ivyLibDir=${basedir}/lib
libDir=${podDir}/lib

testDir=${basedir}/src/test
srcDir=${basedir}/src/main
webDir=${basedir}/src/web

outTestDir=${podDir}/test-classes
outJarDir=${podDir}/classes

csslang=@@WEBCSSLANG@@
jslang=@@WEBLANG@@







##############################################################################
# EOF



