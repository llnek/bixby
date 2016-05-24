;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.


(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.io.mails

  (:require
    [czlab.crypto.codec :refer [pwdify]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.core
     :refer [nextLong
             spos?
             throwIOE
             tryc ]]
    [czlab.xlib.str :refer [hgl? ]])

  (:use [czlab.skaro.core.sys]
        [czlab.skaro.io.loops ]
        [czlab.skaro.io.core ])

  (:import
    [javax.mail.internet MimeMessage]
    [javax.mail Flags Flags$Flag
     Store Folder
     Session
     Provider
     Provider$Type]
    [czlab.wflow.server Emitter]
    [java.util Properties]
    [java.io IOException]
    [czlab.skaro.io EmailEvent]
    [czlab.xlib Muble Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeFolder ""

  [^Folder fd]

  (tryc
    (when (some? fd)
      (when (.isOpen fd) (.close fd true)))))

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
    (.setv co :folder nil)))

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
            (throwIOE (str "Failed to find store: " pkey) ))))
      (.setProvider session @sun)
      (.setv co :proto @proto)
      (.setv co :session session))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ctor-email-event ""

  [co msg]

  (let [eeid (nextLong) ]
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

      {:typeid :czc.skaro.io/EmailEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; POP3
(defonce ST_POP3S  "com.sun.mail.pop3.POP3SSLStore" )
(defonce ST_POP3  "com.sun.mail.pop3.POP3Store")
(defonce POP3_MOCK "demopop3s")
(defonce POP3S "pop3s")
(defonce POP3C "pop3")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioReifyEvent :czc.skaro.io/POP3

  [co & args]

  (log/info "ioReifyEvent: POP3: %s" (.id ^Identifiable co))
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
    (when-some [^Folder fd (.getv co :folder) ]
      (.setv co :folder (.getFolder fd "INBOX")))
    (let [^Folder fd (.getv co :folder) ]
      (when (or (nil? fd)
                (not (.exists fd)))
        (throwIOE "cannot find inbox.")) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readPop3 ""

  [^Emitter co msgs]

  (let [^Muble src co]
    (doseq [^MimeMessage mm  msgs]
      (try
        (doto mm
          (.getAllHeaders)
          (.getContent))
        (.dispatch co (ioReifyEvent co mm) {} )
        (finally
          (when (.getv src :deleteMsg)
            (.setFlag mm Flags$Flag/DELETED true)))))))

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
          (readPop3 co (.getMessages fd)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableOneLoop :czc.skaro.io/POP3

  [^Muble co & args]

  (try
    (connectPop3 co)
    (scanPop3 co)
    (catch Throwable e#
      (log/warn e# ""))
    (finally
      (closeStore co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stdConfig ""

  [^Muble co cfg]

  (let [intv (:intervalSecs cfg)
        port (:port cfg)
        pkey (:appkey cfg)
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
                           (pwdify (if (nil? pwd) nil pwd) pkey) ))
      (-> (persistent! @cpy)
          (dissoc :intervalSecs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compConfigure :czc.skaro.io/POP3

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
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; IMAP
(defonce ST_IMAPS "com.sun.mail.imap.IMAPSSLStore" )
(defonce ST_IMAP "com.sun.mail.imap.IMAPStore" )
(defonce IMAP_MOCK "demoimaps")
(defonce IMAPS "imaps" )
(defonce IMAP "imap" )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioReifyEvent :czc.skaro.io/IMAP

  [co & args]

  (log/info "ioReifyEvent: IMAP: %s" (.id ^Identifiable co))
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
(defmethod loopableOneLoop :czc.skaro.io/IMAP

  [^Muble co & args]

  (try
    (connect-imap co)
    (scan-imap co)
    (catch Throwable e#
      (log/warn e# ""))
    (finally
      (closeStore co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compConfigure :czc.skaro.io/IMAP

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
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


