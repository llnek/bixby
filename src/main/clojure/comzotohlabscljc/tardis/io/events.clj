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

  comzotohlabscljc.tardis.io.events )

(import '(org.apache.commons.io IOUtils))
(import '(javax.mail.internet MimeMessage))
(import '(javax.jms Message))
(import '(java.net Socket))
(import '(java.io File))
(import '(com.zotohlabs.frwk.io XData))
(import '(com.zotohlabs.frwk.core Identifiable))
(import '(com.zotohlabs.gallifrey.io IOResult IOEvent Emitter))

(use '[clojure.tools.logging :only [info warn error debug] ])
(use '[comzotohlabscljc.util.core :only [MuObj make-mmap] ])
(use '[comzotohlabscljc.tardis.io.core])
(use '[comzotohlabscljc.util.seqnum :only [next-long] ])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defmulti eve-set-session "" (fn [a b] (:typeid (meta a))))
(defmulti eve-set-result "" (fn [a b] (:typeid (meta a))))
(defmulti eve-destroy "" (fn [a] (:typeid (meta a))))

(defn makeEvent [src evtId]
  (let [ eeid (next-long)
         impl (make-mmap) ]
    (with-meta
      (reify

        MuObj

        (setf! [_ k v] (.mm-s impl k v) )
        (seq* [_] (seq (.mm-m* impl)))
        (getf [_ k] (.mm-g impl k) )
        (clrf! [_ k] (.mm-r impl k) )
        (clear! [_] (.mm-c impl))

        IOEvent

        (emitter [_] src) )

      { :typeid evtId } )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn eve-unbind [^comzotohlabscljc.util.core.MuObj ev]
  (.setf! ev :waitHolder nil))

(defn eve-bind [^comzotohlabscljc.util.core.MuObj ev obj]
  (.setf! ev :waitHolder obj))

(defmethod eve-set-session :czc.tardis.io/EmEvent [^comzotohlabscljc.util.core.MuObj obj s]
  (do
    (.setf! obj :session s)
    obj))

(defmethod eve-destroy :czc.tardis.io/EmEvent [^comzotohlabscljc.util.core.MuObj obj] nil)

(defmethod eve-set-result :czc.tardis.io/EmEvent [^IOEvent obj ^IOResult res]
  (let [ ^comzotohlabscljc.tardis.io.core.WaitEventHolder
         weh (.getf ^comzotohlabscljc.util.core.MuObj obj :waitEventHolder)
         s (.getSession obj)
         src (.emitter obj) ]
    (when-not (nil? s) (.handleResult s obj res))
    (.setf! ^comzotohlabscljc.util.core.MuObj obj :result res)
    (when-not (nil? weh)
      (try
          (.resumeOnResult weh res)
        (finally
          (.setf! ^comzotohlabscljc.util.core.MuObj obj :waitEventHolder nil)
          (.release ^comzotohlabscljc.tardis.io.core.EmitterAPI src weh))))))


(defmethod eve-destroy :czc.tardis.io/SocketEvent [^comzotohlabscljc.util.core.MuObj obj]
  (let [ ^Socket s (.getf obj :socket) ]
    (.setf! obj :socket nil)
    (when-not (nil? s) (IOUtils/closeQuietly s))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(derive :czc.tardis.io/WebSockEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/SocketEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/TimerEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/JMSEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/EmailEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/FileEvent :czc.tardis.io/EmEvent)
(derive :czc.tardis.io/HTTPEvent :czc.tardis.io/EmEvent)
;;(derive :czc.tardis.io/MVCEvent :czc.tardis.io/HTTPEvent)

(def ^:private events-eof nil)

