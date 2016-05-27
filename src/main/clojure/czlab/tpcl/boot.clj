;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.tpcl.boot

  (:require
    [boot.task.built-in
     :refer [install
             pom
             target
             uber aot]]
    [czlab.xlib.logging :as log]
    [clojure.data.json :as js]
    [cemerick.pomegranate :as pom]
    [clojure.java.io :as io]
    [boot.core :as bc :refer :all]
    [clojure.string :as cs]
    [czlab.tpcl.antlib :as a])

  (:import
    [java.util Stack]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defonce ^:private D-VARS (atom {}))
(defonce ^:private U-VARS (atom {}))
(defonce ^:private LATCH (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn se! ""

  [k v]

  (swap! D-VARS assoc k v))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getDVars "" [] @D-VARS)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn replaceFile ""

  [file work]

  {:pre [(fn? work)]}

  (spit file
        (-> (slurp file :encoding "utf-8")
              (work))
        :encoding "utf-8"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- grep-paths ""

  [top bin fext]

  (doseq [f (.listFiles (io/file top))]
    (cond
      (.isDirectory f)
      (grep-paths f bin fext)
      (.endsWith (.getName f) fext)
      (let [p (.getParentFile f)]
        (when-not (contains? @bin p))
          (swap! bin assoc p p))
      :else nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn grepFolderPaths

  "Recurse a folder, picking out sub-folders
   which contain files with the given extension"
  [rootDir ext]

  (let [rpath (.getCanonicalPath (io/file rootDir))
        rlen (.length rpath)
        out (atom [])
        bin (atom {})]
    (grep-paths rootDir bin ext)
    (doseq [[k v] @bin]
      (let [kp (.getCanonicalPath ^File k)]
        (swap! out conj (.substring kp (+ rlen 1)))))
    @out))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn fp!

  "Constructs a file path"
  [& args]

  (cs/join "/" args))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- glocal ""

  [k]

  (let [v (get @D-VARS k)]
    (if (fn? v)
      (v k)
      v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ge ""

  [k & [local]]

  (or
    (if (nil? local)
      (if-let [v (get @U-VARS k)]
        (if (fn? v)
          (v k)
          v)
        (glocal k))
      (glocal k))
    (get-env k)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro minitask

  "Wraps it like an ant task"
  [func & forms]

  `(do (println (str ~func ":")) ~@forms))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn artifactID ""

  []

  (name (ge :project)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn listCljNsps

  "Format a list of clojure namespaces"

  [root & paths]

  (let [base #(cs/replace (.getName %) #"\.[^\.]+$" "")
        dot #(cs/replace % "/" ".")
        ffs #(and (.isFile %)
                  (.endsWith (.getName %) ".clj"))]
    (sort
      (reduce
        (fn [memo path]
          (let [nsp (dot path)]
            (concat
              memo
              (map #(str nsp "." (base %))
                   (filter ffs
                           (.listFiles (io/file root path)))))))
        []
        paths))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clsBuild ""

  [& args]

  (minitask "clean/build"
    (a/cleanDir (io/file (ge :bootBuildDir)))
    (a/cleanDir (io/file (ge :libDir)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn preBuild ""

  [& args]

  (minitask "pre/build"
    (doseq [s (ge :mdirs)]
      (.mkdirs (io/file s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn compileJava ""

  []

  (a/runTarget*
    "compile/java"
    (a/antJavac
      (ge :JAVAC_OPTS)
      [[:compilerarg (ge :COMPILER_ARGS)]
       [:include "**/*.java"]
       [:classpath (ge :CPATH)]])
    (a/antCopy
      {:todir (ge :jzzDir)}
      [[:fileset {:dir (fp! (ge :srcDir) "java")
                  :excludes "**/*.java"}]])
    (a/antCopy
      {:todir (ge :jzzDir)}
      [[:fileset {:dir (fp! (ge :srcDir) "resources")}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn compileClj ""

  []

  (let [root (io/file (ge :srcDir) "clojure")
        ps (grepFolderPaths root ".clj")
        bin (atom '())]
    (doseq [p ps]
      (swap! bin concat (partition-all 25 (listCljNsps root p))))
    (minitask "compile/clj"
      (doseq [p @bin]
        (a/runTasks*
          (a/antJava
            (ge :CLJC_OPTS)
            (concat [[:argvalues p ]] (ge :CJNESTED)))))
      (a/runTasks*
        (a/antCopy
              {:todir (ge :czzDir)}
              [[:fileset {:dir root
                          :excludes "**/*.clj"}]])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn jarFiles ""

  []

  (let [j [:fileset
           {:dir (ge :jzzDir)
            :excludes "**/log4j.properties,**/logback.xml"} ]
        c [:fileset
           {:dir (ge :czzDir)
            :excludes "**/log4j.properties,**/logback.xml"} ]]
    (a/runTarget*
      "jar/files"
      (a/antJar
        {:destFile (fp! (ge :distDir)
                        (str (artifactID)
                             "-"
                             (ge :version) ".jar"))}
        [j c]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn preTest ""

  []

  (minitask
    "pretest"
    (.mkdirs (io/file (ge :buildTestDir)))
    (.mkdirs (io/file (ge :reportTestDir)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn compileJavaTest ""

  []

  (a/runTarget*
    "compile/test/java"
    (a/antJavac
      (merge (ge :JAVAC_OPTS)
             {:srcdir (fp! (ge :tstDir) "java")
              :destdir (ge :buildTestDir)})
      [[:include "**/*.java"]
       [:classpath (ge :TPATH)]
       [:compilerarg (ge :COMPILER_ARGS)]])
    (a/antCopy
      {:todir (ge :buildTestDir)}
      [[:fileset {:dir (fp! (ge :tstDir) "java")
                  :excludes "**/*.java"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn compileCljTest ""

  []

  (let [root (io/file (ge :tstDir) "clojure")
        ps (grepFolderPaths root ".clj")
        bin (atom '())]
    (doseq [p ps]
      (swap! bin concat (partition-all 25 (listCljNsps root p))))
    (minitask
      "compile/test/clj"
      (doseq [p @bin]
        (a/runTasks*
          (a/antJava
            (ge :CLJC_OPTS)
            [[:sysprops (assoc (ge :CLJC_SYSPROPS)
                               :clojure.compile.path (ge :buildTestDir))]
             [:classpath (ge :TJPATH)]
             [:argvalues p]])))
      (a/runTasks*
        (a/antCopy
          {:todir (ge :buildTestDir)}
          [[:fileset {:dir root
                      :excludes "**/*.clj"}]])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runCljTest ""

  []

  (a/runTarget*
    "run/test/clj"
    (a/antJunit
      {:logFailedTests true
       :showOutput false
       :printsummary true
       :fork true
       :haltonfailure true}
      [[:classpath (ge :TJPATH)]
       [:formatter {:type "plain"
                    :useFile false}]
       [:test {:name (ge :test-runner)
               :todir (ge :reportTestDir)}
              [[:formatter {:type "xml"}]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runJavaTest ""

  []

  (a/runTarget*
    "run/test/java"
    (a/antJunit
      {:logFailedTests true
       :showOutput false
       :printsummary true
       :fork true
       :haltonfailure true}
      [[:classpath (ge :TPATH)]
       [:formatter {:type "plain"
                    :useFile false}]
       [:batchtest {:todir (ge :reportTestDir)}
                   [[:fileset {:dir (ge :buildTestDir)}
                              [[:include "**/JUnit.*"]]]
                    [:formatter {:type "xml"}]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn buildCljTest ""

  []

  (a/cleanDir (io/file (ge :buildTestDir)))
  (preTest)
  (compileJavaTest)
  (compileCljTest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn buildJavaTest ""

  []

  (a/cleanDir (io/file (ge :buildTestDir)))
  (preTest)
  (compileJavaTest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn runCmd ""

  [cmd workDir args]

  (a/runTarget*
    (str "cmd:" cmd)
    (a/antExec {:executable cmd
                :dir workDir
                :spawn false}
                [[:argvalues (or args [])]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn genDocs ""

  [& args]

  (let [rootDir (fp! (ge :packDir) "docs/api")
        srcDir (ge :srcDir)]
    (a/cleanDir rootDir)
    (a/runTarget*
      "pack/docs"
      (a/antJavadoc
        {:destdir rootDir
         :access "protected"
         :author true
         :nodeprecated false
         :nodeprecatedlist false
         :noindex false
         :nonavbar false
         :notree false
         :source "1.8"
         :splitindex true
         :use true
         :version true}
         [[:fileset {:dir (fp! srcDir "java")
                     :includes "**/*.java"}]
          [:classpath (ge :CPATH) ]])

    (a/antJava
      {:classname "czlab.tpcl.codox"
       :fork true
       :failonerror true}
      [[:argvalues [(ge :basedir)
                    (fp! srcDir "clojure")
                    (fp! rootDir)]]
       [:classpath (ge :CJPATH) ]]) )))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- bootEnvVars! "Basic vars"

  []

  (se! :homedir (System/getProperty "user.home"))
  (se! :basedir (System/getProperty "user.dir"))

  (se! :target-path "target")

  (se! :warnonref
            :clojure.compile.warn-on-reflection)
  (se! :warn-reflection true)

  (se! :pmode "dev")
  (se! :bld "cout")

  (se! :cout "z")
  (se! :jzz "j")
  (se! :czz "c")
  (se! :wzz "w")

  (se! :mdirs
            (fn [_]
              [(ge :bootBuildDir)
               (ge :distDir)
               (ge :libDir)
               (ge :qaDir)
               (ge :czzDir)
               (ge :jzzDir)]))

  (se! :bootBuildDir
            (fn [_] (fp! (ge :basedir)
                         (ge :bld))))

  (se! :jzzDir
            (fn [_] (fp! (ge :bootBuildDir)
                         (ge :jzz))))

  (se! :czzDir
            (fn [_] (fp! (ge :bootBuildDir)
                         (ge :czz))))

  (se! :wzzDir
            (fn [_] (fp! (ge :bootBuildDir)
                         (ge :wzz))))

  (se! :distDir
            (fn [_] (fp! (ge :bootBuildDir)
                         "d")))

  (se! :qaDir
            (fn [_] (fp! (ge :bootBuildDir)
                         "t")))

  (se! :docs
            (fn [_] (fp! (ge :bootBuildDir)
                         "docs")))

  (se! :libDir
            (fn [_] (fp! (ge :basedir)
                         (ge :target-path))))

  (se! :srcDir
            (fn [_] (fp! (ge :basedir)
                         "src" "main")))

  (se! :tstDir
            (fn [_] (fp! (ge :basedir)
                         "src" "test")))

  (se! :buildTestDir
            (fn [_] (fp! (ge :qaDir)
                         (ge :cout))))

  (se! :reportTestDir
            (fn [_] (fp! (ge :qaDir) "r")))

  (se! :packDir
            (fn [_] (fp! (ge :bootBuildDir)
                         "p"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn idAndVer

  ""
  []

  (str (artifactID) "-" (ge :version)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- bootSyncCPath ""

  [& paths]

  (doseq [p paths]
    (pom/add-classpath p)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- bootEnvPaths! ""

  []

  (se!
    :COMPILER_ARGS
    (fn [_]
      {:line "-Xlint:deprecation -Xlint:unchecked"}))

  (se!
    :COMPILE_OPTS
    (fn [_]
      {:includeantruntime false
       :debug (ge :debug)
       :fork true}))

  (se!
    :CPATH
    (fn [_]
      [[:location (ge :jzzDir)]
       [:location (ge :czzDir)]
       [:fileset {:dir (ge :libDir)
                  :includes "**/*.jar"}]]))

  (se!
    :TPATH
    (fn [_]
      (->> (ge :CPATH)
           (cons [:location (ge :buildTestDir)])
           (into []))))

  (se!
    :JAVAC_OPTS
    (fn [_]
      (merge {:srcdir (fp! (ge :srcDir) "java")
              :destdir (ge :jzzDir)
              :target "1.8"
              :debugLevel "lines,vars,source"}
             (ge :COMPILE_OPTS))))

  (se!
    :CJPATH
    (fn [_]
      (->> (ge :CPATH)
           (cons [:location (fp! (ge :srcDir)
                                 "clojure")])
           (into []))))

  (se!
    :TJPATH
    (fn [_]
      (->> (ge :CJPATH)
           (concat [[:location (fp! (ge :tstDir) "clojure")]
                    [:location (ge :buildTestDir)]])
           (into []))))

  (se!
    :CLJC_OPTS
    (fn [_]
      {:classname "clojure.lang.Compile"
       :fork true
       :failonerror true
       :maxmemory "2048m"}))

  (se!
    :CLJC_SYSPROPS
    (fn [_]
      {(ge :warnonref) (ge :warn-reflection)
       :clojure.compile.path (ge :czzDir)}))

  (se!
    :CJNESTED
    (fn [_]
      [[:sysprops (ge :CLJC_SYSPROPS)]
       [:classpath (ge :CJPATH)]]))

  (se!
    :CJNESTED_RAW
    (fn [_]
      [[:sysprops (-> (ge :CLJC_SYSPROPS)
                      (assoc (ge :warnonref) false))]
       [:classpath (ge :CJPATH)]]))

  nil)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bootEnv! "Setup env-vars and paths"

  [& [options]]

  (reset! U-VARS (merge {} options))
  (reset! LATCH 911)
  (bootEnvVars!)
  (bootEnvPaths!)
  (bootSyncCPath (str (ge :jzzDir) "/")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bootSpit ""

  [^String s file]

  (spit file s :encoding "utf-8"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bootSpitJson ""

  [json file]

  (bootSpit (js/write-str json) file))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bootSlurp ""

  ^String
  [file]

  (slurp file :encoding "utf-8"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn bootSlurpJson ""

  [file]

  (-> (bootSlurp file)
      (js/read-str :key-fn keyword)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(task-options!
  uber {:as-jars true}
  aot {:all true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask testjava

  "test java"

  []

  (bc/with-pre-wrap fileset
    (buildJavaTest)
    (runJavaTest)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask testclj

  "test clj"

  []

  (bc/with-pre-wrap fileset
    (buildCljTest)
    (runCljTest)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask juber

  "my own uber"

  []

  (bc/with-pre-wrap fileset
    (let [from (io/file (ge :basedir)
                        (ge :target-path))
          jars (output-files fileset)
          to (io/file (ge :libDir))]
      (a/runTarget
        "juber"
        (for [j (seq jars)
              :let [dir (:dir j)
                    pn (:path j)
                    ;;boot prepends a hash to the jar file, dunno why,
                    ;;but i dont like it, so ripping it out
                    mt (re-matches #"^[0-9a-z]*-(.*)" pn)]]
          (if (= (count mt) 2)
            (a/antCopy {:file (fp! dir pn)
                        :tofile (fp! to (last mt))})
            (a/antCopy {:file (fp! dir pn)
                        :todir to}))))
      (println (format "copied (%d) jars to %s" (count jars) to))
    fileset)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask nullfs

  ""

  []

  (bc/with-pre-wrap fileset
    (bc/new-fileset)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask libjars

  "resolve all dependencies (jars)"

  []

  (a/cleanDir (io/file (ge :libDir)))
  (comp (uber)(juber) (nullfs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask initBuild

  "clean,pre-build"

  []

  (bc/with-pre-wrap fileset
    (clsBuild)
    (preBuild)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask buildr

  "compile"

  []

  (bc/with-pre-wrap fileset
    (compileJava)
    (compileClj)
  fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask jar!

  "jar!"

  []

  (bc/with-pre-wrap fileset
    (let [p (str (ge :project))]
      (replaceFile
        (fp! (ge :jzzDir)
             p
             "version.properties")
        #(cs/replace %
                     "@@pom.version@@"
                     (ge :version))))
    (jarFiles)
  fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask post-pom!

  ""
  []

  (bc/with-pre-wrap fileset
    (doseq [f (seq (output-files fileset))]
      (let [dir (:dir f)
            pn (:path f)
            tf (io/file (ge :jzzDir) pn)
            pd (.getParentFile tf)]
        (when (.startsWith pn "META-INF")
          (.mkdirs pd)
          (spit tf
                (slurp (fp! dir pn)
                       :encoding "utf-8")
                :encoding "utf-8"))))
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask pom!

  ""
  []

  (comp (nullfs)
        (pom :project (ge :project)
             :version (ge :version))
        (post-pom!)
        (nullfs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask dev

  "clean,resolve,build"

  []

  (comp (initBuild)
        (libjars)
        (buildr)
        (pom!)
        (jar!)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask packDistro

  ""
  []

  (bc/with-pre-wrap fileset

    (let [root (ge :packDir)
          dist (ge :distDir)
          ver (ge :version)
          src (ge :srcDir)]
      ;; make some dirs
      (a/cleanDir root)
      (map #(.mkdirs (io/file root %))
           ["dist" "lib" "docs"])
      ;; copy license stuff
      (a/runTarget*
        "pack/lics"
        (a/antCopy
          {:todir root}
          [[:fileset
            {:dir (ge :basedir)
             :includes "*.md,LICENSE"}]]))
      ;; copy source
      (a/runTarget*
        "pack/src"
        (a/antCopy
          {:todir (fp! root "src/main/clojure")}
          [[:fileset {:dir (fp! src "clojure")}]])
        (a/antCopy
          {:todir (fp! root "src/main/java")}
          [[:fileset {:dir (fp! src "java")}]]))
      ;; copy distro jars
      (a/runTarget*
        "pack/dist"
        (a/antCopy
          {:todir (fp! root "dist")}
          [[:fileset {:dir dist
                      :includes "*.jar"}]]))
      (a/runTarget*
        "pack/lib"
        (a/antCopy
          {:todir (fp! root "lib")}
          [[:fileset {:dir (ge :libDir)}]]))

      (if (ge :wantDocs) (genDocs))

      ;; tar everything
      (a/runTarget*
        "pack/all"
        (a/antTar
          {:destFile
           (fp! dist (str (artifactID)
                          "-"
                          ver
                          ".tar.gz"))
           :compression "gzip"}
          [[:tarfileset {:dir root
                         :excludes "bin/**"}]
           [:tarfileset {:dir root
                         :mode "755"
                         :includes "bin/**"}]]))
      nil)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask localInstall

  ""
  []

  (comp
        (install :file
                 (str (ge :distDir)
                      "/"
                      (idAndVer)
                      ".jar"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


