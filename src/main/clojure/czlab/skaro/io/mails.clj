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

  (:require
    [czlab.xlib.util.core
    :refer [NextLong spos? ThrowIOE tryc notnil?]]
    [czlab.xlib.crypto.codec :refer [Pwdify]]
    [czlab.xlib.util.str :refer [hgl? ]])

  (:require
    [czlab.xlib.util.logging :as log])

  (:use [czlab.skaro.core.sys]
        [czlab.skaro.io.loops ]
        [czlab.skaro.io.core ])

  (:import
    [javax.mail Flags Flags$Flag
    Store Folder
    Session Provider Provider$Type]
    [java.util Properties]
    [javax.mail.internet MimeMessage]
    [java.io IOException]
    [com.zotohlab.skaro.core Muble]
    [com.zotohlab.skaro.io EmailEvent]
    [com.zotohlab.frwk.core Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeFolder ""

  [^Folder fd]

  (tryc
    (when (some? fd)
      (when (.isOpen fd) (.close fd true)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeStore ""

  [^Muble co]

  (let [^Store conn (.getv co :store)
        ^Folder fd (.getv co :folder) ]
    (closeFolder fd)
    (tryc
      (when (some? conn) (.close conn)) )
    (.setv co :store nil)
    (.setv co :folder nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resolve-provider ""

  [^Muble co
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
          (log/debug "using demo store %s!!!" mock)
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
      (.setv co :proto @proto)
      (.setv co :session session))
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
(defonce ST_POP3S  "com.sun.mail.pop3.POP3SSLStore" )
(defonce ST_POP3  "com.sun.mail.pop3.POP3Store")
(defonce POP3_MOCK "demopop3s")
(defonce POP3S "pop3s")
(defonce POP3C "pop3")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.skaro.io/POP3

  [co & args]

  (log/info "IOESReifyEvent: POP3: %s" (.id ^Identifiable co))
  (ctor-email-event co (first args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- connectPop3 ""

  [^Muble co]

  (let [^Session session (.getv co :session)
        cfg (.getv co :emcfg)
        pwd (str (:passwd cfg))
        user (:user cfg)
        ^String host (:host cfg)
        ^long port (:port cfg)
        ^String proto (.getv co :proto)
        s (.getStore session proto) ]
    (when (some? s)
      (.connect s host port user (if (hgl? pwd)
                                   pwd
                                   nil))
      (.setv co :store s)
      (.setv co :folder (.getDefaultFolder s)))
    (when-let [^Folder fd (.getv co :folder) ]
      (.setv co :folder (.getFolder fd "INBOX")))
    (let [^Folder fd (.getv co :folder) ]
      (when (or (nil? fd)
                (not (.exists fd)))
        (ThrowIOE "cannot find inbox.")) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readPop3 ""

  [^czlab.skaro.io.core.EmitAPI co msgs]

  (let [^Muble src co]
    (doseq [^MimeMessage mm (seq msgs) ]
      (try
        (doto mm (.getAllHeaders)(.getContent))
        (.dispatch co (IOESReifyEvent co mm) {} )
        (finally
          (when (.getv src :deleteMsg)
            (.setFlag mm Flags$Flag/DELETED true)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanPop3 ""

  [^Muble co]

  (let [^Folder fd (.getv co :folder)
        ^Store s (.getv co :store) ]
    (when (and (some? fd)
               (not (.isOpen fd)))
      (.open fd Folder/READ_WRITE) )
    (when (.isOpen fd)
      (let [cnt (.getMessageCount fd) ]
        (log/debug "count of new mail-messages: %s" cnt)
        (when (> cnt 0)
          (readPop3 co (.getMessages fd)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableOneLoop :czc.skaro.io/POP3

  [^Muble co]

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

  [^Muble co cfg]

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
      (var-set cpy (assoc! @cpy :host (str (:host cfg))))
      (var-set cpy (assoc! @cpy :port (if (spos? port) port 995)))
      (var-set cpy (assoc! @cpy :user (str (:user cfg))))
      (var-set cpy (assoc! @cpy :passwd
                           (Pwdify (if (nil? pwd) nil pwd) pkey) ))
      (-> (persistent! @cpy)
          (dissoc :intervalSecs)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/POP3

  [^Muble co cfg0]

  (log/info "compConfigure: POP3: %s" (.id ^Identifiable co))
  (let [demo (System/getProperty "skaro.demo.pop3" "")
        cfg (merge (.getv co :dftOptions) cfg0)
        c2 (stdConfig co cfg) ]
    (.setv co :emcfg c2)
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
(defonce ST_IMAPS "com.sun.mail.imap.IMAPSSLStore" )
(defonce ST_IMAP "com.sun.mail.imap.IMAPStore" )
(defonce IMAP_MOCK "demoimaps")
(defonce IMAPS "imaps" )
(defonce IMAP "imap" )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.skaro.io/IMAP

  [co & args]

  (log/info "IOESReifyEvent: IMAP: %s" (.id ^Identifiable co))
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

  [^Muble co]

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

  [^Muble co cfg0]

  (log/info "compConfigure: IMAP: %s" (.id ^Identifiable co))
  (let [demo (System/getProperty "skaro.demo.imap" "")
        cfg (merge (.getv co :dftOptions) cfg0)
        c2 (stdConfig co cfg) ]
    (.setv co :emcfg c2)
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

