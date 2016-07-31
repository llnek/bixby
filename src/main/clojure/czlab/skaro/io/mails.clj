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
      :author "Kenneth Leung" }

  czlab.skaro.io.mails

  (:require
    [czlab.crypto.codec :refer [passwd<>]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.core
     :refer [seqint2
             spos?
             try!
             throwIOE]]
    [czlab.xlib.str :refer [hgl? ]])

  (:use [czlab.skaro.core.sys]
        [czlab.skaro.io.loops ]
        [czlab.skaro.io.core ])

  (:import
    [javax.mail.internet MimeMessage]
    [javax.mail
     Flags$Flag
     Flags
     Store
     Folder
     Session
     Provider
     Provider$Type]
    [czlab.server EventEmitter]
    [java.util Properties]
    [java.io IOException]
    [czlab.skaro.io EmailEvent]
    [czlab.xlib Muble Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeFolder

  ""
  [^Folder fd]

  (when (some? fd)
    (try!
      (when (.isOpen fd) (.close fd true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeStore

  ""
  [^Context co]

  (let [conn (.getv (.getx co) :store)
        fd (.getv (.getx co) :folder) ]
    (closeFolder fd)
    (when (some? conn)
      (try!
        (.close ^Store conn)))
    (doto (.getx co)
      (.unsetv :store)
      (.unsetv :folder))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resolveProvider

  ""
  [^Context co ^String cz ^String proto]

  (let
    [demo (hgl? (System/getProperty "skaro.demo.flag" ""))
     ss (-> (doto (Properties.)
              (.put  "mail.store.protocol" proto))
              (Session/getInstance nil))
     ps (.getProviders ss)
     [^Provider sun ^String proto]
     (if demo?
       [(Provider. Provider$Type/STORE
                   *mock-mail-provider*
                   "yo" "test" "1") "yo"]
       [(some #(if (= cz (.getClassName ^Provider %)) %)
              (seq ps))
        proto])]
    (when (nil? sun)
      (throwIOE (str "Failed to find store: " cz)))
    (log/debug "using store %s!!!" sun)
    (.setProvider ss sun)
    (doto (.getx co)
      (.setv :proto proto)
      (.setv :session ss))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ctorEmailEvent

  ""
  [^EventEmitter co msg]

  (let [eeid (seqint2) ]
    (with-meta
      (reify

        Identifiable
        (id [_] eeid)

        EmailEvent
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (id [_] eeid)
        (checkAuthenticity [_] false)
        (emitter [_] co)
        (getMsg [_] msg))

      {:typeid ::EmailEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; POP3
(def ST_POP3S  "com.sun.mail.pop3.POP3SSLStore" )
(def ST_POP3  "com.sun.mail.pop3.POP3Store")
(def POP3_MOCK "demopop3s")
(def POP3S "pop3s")
(def POP3C "pop3")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>

  ::POP3
  [co & args]

  (log/info "ioevent: POP3: %s" (.id ^Identifiable co))
  (ctorEmailEvent co (first args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- connectPop3

  ""
  [^Context co]

  (let [{:keys [^Session session
                emcfg
                ^String proto]}
        (.impl (.getx co))
        {:keys [^String host
                port
                ^String user
                ^String passwd]}
        emcfg]
    (when-some [s (.getStore session proto)]
      (.connect s
                host ^long port
                user ^String (stror pwd nil))
      (doto (.getx co)
        (.setv :folder
               (some-> (.getDefaultFolder s)
                       (.getFolder "INBOX")))
        (.setv :store s)))
    (let [fd (.getv (.getx co) :folder) ]
      (when (or (nil? fd)
                (not (.exists ^Folder fd)))
        (.unsetv (.getx co) :store)
        (try! (.close s))
        (throwIOE "cannot find inbox")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readPop3

  ""
  [^Context co msgs]

  (let [d (.getv (.getx co) :deleteMsg)]
    (doseq [^MimeMessage mm  msgs]
      (doto mm
        (.getAllHeaders)
        (.getContent))
      (.dispatch ^EventEmitter co (ioevent<> co mm) nil)
      (when d (.setFlag mm Flags$Flag/DELETED true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanPop3

  ""
  [^Context co]

  (let [{:keys [^Folder folder
                ^Store store]}
        (.impl (.getx co))]
    (when (and (some? folder)
               (not (.isOpen folder)))
      (.open folder Folder/READ_WRITE))
    (when (.isOpen folder)
      (try
        (let [cnt (.getMessageCount folder)]
          (log/debug "count of new mail-messages: %d" cnt)
          (when (> cnt 0)
            (readPop3 co (.getMessages folder))))
        (finally
          (try! (.close folder)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableOneLoop ::POP3

  [^Context co & args]

  (try
    (connectPop3 co)
    (scanPop3 co)
    (catch Throwable e#
      (log/warn e# ""))
    (finally
      (closeStore co)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stdConfig

  ""
  [^Context co cfg]

  (let [{:keys [intervalSecs port deleteMsg
                host user ssl appkey passwd]}
        cfg]
    (with-local-vars [cpy (transient cfg)]
      (var-set cpy
               (assoc! @cpy :intervalMillis
                       (* 1000 (if (spos? intervalSecs)
                                 intervalSecs 300))))
      (var-set cpy
               (assoc! @cpy :ssl
                       (if (false? ssl) false true)))
      (var-set cpy
               (assoc! @cpy :deleteMsg
                       (true? deleteMsg)))
      (var-set cpy (assoc! @cpy :host (str host)))
      (var-set cpy (assoc! @cpy :port
                           (if (spos? port) port 995)))
      (var-set cpy (assoc! @cpy :user (str user )))
      (var-set cpy (assoc! @cpy :passwd
                           (.text (passwd<> passwd pkey))))
      (-> (persistent! @cpy)
          (dissoc :intervalSecs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->configure

  ::POP3
  [^Context co cfg0]

  (log/info "comp->configure: POP3: %s" (.id ^Identifiable co))
  (let [c2 (->> (merge (.getv (.getx co)
                          :dftOptions) cfg0)
                (stdConfig co))]
    (.setv (.getx co) :emcfg c2)
    (apply resolveProvider
           co
           (if (:ssl c2)
             [ST_POP3S POP3S]
             [ST_POP3 POP3C]))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; IMAP
(def ST_IMAPS "com.sun.mail.imap.IMAPSSLStore" )
(def ST_IMAP "com.sun.mail.imap.IMAPStore" )
(def IMAP_MOCK "demoimaps")
(def IMAPS "imaps" )
(def IMAP "imap" )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent

  ::IMAP
  [co & args]

  (log/info "ioevent: IMAP: %s" (.id ^Identifiable co))
  (ctorEmailEvent co (first args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro connectIMAP
  ""
  {:private true}
  [co]
  `(connectPop3 ~co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro scanIMAP
  ""
  {:private true}
  [co]
  `(scanPop3 ~co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableOneLoop

  ::IMAP
  [^Context co & args]

  (try
    (connectIMAP co)
    (scanIMAP co)
    (catch Throwable e#
      (log/warn e# ""))
    (finally
      (closeStore co)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->configure

  ::IMAP
  [^Muble co cfg0]

  (log/info "comp->configure: IMAP: %s" (.id ^Identifiable co))
  (let [c2 (->> (merge (.getv (.getx co)
                              :dftOptions) cfg0)
                (stdConfig co))]
    (.setv (.getx co) :emcfg c2)
    (apply resolveProvider
           co
           (if (:ssl c2)
             [ST_IMAPS IMAPS]
             [ST_IMAP IMAP]))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


