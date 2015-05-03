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

  czlabclj.tardis.io.socket

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.core
         :only
         [NextLong test-posnum ConvLong spos?]]
        [czlabclj.tardis.io.core]
        [czlabclj.tardis.core.sys]
        [czlabclj.xlib.util.process :only [Coroutine]]
        [czlabclj.xlib.util.meta :only [GetCldr]]
        [czlabclj.xlib.util.str :only [strim nsb hgl?]])

  (:import  [java.net InetAddress ServerSocket Socket]
            [org.apache.commons.io IOUtils]
            [com.zotohlab.frwk.core Identifiable]
            [com.zotohlab.skaro.io SocketEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/SocketIO

  [co & args]

  (log/info "IOESReifyEvent: SocketIO: " (.id ^Identifiable co))
  (let [^Socket soc (first args)
        eeid (NextLong) ]
    (with-meta
      (reify

        Identifiable
        (id [_] eeid)

        SocketEvent
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (checkAuthenticity [_] false)
        (getSockOut [_] (.getOutputStream soc))
        (getSockIn [_] (.getInputStream soc))
        (emitter [_] co)
        (dispose [_] (IOUtils/closeQuietly soc)))

      { :typeid :czc.tardis.io/SocketEvent }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/SocketIO

  [^czlabclj.tardis.core.sys.Elmt co cfg0]

  (log/info "CompConfigure: SocketIO: " (.id ^Identifiable co))
  (test-posnum "socket-io port" (:port cfg0))
  (let [cfg (merge (.getAttr co :dftOptions) cfg0)
        tout (:timeoutMillis cfg)
        blog (:backlog cfg) ]
    (with-local-vars [cpy (transient cfg)]
      (var-set cpy (assoc! @cpy :backlog
                           (if (spos? blog) blog 100)))
      (var-set cpy (assoc! @cpy
                           :host (strim (:host cfg))))
      (var-set cpy (assoc! @cpy
                           :timeoutMillis
                           (if (spos? tout) tout 0)))
      (.setAttr! co :emcfg (persistent! @cpy)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/SocketIO

  [^czlabclj.tardis.core.sys.Elmt co]

  (log/info "CompInitialize: SocketIO: " (.id ^Identifiable co))
  (let [cfg (.getAttr co :emcfg)
        backlog (:backlog cfg)
        host (:host cfg)
        port (:port cfg)
        ip (if (hgl? host)
             (InetAddress/getByName host)
             (InetAddress/getLocalHost))
        soc (ServerSocket. port backlog ip) ]
    (log/info "Opened Server Socket " soc  " (bound?) " (.isBound soc))
    (doto soc (.setReuseAddress true))
    (.setAttr! co :ssocket soc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sockItDown ""

  [^czlabclj.tardis.io.core.EmitAPI co ^Socket soc]

  (.dispatch co (IOESReifyEvent co soc) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/SocketIO

  [^czlabclj.tardis.core.sys.Elmt co]

  (log/info "IOESStart: SocketIO: " (.id ^Identifiable co))
  (let [^ServerSocket ssoc (.getAttr co :ssocket)]
    (when-not (nil? ssoc)
      (Coroutine #(while (.isBound ssoc)
                    (try
                      (sockItDown co (.accept ssoc))
                      (catch Throwable e#
                        (log/warn e# "")
                        (IOUtils/closeQuietly ssoc)
                        (.setAttr! co :ssocket nil))))
                 (GetCldr)))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/SocketIO

  [^czlabclj.tardis.core.sys.Elmt co]

  (log/info "IOESStop: SocketIO: " (.id ^Identifiable co))
  (let [^ServerSocket ssoc (.getAttr co :ssocket) ]
    (IOUtils/closeQuietly ssoc)
    (.setAttr! co :ssocket nil)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private socket-eof nil)

