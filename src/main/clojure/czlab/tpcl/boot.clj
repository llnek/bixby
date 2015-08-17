;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.tpcl.boot

  (:require
    [boot.task.built-in :refer [uber aot]]
    [czlab.xlib.util.logging :as log]
    [clojure.data.json :as js]
    [cemerick.pomegranate :as pom]
    [clojure.java.io :as io]
    [boot.core :as bc :refer :all]
    [clojure.string :as cs]
    [czlab.tpcl.antlib :as a])

  (:import
    [java.util GregorianCalendar Date Stack UUID]
    [java.text SimpleDateFormat]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ge "" [expr] `(bc/get-env ~expr))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn fp! "" [& args] (cs/join "/" args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn se!

  "Local version of set-env!"

  [options k dv]

  (if-some [v (get options k)]
    (if (fn? v)
      (v options k)
      (set-env! k v))
    (set-env! k dv)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro minitask ""

  [func & forms]

  `(do (println (str ~func ":")) ~@forms))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FmtTime ""

  [^String fmt]

  (-> (SimpleDateFormat. fmt)
      (.format (-> (GregorianCalendar.)
                   (.getTimeInMillis)
                   (Date.)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RandUUID "" [] (UUID/randomUUID))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReplaceFile ""

  [file work]

  {:pre [(fn? work)]}

  (->> (-> (slurp file :encoding "utf-8")
           (work))
       (spit file)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FmtCljNsps

  "Format list of namespaces"

  [root & paths]

  (let [bn #(cs/replace (.getName %) #"\.[^\.]+$" "")
        dot #(cs/replace % "/" ".")
        ffs #(and (.isFile %)
                  (.endsWith (.getName %) ".clj"))]
    (reduce
      (fn [memo path]
        (let [nsp (dot path)]
          (concat
            memo
            (map #(str nsp "." (bn %))
                 (filter ffs
                         (.listFiles (io/file root path)))))))
      []
      paths)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Babel

  "Run babel on the given arguments"

  [workingDir args]

  (a/RunTarget*
    "babel"
    (a/AntExec
      {:executable "babel"
       :dir workingDir}
      [[:argvalues args ]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- walk-tree ""

  [cfgtor ^Stack stk seed]

  (doseq [f (-> (or seed (.peek stk))
                (.listFiles))]
    (let [p (if (.empty stk)
              '()
              (for [x (.toArray stk)] (.getName x)))
          fid (.getName f)
          paths (conj (into [] p) fid) ]
      (if
        (.isDirectory f)
        (when (some? (cfgtor f :dir true))
          (.push stk f)
          (walk-tree cfgtor stk nil))
        ;else
        (when-some [rc (cfgtor f :paths paths)]
          (Babel (:work-dir rc) (:args rc))
          (cfgtor f :paths paths :postgen true)))))
  (when-not (.empty stk) (.pop stk)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BabelTree ""

  [rootDir cfgtor]

  {:pre [(fn? cfgtor)]}

  (walk-tree cfgtor (Stack.) (io/file rootDir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- collect-paths ""

  [^File top bin fext]

  (doseq [f (.listFiles top)]
    (cond
      (.isDirectory f)
      (collect-paths f bin fext)
      (.endsWith (.getName f) fext)
      (let [p (.getParentFile f)]
        (when-not (contains? @bin p))
          (swap! bin assoc p p))
      :else nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CollectCljPaths ""

  [^File root]

  (let [rpath (.getCanonicalPath root)
        rlen (.length rpath)
        out (atom [])
        bin (atom {})]
    (collect-paths root bin ".clj")
    (doseq [[k v] @bin]
      (let [kp (.getCanonicalPath ^File k)]
        (swap! out conj (.substring kp (+ rlen 1)))))
    (sort @out)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CleanPublic ""

  [& args]

  (a/RunTarget* "clean/public"
    (a/AntDelete {}
      [[:fileset {:dir (fp! (ge :basedir) "public")
                  :includes "scripts/**,styles/**,pages/**"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Clean4Build ""

  [& args]

  (a/RunTarget* "clean/build"
    (a/AntDelete {:dir (ge :bootBuildDir)
                  :excludes (str (ge :czz) "/**")})
    (a/AntDelete {}
      [[:fileset {:dir (ge :czzDir)
                  :excludes "clojure/**"}]]))
  (a/CleanDir (io/file (ge :libDir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PreBuild ""

  [& args]

  (minitask "prebuild"
    (doseq [s [(ge :bootBuildDir)
               (ge :distDir)
               (ge :libDir)
               (ge :qaDir)
               (ge :wzzDir)
               (ge :czzDir)
               (ge :jzzDir)]]
      (.mkdirs (io/file s)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompileJava ""

  []

  (a/RunTarget* "compile/java"
    (a/AntJavac
      (ge :JAVAC_OPTS)
      [[:compilerarg (ge :COMPILER_ARGS)]
       [:include "**/*.java"]
       [:classpath (ge :CPATH)]])
    (a/AntCopy
      {:todir (ge :jzzDir)}
      [[:fileset {:dir (fp! (ge :srcDir) "java")
                  :excludes "**/*.java"}]])
    (a/AntCopy
      {:todir (ge :jzzDir)}
      [[:fileset {:dir (fp! (ge :srcDir) "resources")}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompileClj ""

  []

  (let [root (io/file (ge :srcDir) "clojure")
        ps (CollectCljPaths root)
        bin (atom '())]
    (doseq [p ps]
      (swap! bin concat (partition-all 25 (FmtCljNsps root p))))
    (minitask "compile/clj"
      (doseq [p @bin]
        (a/RunTasks*
          (a/AntJava
            (ge :CLJC_OPTS)
            (concat [[:argvalues p ]] (ge :CJNESTED)))))
      (a/RunTasks*
        (a/AntCopy
              {:todir (ge :czzDir)}
              [[:fileset {:dir root
                          :excludes "**/*.clj"}]])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn JarFiles ""

  []

  (let [j [:fileset {:dir (ge :jzzDir)
                     :excludes "demo/**,**/log4j.properties,**/logback.xml"} ]
        c [:fileset {:dir (ge :czzDir)
                     :excludes "demo/**,**/log4j.properties,**/logback.xml"} ] ]
    (a/RunTarget* "jar/files"
      (a/AntJar
        {:destFile (fp! (ge :distDir)
                        (str "java-" (ge :buildVersion) ".jar"))}
        [j])
      (a/AntJar
        {:destFile (fp! (ge :distDir)
                        (str "clj-" (ge :buildVersion) ".jar"))}
        [c])
      (a/AntJar
        {:destFile (fp! (ge :distDir)
                        (str (ge :PID) "-" (ge :buildVersion) ".jar"))}
        [j c]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PreTest ""

  []

  (minitask "pretest"
    (.mkdirs (io/file (ge :buildTestDir)))
    (.mkdirs (io/file (ge :reportTestDir)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompileJavaTest ""

  []

  (a/RunTarget* "compile/test/java"
    (a/AntJavac (merge (ge :JAVAC_OPTS)
                         {:srcdir (fp! (ge :tstDir) "java")
                          :destdir (ge :buildTestDir)})
                  [[:include "**/*.java"]
                   [:classpath (ge :TPATH)]
                   [:compilerarg (ge :COMPILER_ARGS)]])
    (a/AntCopy {:todir (ge :buildTestDir)}
                 [[:fileset {:dir (fp! (ge :tstDir) "java")
                             :excludes "**/*.java"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompileCljTest ""

  []

  (let [root (io/file (ge :tstDir) "clojure")
        ps (CollectCljPaths root)
        bin (atom '())]
    (doseq [p ps]
      (swap! bin concat (partition-all 25 (FmtCljNsps root p))))
    (minitask "compile/test/clj"
      (doseq [p @bin]
        (a/RunTasks*
          (a/AntJava
            (ge :CLJC_OPTS)
            [[:sysprops (assoc (ge :CLJC_SYSPROPS)
                               :clojure.compile.path (ge :buildTestDir))]
             [:classpath (ge :TJPATH)]
             [:argvalues p]])))
      (a/RunTasks*
        (a/AntCopy
          {:todir (ge :buildTestDir)}
          [[:fileset {:dir root
                      :excludes "**/*.clj"}]])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunCljTest ""

  []

  (a/RunTarget* "run/test/clj"
    (a/AntJunit
      {:logFailedTests true
       :showOutput false
       :printsummary true
       :fork true
       :haltonfailure true}
      [[:classpath (ge :TJPATH)]
       [:formatter {:type "plain"
                    :useFile false}]
       [:test {:name (str (ge :DOMAIN) ".ClojureJUnit")
               :todir (ge :reportTestDir)}
              [[:formatter {:type "xml"}]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunJavaTest ""

  []

  (a/RunTarget* "run/test/java"
    (a/AntJunit
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
(defn BuildCljTest ""

  []

  (a/CleanDir (io/file (ge :buildTestDir)))
  (PreTest)
  (CompileJavaTest)
  (CompileCljTest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BuildJavaTest ""

  []

  (a/CleanDir (io/file (ge :buildTestDir)))
  (PreTest)
  (CompileJavaTest))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunCmd ""

  [cmd workDir args]

  (a/RunTarget* cmd
    (a/AntExec {:executable cmd
                :dir workDir
                :spawn false}
                [[:argvalues (or args [])]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootEnvVars ""

  [& [options]]

  (let [options (or options {})]

    (se! options :warn-reflection :clojure.compile.warn-on-reflection)
    (se! options :skaroHome (System/getProperty "skaro.home.dir"))
    (se! options :basedir (System/getProperty "skaro.app.dir"))

    (se! options :cout "z")
    (se! options :jzz "j")
    (se! options :czz "c")
    (se! options :wzz "w")

    (se! options :bld "build")
    (se! options :pmode "dev")

    (se! options :bootBuildDir (fp! (ge :basedir) (ge :bld)))

    (se! options :jzzDir (fp! (ge :bootBuildDir) (ge :jzz)))
    (se! options :czzDir (fp! (ge :bootBuildDir) (ge :czz)))
    (se! options :wzzDir (fp! (ge :bootBuildDir) (ge :wzz)))

    (se! options :distDir (fp! (ge :bootBuildDir) "d"))
    (se! options :qaDir (fp! (ge :bootBuildDir) "t"))
    (se! options :docs (fp! (ge :bootBuildDir) "docs"))

    (se! options :libDir (fp! (ge :basedir)
                              (ge :target-path)))

    (se! options :srcDir (fp! (ge :basedir) "src" "main"))
    (se! options :tstDir (fp! (ge :basedir) "src" "test"))

    (se! options :reportTestDir (fp! (ge :qaDir) "reports"))
    (se! options :buildTestDir (fp! (ge :qaDir) (ge :cout)))

    (doseq [k (keys options)]
      (when (nil? (get-env k))
        (se! options k nil))) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootSyncCPath ""

  [& paths]

  (doseq [p paths]
    (pom/add-classpath p)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootEnvPaths ""

  [& [options]]

  (let [options (or options {})]

    (se! options :COMPILER_ARGS {:line "-Xlint:deprecation -Xlint:unchecked"})

    (se! options :COMPILE_OPTS {:debug (ge :buildDebug)
                                :includeantruntime false
                                :fork true})

    (se! options :CPATH [[:location (fp! (ge :srcDir) "artifacts")]
                         [:location (ge :jzzDir)]
                         [:location (ge :czzDir)]
                         [:fileset {:dir (ge :libDir)
                                    :includes "**/*.jar"}]
                         [:fileset {:dir (fp! (ge :skaroHome) "dist")
                                    :includes "**/*.jar"} ]
                         [:fileset {:dir (fp! (ge :skaroHome) "lib")
                                    :includes "**/*.jar"} ]] )

    (se! options :TPATH (->> (ge :CPATH)
                             (cons [:location (ge :buildTestDir)])
                             (into [])))

    (se! options :JAVAC_OPTS (merge {:srcdir (fp! (ge :srcDir) "java")
                                     :destdir (ge :jzzDir)
                                     :target "1.8"
                                     :debugLevel "lines,vars,source"}
                                    (ge :COMPILE_OPTS)))

    (se! options :CJPATH (->> (ge :CPATH)
                              (cons [:location (fp! (ge :srcDir) "clojure")])
                              (into [])))

    (se! options :TJPATH (->> (ge :CJPATH)
                              (concat [[:location (fp! (ge :tstDir) "clojure")]
                                       [:location (ge :buildTestDir)]])
                              (into [])))

    (se! options :CLJC_OPTS {:classname "clojure.lang.Compile"
                             :fork true
                             :failonerror true
                             :maxmemory "2048m"})

    (se! options :CLJC_SYSPROPS {:clojure.compile.path (ge :czzDir)
                                 (ge :warn-reflection) true})

    (se! options :CJNESTED [[:sysprops (ge :CLJC_SYSPROPS)]
                            [:classpath (ge :CJPATH)]])

    (doseq [k (keys options)]
      (when (nil? (get-env k))
        (se! options k nil)))

    (BootSyncCPath (str (ge :jzzDir) "/"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootSpit ""

  [^String s file]

  (spit file s :encoding "utf-8"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootSpitJson ""

  [json file]

  (BootSpit (js/write-str json) file))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootSlurp ""

  ^String
  [file]

  (slurp file :encoding "utf-8"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootSlurpJson ""

  [file]

  (-> (BootSlurp file)
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
    (BuildJavaTest)
    (RunJavaTest)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask testclj

  "test clj"

  []

  (bc/with-pre-wrap fileset
    (BuildCljTest)
    (RunCljTest)
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
      (a/RunTarget
        "juber"
        (for [j (seq jars)]
          (a/AntCopy {:file (fp! (:dir j) (:path j))
                        :todir to})))
      (format "copied (%d) jars to %s" (count jars) to))
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask libjars

  "resolve all dependencies (jars)"

  []

  (comp (uber)(juber)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask clean4build

  "clean,pre-build"

  []

  (bc/with-pre-wrap fileset
    (Clean4Build)
    (CleanPublic)
    (PreBuild)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask buildr

  "compile"

  []

  (bc/with-pre-wrap fileset
    (CompileJava)
    (CompileClj)
  fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask dev

  "clean,resolve,build"

  []

  (comp (clean4build)
        (libjars)
        (buildr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask jar!

  "jar!"

  []

  (bc/with-pre-wrap fileset
    (JarFiles)
  fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

