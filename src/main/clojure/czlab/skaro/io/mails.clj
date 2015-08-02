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

(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.io.mails

  (:require [czlab.xlib.util.core
             :refer
             [NextLong
              spos?
              ThrowIOE
              tryc
              notnil?]]
            [czlab.xlib.crypto.codec :refer [Pwdify]]
            [czlab.xlib.util.str :refer [hgl? nsb]])

  (:require [clojure.tools.logging :as log])

  (:use [czlab.skaro.core.sys]
        [czlab.skaro.io.loops ]
        [czlab.skaro.io.core ])

  (:import  [javax.mail Flags Flags$Flag
             Store Folder
             Session Provider Provider$Type]
            [java.util Properties]
            [javax.mail.internet MimeMessage]
            [java.io IOException]
            [com.zotohlab.skaro.io EmailEvent]
            [com.zotohlab.frwk.core Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeFolder ""

  [^Folder fd]

  (tryc
    (when-not (nil? fd)
      (when (.isOpen fd) (.close fd true)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeStore ""

  [^czlab.xlib.util.core.Muble co]

  (let [^Store conn (.getf co :store)
        ^Folder fd (.getf co :folder) ]
    (closeFolder fd)
    (tryc
      (when-not (nil? conn) (.close conn)) )
    (.setf! co :store nil)
    (.setf! co :folder nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resolve-provider ""

  [^czlab.xlib.util.core.Muble co
   protos
   ^String demo ^String mock]

  (let [[^String pkey ^String sn]  protos
        props (doto (Properties.)
                (.put  "mail.store.protocol" sn) )
        session (Session/getInstance props nil)
        ps (.getProviders session) ]
    (with-local-vars [proto sn sun nil]
      (if (hgl? demo)
        (do
          (var-set sun (Provider. Provider$Type/STORE
                                  mock demo "test" "1.0.0"))
          (log/debug "Using demo store " mock " !!!")
          (var-set proto mock))
        (do
          (var-set sun (some #(if (= pkey (.getClassName ^Provider %))
                                 %
                                 nil)
                             (seq ps)))
          (when (nil? @sun)
            (ThrowIOE (str "Failed to find store: " pkey) ))
          ))
      (.setProvider session @sun)
      (.setf! co :proto @proto)
      (.setf! co :session session))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ctor-email-event ""

  [co msg]

  (let [eeid (NextLong) ]
    (with-meta
      (reify

        Identifiable
        (id [_] eeid)

        EmailEvent
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (checkAuthenticity [_] false)
        (emitter [_] co)
        (getMsg [_] msg))

      { :typeid :czc.skaro.io/EmailEvent }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; POP3
(def ST_POP3S  "com.sun.mail.pop3.POP3SSLStore" )
(def ST_POP3  "com.sun.mail.pop3.POP3Store")
(def POP3_MOCK "demopop3s")
(def POP3S "pop3s")
(def POP3C "pop3")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.skaro.io/POP3

  [co & args]

  (log/info "IOESReifyEvent: POP3: " (.id ^Identifiable co))
  (ctor-email-event co (first args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- connectPop3 ""

  [^czlab.xlib.util.core.Muble co]

  (let [^Session session (.getf co :session)
        cfg (.getf co :emcfg)
        pwd (nsb (:passwd cfg))
        user (:user cfg)
        ^String host (:host cfg)
        ^long port (:port cfg)
        ^String proto (.getf co :proto)
        s (.getStore session proto) ]
    (when-not (nil? s)
      (.connect s host port user (if (hgl? pwd)
                                   pwd
                                   nil))
      (.setf! co :store s)
      (.setf! co :folder (.getDefaultFolder s)))
    (when-let [^Folder fd (.getf co :folder) ]
      (.setf! co :folder (.getFolder fd "INBOX")))
    (let [^Folder fd (.getf co :folder) ]
      (when (or (nil? fd)
                (not (.exists fd)))
        (ThrowIOE "cannot find inbox.")) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readPop3 ""

  [^czlab.skaro.io.core.EmitAPI co msgs]

  (let [^czlab.xlib.util.core.Muble src co]
    (doseq [^MimeMessage mm (seq msgs) ]
      (try
        (doto mm (.getAllHeaders)(.getContent))
        (.dispatch co (IOESReifyEvent co mm) {} )
        (finally
          (when (.getf src :deleteMsg)
            (.setFlag mm Flags$Flag/DELETED true)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanPop3 ""

  [^czlab.xlib.util.core.Muble co]

  (let [^Folder fd (.getf co :folder)
        ^Store s (.getf co :store) ]
    (when (and (notnil? fd)
               (not (.isOpen fd)))
      (.open fd Folder/READ_WRITE) )
    (when (.isOpen fd)
      (let [cnt (.getMessageCount fd) ]
        (log/debug "Count of new mail-messages: " cnt)
        (when (> cnt 0)
          (readPop3 co (.getMessages fd)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableOneLoop :czc.skaro.io/POP3

  [^czlab.xlib.util.core.Muble co]

  (try
    (connectPop3 co)
    (scanPop3 co)
    (catch Throwable e#
      (log/warn e# ""))
    (finally
      (closeStore co))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stdConfig ""

  [^czlab.xlib.util.core.Muble co cfg]

  (let [intv (:intervalSecs cfg)
        port (:port cfg)
        pkey (:app.pkey cfg)
        pwd (:passwd cfg) ]
    (with-local-vars [cpy (transient cfg)]
      (var-set cpy (assoc! @cpy :intervalMillis
                           (* 1000 (if (spos? intv) intv 300))))
      (var-set cpy (assoc! @cpy :ssl
                           (if (false? (:ssl cfg)) false true)))
      (var-set cpy (assoc! @cpy :deleteMsg
                           (true? (:deleteMsg cfg))))
      (var-set cpy (assoc! @cpy :host (nsb (:host cfg))))
      (var-set cpy (assoc! @cpy :port (if (spos? port) port 995)))
      (var-set cpy (assoc! @cpy :user (nsb (:user cfg))))
      (var-set cpy (assoc! @cpy :passwd
                           (Pwdify (if (nil? pwd) nil pwd) pkey) ))
      (-> (persistent! @cpy)
          (dissoc :intervalSecs)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/POP3

  [^czlab.xlib.util.core.Muble co cfg0]

  (log/info "CompConfigure: POP3: " (.id ^Identifiable co))
  (let [demo (System/getProperty "skaro.demo.pop3" "")
        cfg (merge (.getf co :dftOptions) cfg0)
        c2 (stdConfig co cfg) ]
    (.setf! co :emcfg c2)
    (resolve-provider co
                      (if (:ssl c2)
                        [ST_POP3S POP3S]
                        [ST_POP3 POP3C])
                      demo
                      POP3_MOCK)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; IMAP
(def ST_IMAPS "com.sun.mail.imap.IMAPSSLStore" )
(def ST_IMAP "com.sun.mail.imap.IMAPStore" )
(def IMAP_MOCK "demoimaps")
(def IMAPS "imaps" )
(def IMAP "imap" )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.skaro.io/IMAP

  [co & args]

  (log/info "IOESReifyEvent: IMAP: " (.id ^Identifiable co))
  (ctor-email-event co (first args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- connect-imap ""

  [co]

  (connectPop3 co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- read-imap ""

  [co msgs]

  (readPop3 co msgs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scan-imap ""

  [co]

  (scanPop3 co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableOneLoop :czc.skaro.io/IMAP

  [^czlab.xlib.util.core.Muble co]

  (try
    (connect-imap co)
    (scan-imap co)
    (catch Throwable e#
      (log/warn e# ""))
    (finally
      (closeStore co))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/IMAP

  [^czlab.xlib.util.core.Muble co cfg0]

  (log/info "CompConfigure: IMAP: " (.id ^Identifiable co))
  (let [demo (System/getProperty "skaro.demo.imap" "")
        cfg (merge (.getf co :dftOptions) cfg0)
        c2 (stdConfig co cfg) ]
    (.setf! co :emcfg c2)
    (resolve-provider co
                      (if (:ssl c2)
                        [ST_IMAPS IMAPS]
                        [ST_IMAP IMAP])
                      demo
                      IMAP_MOCK)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

