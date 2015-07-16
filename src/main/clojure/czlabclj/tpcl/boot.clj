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

  czlabclj.tpcl.boot

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [boot.core :as bc :refer :all]
            [boot.task.built-in :refer [uber aot]]
            [clojure.string :as cs]
            [czlabclj.tpcl.antlib :as a])

  (:import [java.util GregorianCalendar
            Date Stack UUID]
           [java.text SimpleDateFormat]
           [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn fp! "" [& args] (cs/join "/" args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ge "" [expr] `(bc/get-env ~expr))

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

  (->> (-> (slurp file :encoding "utf-8")
           (work))
       (spit file)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FmtCljNsps "Format list of namespaces."

  [root & dirs]

  (reduce
    (fn [memo dir]
      (let [nsp (cs/replace dir "/" ".")]
        (concat
          memo
          (map #(str nsp
                     "."
                     (cs/replace (.getName %) #"\.[^\.]+$" ""))
               (filter #(and (.isFile %)
                             (.endsWith (.getName %) ".clj"))
                       (.listFiles (io/file root dir)))))))
    []
    dirs
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Babel "Run babel on the given arguments."

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
      (cond
        (.isDirectory f)
        (when-not (nil? (cfgtor f :dir true))
          (.push stk f)
          (walk-tree cfgtor stk nil))
        :else
        (when-let [rc (cfgtor f :paths paths)]
          (Babel (:work-dir rc) (:args rc))
          (cfgtor f :paths paths :postgen true)))))

  (when-not (.empty stk) (.pop stk)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BabelTree ""

  [rootDir cfgtor]

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
      :else nil)
  ))

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
    (sort @out)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CleanPublic ""

  []

  (a/RunTarget* "clean/public"
    (a/AntDelete {}
      [[:fileset {:dir (fp! (ge :basedir) "public")
                  :includes "scripts/**,styles/**,pages/**"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Clean4Build ""

  []

  (a/RunTarget* "clean/build"
    (a/AntDelete {:dir (ge :bootBuildDir)
                  :excludes (str (ge :bld) "/**")})
    (a/AntDelete {}
      [[:fileset {:dir (ge :buildDir)
                  :excludes "clojure/**"}]]))
  (a/CleanDir (io/file (ge :libDir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PreBuild ""

  []

  (minitask "prebuild"
    (doseq [s [(ge :bootBuildDir)
               (ge :patchDir)
               (ge :libDir)
               (ge :buildDir)]]
      (.mkdirs (io/file s)))
  ))

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
      {:todir (ge :buildDir)}
      [[:fileset {:dir (fp! (ge :srcDir) "java")
                  :excludes "**/*.java"}]])))

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
              {:todir (ge :buildDir)}
              [[:fileset {:dir root
                          :excludes "**/*.clj"}]])))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn JarFiles ""

  []

  (a/RunTarget* "jar/files"
    (a/AntJar
      {:destFile (fp! (ge :libDir)
                      (str (ge :PID) "-" (ge :buildVersion) ".jar"))}
      [[:fileset {:dir (ge :buildDir)} ]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PreTest ""

  []

  (minitask "pretest"
    (.mkdirs (io/file (ge :buildTestDir)))
    (.mkdirs (io/file (ge :reportTestDir)))
  ))

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
                      :excludes "**/*.clj"}]])))
  ))

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
(defn BootEnvVars ""

  []

  (set-env! :skaroHome (System/getProperty "skaro.home.dir"))
  (when (nil? (get-env :basedir))
    (set-env! :basedir (System/getProperty "skaro.app.dir")))

  (set-env! :bld "build")
  (set-env! :pmode "dev")

  (set-env! :bootBuildDir (fp! (ge :basedir) (ge :bld)))
  (set-env! :buildDir (fp! (ge :bootBuildDir) "classes"))
  (set-env! :qaDir (fp! (ge :bootBuildDir) "test"))
  (set-env! :docs (fp! (ge :bootBuildDir) "docs"))

  (set-env! :patchDir (fp! (ge :basedir) "patch"))
  (set-env! :libDir (fp! (ge :basedir)
                         (ge :target-path)))

  (set-env! :srcDir (fp! (ge :basedir) "src" "main"))
  (set-env! :tstDir (fp! (ge :basedir) "src" "test"))

  (set-env! :reportTestDir (fp! (ge :qaDir) "reports"))
  (set-env! :buildTestDir (fp! (ge :qaDir) "classes"))

  (.mkdirs (io/file (ge :buildDir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootEnvPaths ""

  []

  (set-env! :COMPILER_ARGS {:line "-Xlint:deprecation -Xlint:unchecked"})

  (set-env! :COMPILE_OPTS {:debug (ge :buildDebug)
                           :includeantruntime false
                           :fork true})

  (set-env! :CPATH [[:location (ge :buildDir)]
                    [:fileset {:dir (ge :libDir)
                               :includes "**/*.jar"}]
                    [:fileset {:dir (fp! (ge :skaroHome) "dist")
                               :includes "**/*.jar"} ]
                    [:fileset {:dir (fp! (ge :skaroHome) "lib")
                               :includes "**/*.jar"} ]] )

  (set-env! :TPATH (->> (ge :CPATH)
                        (cons [:location (ge :buildTestDir)])
                        (into [])))

  (set-env! :JAVAC_OPTS (merge {:srcdir (fp! (ge :srcDir) "java")
                                :destdir (ge :buildDir)
                                :target "1.8"
                                :debugLevel "lines,vars,source"}
                                (ge :COMPILE_OPTS)))

  (set-env! :CJPATH (->> (ge :CPATH)
                         (cons [:location (fp! (ge :srcDir) "clojure")])
                         (into [])))

  (set-env! :TJPATH (->> (ge :CJPATH)
                         (concat [[:location (fp! (ge :tstDir) "clojure")]
                                  [:location (ge :buildTestDir)]])
                         (into [])))

  (set-env! :CLJC_OPTS {:classname "clojure.lang.Compile"
                        :fork true
                        :failonerror true
                        :maxmemory "2048m"})

  (set-env! :CLJC_SYSPROPS {:clojure.compile.warn-on-reflection true
                            :clojure.compile.path (ge :buildDir) })

  (set-env! :CJNESTED [[:sysprops (ge :CLJC_SYSPROPS)]
                       [:classpath (ge :CJPATH)]]) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(task-options!
  uber {:as-jars true}
  aot {:all true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask testjava "test java"

  []

  (bc/with-pre-wrap fileset
    (BuildJavaTest)
    (RunJavaTest)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask testclj "test clj"

  []

  (bc/with-pre-wrap fileset
    (BuildCljTest)
    (RunCljTest)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask juber "my own uber"

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
    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask libjars "resolve all dependencies (jars)"

  []

  (comp (uber)(juber)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask clean4build "clean,pre-build"

  []

  (bc/with-pre-wrap fileset
    (Clean4Build)
    (CleanPublic)
    (PreBuild)
    fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask buildr "compile"

  []

  (bc/with-pre-wrap fileset
    (CompileJava)
    (CompileClj)
  fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask dev "clean,resolve,build"

  []

  (comp (clean4build)
        (libjars)
        (buildr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask jar! "jar!"

  []

  (bc/with-pre-wrap fileset
    (JarFiles)
  fileset))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF
