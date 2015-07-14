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
            [boot.core :as bcore :refer :all]
            [boot.task.built-in :refer [uber aot]]
            [clojure.string :as cstr]
            [czlabclj.tpcl.antlib :as ant])

  (:import [java.util GregorianCalendar
            Date Stack UUID]
           [java.text SimpleDateFormat]
           [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;(defmacro ^:private fp! "" [& args] `(cstr/join "/" '~args))
(defn fp! "" [& args] (cstr/join "/" args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ge "" [expr] `(bcore/get-env ~expr))

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
(defn RandUUID ""
  []
  (UUID/randomUUID))

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
      (let [nsp (cstr/replace dir #"\/" ".")]
        (concat
          memo
          (map #(str nsp
                     "."
                     (cstr/replace (.getName %) #"\.[^\.]+$" ""))
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

  (let []
    (ant/RunTarget*
      "babel"
      (ant/AntExec
        {:executable "babel"
         :dir workingDir}
        [[:argvalues args ]]))
  ))

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
(task-options!
  uber {:as-jars true}
  aot {:all true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask juber

  "my own uber"
  []

  (bcore/with-pre-wrap fileset

    (let [from (io/file (ge :basedir)
                        (ge :target-path))
          jars (output-files fileset)
          to (io/file (ge :libDir))]
      (ant/RunTarget
        "libjars"
        (for [j (seq jars)]
          (ant/AntCopy {:file (fp! (:dir j) (:path j))
                        :todir to})))
      (format "copied (%d) jars to %s" (count jars) to))

    fileset
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftask libjars

  "copy all dependencies (jars) to libdir"
  []

  (comp (uber)(juber)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BootEnvVars ""

  []

  (set-env! :bootBuildDir (fp! (ge :basedir) (ge :bld)))

  (set-env! :qaDir (fp! (ge :bootBuildDir) "test"))
  (set-env! :docs (fp! (ge :bootBuildDir) "docs"))

  (set-env! :podDir (fp! (ge :basedir) (ge :pod)))
  (set-env! :libDir (fp! (ge :basedir)
                         (ge :target-path)))

  (set-env! :srcDir (fp! (ge :basedir) "src" "main"))
  (set-env! :tstDir (fp! (ge :basedir) "src" "test"))
  (set-env! :buildDir (fp! (ge :podDir) "classes"))

  (set-env! :reportTestDir (fp! (ge :qaDir) "reports"))
  (set-env! :buildTestDir (fp! (ge :qaDir) "classes")) )

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
;;EOF
