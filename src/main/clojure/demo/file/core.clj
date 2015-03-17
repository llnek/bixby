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
      :author "kenl"}

  demo.file.core

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.process :only [ThreadFunc] ]
        [cmzlabclj.nucleus.util.core :only [Try!] ]
        [cmzlabclj.nucleus.util.str :only [nsb] ]
        [cmzlabclj.tardis.core.wfs :only [DefWFTask]])

  (:import  [com.zotohlab.wflow FlowNode PTask
                                PipelineDelegate]
            [com.zotohlab.gallifrey.core Container]
            [com.zotohlab.gallifrey.io FileEvent]
            [com.zotohlab.frwk.server Service]
            [java.util.concurrent.atomic AtomicInteger]
            [org.apache.commons.io FileUtils]
            [java.util Date]
            [java.lang StringBuilder]
            [java.io File IOException]
            [com.zotohlab.wflow.core Job]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^AtomicInteger _count (AtomicInteger.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ncount ""

  []

  (.incrementAndGet _count))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype DemoGen [] PipelineDelegate

  (getStartActivity [_ pipe]
    (require 'demo.file.core)
    (DefWFTask
      (fn [cur job arg]
        (let [s (str "Current time is " (Date.))
              ^Service p (-> (.container pipe)
                             (.getService :default-sample)) ]
          (FileUtils/writeStringToFile (File. (nsb (.getv p :targetFolder))
                                              (str "ts-" (ncount) ".txt"))
                                       s
                                       "utf-8")
          nil))))

  (onStop [_ p] )

  (onError [_ err c] nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype DemoPick [] PipelineDelegate

  (getStartActivity [_ pipe]
    (require 'demo.file.core)
    (DefWFTask
      (fn [cur ^Job job arg]
        (let [^FileEvent ev (.event job)
              f (.getFile ev) ]
          (println "Picked up new file: " f)
          (println "Content: " (FileUtils/readFileToString f "utf-8"))
          nil))))

  (onStop [_ p] )
  (onError [_ err c] nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype DemoMain [] cmzlabclj.tardis.impl.ext.CljAppMain

  (contextualize [_ c] )

  (initialize [_]
    (println "Demo file directory monitoring - picking up new files"))

  (configure [_ cfg] )

  (start [_] )

  (stop [_])

  (dispose [_] ))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

