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

  czlabclj.tardis.io.mails

  (:require [czlabclj.xlib.util.core
             :refer
             [NextLong
              spos?
              ThrowIOE
              tryc
              notnil?]]
            [czlabclj.xlib.crypto.codec :refer [Pwdify]]
            [czlabclj.xlib.util.str :refer [hgl? nsb]])

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.tardis.core.sys]
        [czlabclj.tardis.io.loops ]
        [czlabclj.tardis.io.core ])

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

  [^czlabclj.tardis.core.sys.Elmt co]

  (let [^Store conn (.getAttr co :store)
        ^Folder fd (.getAttr co :folder) ]
    (closeFolder fd)
    (tryc
      (when-not (nil? conn) (.close conn)) )
    (.setAttr! co :store nil)
    (.setAttr! co :folder nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resolve-provider ""

  [^czlabclj.tardis.core.sys.Elmt co
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
      (.setAttr! co :proto @proto)
      (.setAttr! co :session session))
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

      { :typeid :czc.tardis.io/EmailEvent }
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
(defmethod IOESReifyEvent :czc.tardis.io/POP3

  [co & args]

  (log/info "IOESReifyEvent: POP3: " (.id ^Identifiable co))
  (ctor-email-event co (first args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- connectPop3 ""

  [^czlabclj.tardis.core.sys.Elmt co]

  (let [^Session session (.getAttr co :session)
        cfg (.getAttr co :emcfg)
        pwd (nsb (:passwd cfg))
        user (:user cfg)
        ^String host (:host cfg)
        ^long port (:port cfg)
        ^String proto (.getAttr co :proto)
        s (.getStore session proto) ]
    (when-not (nil? s)
      (.connect s host port user (if (hgl? pwd)
                                   pwd
                                   nil))
      (.setAttr! co :store s)
      (.setAttr! co :folder (.getDefaultFolder s)))
    (when-let [^Folder fd (.getAttr co :folder) ]
      (.setAttr! co :folder (.getFolder fd "INBOX")))
    (let [^Folder fd (.getAttr co :folder) ]
      (when (or (nil? fd)
                (not (.exists fd)))
        (ThrowIOE "cannot find inbox.")) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readPop3 ""

  [^czlabclj.tardis.io.core.EmitAPI co msgs]

  (let [^czlabclj.tardis.core.sys.Elmt src co]
    (doseq [^MimeMessage mm (seq msgs) ]
      (try
        (doto mm (.getAllHeaders)(.getContent))
        (.dispatch co (IOESReifyEvent co mm) {} )
        (finally
          (when (.getAttr src :deleteMsg)
            (.setFlag mm Flags$Flag/DELETED true)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanPop3 ""

  [^czlabclj.tardis.core.sys.Elmt co]

  (let [^Folder fd (.getAttr co :folder)
        ^Store s (.getAttr co :store) ]
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
(defmethod LoopableOneLoop :czc.tardis.io/POP3

  [^czlabclj.tardis.core.sys.Elmt co]

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

  [^czlabclj.tardis.core.sys.Elmt co cfg]

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
(defmethod CompConfigure :czc.tardis.io/POP3

  [^czlabclj.tardis.core.sys.Elmt co cfg0]

  (log/info "CompConfigure: POP3: " (.id ^Identifiable co))
  (let [demo (System/getProperty "skaro.demo.pop3" "")
        cfg (merge (.getAttr co :dftOptions) cfg0)
        c2 (stdConfig co cfg) ]
    (.setAttr! co :emcfg c2)
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
(defmethod IOESReifyEvent :czc.tardis.io/IMAP

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
(defmethod LoopableOneLoop :czc.tardis.io/IMAP

  [^czlabclj.tardis.core.sys.Elmt co]

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
(defmethod CompConfigure :czc.tardis.io/IMAP

  [^czlabclj.tardis.core.sys.Elmt co cfg0]

  (log/info "CompConfigure: IMAP: " (.id ^Identifiable co))
  (let [demo (System/getProperty "skaro.demo.imap" "")
        cfg (merge (.getAttr co :dftOptions) cfg0)
        c2 (stdConfig co cfg) ]
    (.setAttr! co :emcfg c2)
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

