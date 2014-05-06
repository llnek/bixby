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

  comzotohlabscljc.tardis.io.socket

  (:require [clojure.tools.logging :as log :only (info warn error debug)])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.tardis.io.core])
  (:use [comzotohlabscljc.tardis.core.sys])

  (:use [comzotohlabscljc.util.process :only [Coroutine] ])
  (:use [comzotohlabscljc.util.meta :only [GetCldr] ])
  (:use [comzotohlabscljc.util.core :only [test-posnum ConvLong] ])
  (:use [comzotohlabscljc.util.str :only [nsb hgl?] ])
  (:use [comzotohlabscljc.util.seqnum :only [NextLong] ])
  (:import (java.net InetAddress ServerSocket Socket))
  (:import (org.apache.commons.io IOUtils))
  (:import (com.zotohlabs.frwk.core Identifiable))
  (:import (com.zotohlabs.gallifrey.io SocketEvent)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSocketIO ""

  [container]

  (MakeEmitter container :czc.tardis.io/SocketIO))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/SocketIO

  [co & args]

  (let [ ^Socket soc (first args)
         eeid (NextLong) ]
    (with-meta
      (reify

        Identifiable
        (id [_] eeid)

        SocketEvent
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (getSockOut [_] (.getOutputStream soc))
        (getSockIn [_] (.getInputStream soc))
        (emitter [_] co)
        (dispose [_] (IOUtils/closeQuietly soc)))

      { :typeid :czc.tardis.io/SocketEvent }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/SocketIO

  [^comzotohlabscljc.tardis.core.sys.Element co cfg]

  (let [ tout (:sock-timeout-millis cfg)
         host (:host cfg)
         port (:port cfg)
         blog (:backlog cfg) ]
    (test-posnum "socket-io port" port)
    (.setAttr! co :timeoutMillis (ConvLong tout 0))
    (.setAttr! co :host (nsb host))
    (.setAttr! co :port (int port))
    (.setAttr! co :backlog (ConvLong blog 100))
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/SocketIO

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ backlog (.getAttr co :backlog)
         host (.getAttr co :host)
         port (.getAttr co :port)
         ip (if (hgl? host)
                (InetAddress/getByName host)
                (InetAddress/getLocalHost))
         soc (ServerSocket. port backlog ip) ]
    (log/info "opened Server Socket " soc  " (bound?) " (.isBound soc))
    (doto soc (.setReuseAddress true))
    (.setAttr! co :ssocket soc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sockItDown ""

  [^comzotohlabscljc.tardis.io.core.EmitterAPI co ^Socket soc]

  (.dispatch co (IOESReifyEvent co soc) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/SocketIO

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^ServerSocket ssoc (.getAttr co :ssocket)
         cl (GetCldr) ]
    (when-not (nil? ssoc)
      (Coroutine (fn []
                   (while (.isBound ssoc)
                     (try
                       (sockItDown co (.accept ssoc))
                       (catch Throwable e#
                         (log/warn e# "")
                         (IOUtils/closeQuietly ssoc)
                         (.setAttr! co :ssocket nil)))))
                 cl))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/SocketIO

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^ServerSocket ssoc (.getAttr co :ssocket) ]
    (IOUtils/closeQuietly ssoc)
    (.setAttr! co :ssocket nil)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private socket-eof nil)

