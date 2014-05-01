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

  comzotohlabscljc.tardis.io.events

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.core :only [MubleAPI MakeMMap] ])
  (:use [comzotohlabscljc.tardis.io.core])
  (:use [comzotohlabscljc.util.seqnum :only [NextLong] ])

  (:import (javax.mail.internet MimeMessage))
  (:import (org.apache.commons.io IOUtils))
  (:import (javax.jms Message))
  (:import (java.net Socket))
  (:import (java.io File))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (com.zotohlabs.frwk.core Identifiable))
  (:import (com.zotohlabs.gallifrey.io IOResult IOEvent Emitter)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti EveSetSession "" (fn [a b] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti EveSetResult "" (fn [a b] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti EveDestroy "" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeEvent ""

  [src evtId]

  (let [ eeid (NextLong)
         impl (MakeMMap) ]
    (with-meta
      (reify

        MubleAPI

        (setf! [_ k v] (.setf! impl k v) )
        (seq* [_] (.seq* impl))
        (getf [_ k] (.getf impl k) )
        (clrf! [_ k] (.clrf! impl k) )
        (clear! [_] (.clear! impl))

        IOEvent

        (emitter [_] src) )

      { :typeid evtId }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn EveUnbind ""

  [^comzotohlabscljc.util.core.MubleAPI ev]

  (.setf! ev :waitHolder nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn EveBind ""

  [^comzotohlabscljc.util.core.MubleAPI ev obj]

  (.setf! ev :waitHolder obj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod EveSetSession :czc.tardis.io/EmEvent

  [^comzotohlabscljc.util.core.MubleAPI obj ss]

  (.setf! obj :session ss)
  obj)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod EveDestroy :czc.tardis.io/EmEvent

  [^comzotohlabscljc.util.core.MubleAPI obj]

  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod EveSetResult :czc.tardis.io/EmEvent

  [^IOEvent obj ^IOResult res]

  (let [ ^comzotohlabscljc.tardis.io.core.WaitEventHolder
         weh (.getf ^comzotohlabscljc.util.core.MubleAPI obj :waitEventHolder)
         ss (.getSession obj)
         src (.emitter obj) ]
    (when-not (nil? ss) (.handleResult ss obj res))
    (.setf! ^comzotohlabscljc.util.core.MubleAPI obj :result res)
    (when-not (nil? weh)
      (try
        (.resumeOnResult weh res)
        (finally
          (.setf! ^comzotohlabscljc.util.core.MubleAPI obj :waitEventHolder nil)
          (.release ^comzotohlabscljc.tardis.io.core.EmitterAPI src weh))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod EveDestroy :czc.tardis.io/SocketEvent

  [^comzotohlabscljc.util.core.MubleAPI obj]

  (let [ ^Socket s (.getf obj :socket) ]
    (.setf! obj :socket nil)
    (IOUtils/closeQuietly s)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(derive :czc.tardis.io/WebSockEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/SocketEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/TimerEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/JMSEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/EmailEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/FileEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/HTTPEvent :czc.tardis.io/EmEvent)
;;(derive :czc.tardis.io/MVCEvent :czc.tardis.io/HTTPEvent)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private events-eof nil)

