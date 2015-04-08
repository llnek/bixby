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

  demo.async.core

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.process :only [DelayExec]]
        [czlabclj.xlib.util.core :only [notnil?]]
        [czlabclj.xlib.util.str :only [nsb]]
        [czlabclj.tardis.core.wfs :only [DefPTask]])

  (:import  [com.zotohlab.wflow FlowNode PTask Work AsyncWait
             PDelegate
             AsyncCallback AsyncResumeToken]
            [com.zotohlab.gallifrey.runtime AppMain]
            [com.zotohlab.gallifrey.core Container]
            [com.zotohlab.wflow Job]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doLongAsyncCall ""

  [^FlowNode cur]

  (let [t (AsyncResumeToken. cur)
        cb (reify AsyncCallback
             (onSuccess [_ result]
               (println "CB: Got WS callback: onSuccess")
               (println "CB: Tell the scheduler to re-schedule the original process")
               ;; use the token to tell framework to restart the idled process
               (.resume t result))
             (onError [_ err] (.resume t err))
             (onTimeout [this] (.onError this
                                         (Exception. "time out")))) ]
    (DelayExec #(.onSuccess cb "hello world after 10 seconds.")
               10000)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Demo [] PDelegate

  (getStartActivity [_ pipe]
    (require 'demo.async.core)
    (let [a1 (DefPTask
               (fn [cur job arg]
                 (println "/* Calling a mock-webservice which takes a long time (10secs),")
                 (println "- since the call is *async*, event loop is not blocked.")
                 (println "- When we get a *call-back*, the normal processing will continue */")
                 (doLongAsyncCall cur)
                 (println "\n\n")
                 (println "+ Just called the webservice, the process will be *idle* until")
                 (println "+ the websevice is done.")
                 (println "\n\n")
                 (AsyncWait.)))
          a2 (DefPTask
               (fn [cur job arg]
                 (println "-> The result from WS is: " arg)
                 nil)) ]
      (.chain a1 a2)))

  (onStop [_ pipe] )

  (onError [_ err cur] nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype DemoMain [] czlabclj.tardis.impl.ext.CljAppMain

  (contextualize [_ c] )

  (initialize [_]
    (println "Demo calling an async java-api & resuming."))

  (configure [_ cfg] )

  (start [_])

  (stop [_] )

  (dispose [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)


