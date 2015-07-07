;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tpcl.boot

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [boot.core :as boot]
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
;;EOF
