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
    :name comzotohlabscljc.tardis.io.WEBServlet
    :extends javax.servlet.http.HttpServlet
    :init myInit
    :constructors {[] []}
    :exposes-methods { init superInit  getServletName myName}
    :state myState
  ))

(import '(org.eclipse.jetty.continuation ContinuationSupport))
(import '(org.eclipse.jetty.continuation Continuation))
(import '(javax.servlet.http Cookie HttpServletRequest))
(import '(javax.servlet ServletConfig))
(import '(java.util ArrayList))
(import '(java.net HttpCookie))

(import '(org.apache.commons.io IOUtils))
(import '(java.io IOException))
(import '(com.zotohlabs.frwk.io XData))
(import '(com.zotohlabs.gallifrey.io IOSession HTTPResult HTTPEvent))
(import '(com.zotohlabs.frwk.core Identifiable))


(use '[clojure.tools.logging :only [info warn error debug] ])
(use '[comzotohlabscljc.tardis.io.http  :only [make-http-result] ])
(use '[comzotohlabscljc.util.core :only [TryC] ])
(use '[comzotohlabscljc.tardis.io.triggers])
(use '[comzotohlabscljc.tardis.io.core])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defn- dispREQ [ ^comzotohlabscljc.tardis.io.WEBServlet c0
                 ^Continuation ct evt req rsp]
  (let [ ^comzotohlabscljc.tardis.core.sys.Element dev @(.myState c0)
         wm (.getAttr dev :waitMillis) ]
    (doto ct
      (.setTimeout wm)
      (.suspend rsp))
    (let [ ^comzotohlabscljc.tardis.io.core.WaitEventHolder
           w  (make-async-wait-holder (make-servlet-trigger req rsp dev) evt)
          ^comzotohlabscljc.tardis.io.core.EmitterAPI  src @(.myState c0) ]
      (.timeoutMillis w wm)
      (.hold src w)
      (.dispatch src evt {}))))

(defn- doASyncSvc [this evt req rsp]
  (let [ c (ContinuationSupport/getContinuation req) ]
    (when (.isInitial c) 
      (TryC
          (dispREQ this c evt req rsp) ))))

(defn- doSyncSvc [this evt req rsp]
  (throw (IOException. "No Sync Service!!!!!!")))

(defn -myInit []
  [ [] 
    (atom nil) ] )

(defn -service [ ^comzotohlabscljc.tardis.io.WEBServlet this
                 ^HttpServletRequest req rsp]
  (let [ state (.myState this)
         evt (ioes-reify-event @state req) ]
    (debug
      "********************************************************************"
      (.getRequestURL req)
      "********************************************************************")
    (if true
      (doASyncSvc this evt req rsp)
      (doSyncSvc this evt req rsp))))


(defn -init [ ^comzotohlabscljc.tardis.io.WEBServlet this ^ServletConfig cfg]
  (do
    (.superInit this cfg)
    (let [ ctx (.getServletContext cfg)
           state (.myState this)
           src (.getAttribute ctx "czchhhiojetty") ]
      (reset! state src)
      (TryC
        (debug
          "********************************************************************\n"
          (str "Servlet Container: " (.getServerInfo ctx) "\n")
          (str "Servlet IO: " src "\n")
          "********************************************************************\n"
          (str "Servlet:iniz() - servlet:" (.myName this) "\n" ) )) )))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;





(def ^:private servlet-eof nil)

