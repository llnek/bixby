;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.


(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.tardis.io.servlet

  (:gen-class
    :extends com.zotohlabs.gallifrey.io.AbstractServlet
    :exposes-methods { getServletName myName}
    :name comzotohlabscljc.tardis.io.WEBServlet
    :init myInit
    :constructors {[] []}
    :state myState)

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.tardis.io.http  :only [MakeHttpResult] ])
  (:use [comzotohlabscljc.util.core :only [ThrowIOE TryC] ])
  (:use [comzotohlabscljc.tardis.io.triggers])
  (:use [comzotohlabscljc.tardis.io.core])
  (:use [comzotohlabscljc.tardis.io.webss])

  (:import (org.eclipse.jetty.continuation ContinuationSupport))
  (:import (org.eclipse.jetty.continuation Continuation))
  (:import (javax.servlet.http Cookie HttpServletRequest HttpServletResponse))
  (:import (javax.servlet ServletConfig))
  (:import (java.util ArrayList))
  (:import (java.net HttpCookie))

  (:import (org.apache.commons.io IOUtils))
  (:import (java.io IOException))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (com.zotohlabs.gallifrey.io IOSession HTTPResult HTTPEvent))
  (:import (com.zotohlabs.frwk.core Identifiable)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- dispREQ ""

  [ ^comzotohlabscljc.tardis.io.WEBServlet c0
    ^Continuation ct ^HTTPEvent evt req rsp]

  (let [ ^comzotohlabscljc.tardis.core.sys.Element dev (.emitter evt)
         ssl (= "https" (.getScheme ^HttpServletRequest req))
         wss (MakeWSSession dev ssl)
         wm (.getAttr dev :waitMillis) ]
    (.bindSession evt wss)
    (doto ct
          (.setTimeout wm)
          (.suspend ^HttpServletResponse rsp))
    (let [ ^comzotohlabscljc.tardis.io.core.WaitEventHolder
           w  (MakeAsyncWaitHolder (MakeServletTrigger req rsp dev) evt)
          ^comzotohlabscljc.tardis.io.core.EmitterAPI src dev ]
      (.timeoutMillis w wm)
      (.hold src w)
      (.dispatch src evt {}))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doASyncSvc ""

  [this evt req rsp]

  (let [ c (ContinuationSupport/getContinuation req) ]
    (when (.isInitial c)
      (TryC
          (dispREQ this c evt req rsp) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doSyncSvc ""

  [this evt req rsp]

  (ThrowIOE "No Sync Service!!!!!!"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -myInit []
  [ []
    (atom nil) ] )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -service ""

  [ ^comzotohlabscljc.tardis.io.WEBServlet this
    ^HttpServletRequest req rsp]

  (let [ state (.myState this)
         evt (IOESReifyEvent @state req) ]
    (log/debug
      "********************************************************************"
      (.getRequestURL req)
      "********************************************************************")
    (if true
      (doASyncSvc this evt req rsp)
      (doSyncSvc this evt req rsp))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -doInit ""

  [ ^comzotohlabscljc.tardis.io.WEBServlet this]

  (let [ cfg (.getServletConfig this)
         ctx (.getServletContext cfg)
         state (.myState this)
         src (.getAttribute ctx "czchhhiojetty") ]
    (reset! state src)
    (TryC
      (log/debug
        "********************************************************************\n"
        (str "Servlet Container: " (.getServerInfo ctx) "\n")
        (str "Servlet IO: " src "\n")
        "********************************************************************\n"
        (str "Servlet:iniz() - servlet:" (.myName this) "\n" ) )) 
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private servlet-eof nil)

