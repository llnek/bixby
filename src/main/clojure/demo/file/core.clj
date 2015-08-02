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


(ns ^:no-doc
    ^{:author "kenl"}

  demo.file.core

  (:require [czlab.xlib.util.core :refer [try!]]
            [czlab.xlib.util.str :refer [nsb]])

  (:require [clojure.tools.logging :as log])

  (:import  [com.zotohlab.wflow WHandler Job FlowDot PTask ]
            [com.zotohlab.skaro.core Container]
            [com.zotohlab.skaro.io FileEvent]
            [com.zotohlab.frwk.server ServiceProvider Service]
            [java.util.concurrent.atomic AtomicInteger]
            [org.apache.commons.io FileUtils]
            [java.util Date]
            [java.lang StringBuilder]
            [java.io File IOException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(let [ctr (AtomicInteger.)]
  (defn- ncount ""
    []
    (.incrementAndGet ctr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype DemoGen [] WHandler

  (run [_  j _]
    (require 'demo.file.core)
    (let [^Service p (-> ^ServiceProvider
                         (.container ^Job j)
                         (.getService :default-sample))
          s (str "Current time is " (Date.)) ]
      (spit (File. (nsb (.getv p :targetFolder))
                   (str "ts-" (ncount) ".txt"))
            s :encoding "utf-8"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype DemoPick [] WHandler

  (run [_  j _]
    (require 'demo.file.core)
    (let [f (-> ^FileEvent (.event ^Job j)
                (.getFile)) ]
      (println "Picked up new file: " f)
      (println "Content: " (slurp f :encoding "utf-8")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

