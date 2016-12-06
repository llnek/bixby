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

(ns ^{:doc "Implementation for email services."
      :author "Kenneth Leung"}

  czlab.wabbit.io.mails

  (:require [czlab.twisty.codec :refer [passwd<>]]
            [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.io.loops]
        [czlab.wabbit.sys.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wabbit.io.core])

  (:import [czlab.wabbit.io IoService EmailEvent]
           [javax.mail.internet MimeMessage]
           [javax.mail
            Flags$Flag
            Flags
            Store
            Folder
            Session
            Provider
            Provider$Type]
           [czlab.wabbit.server Container]
           [java.util Properties]
           [java.io IOException]
           [czlab.xlib Muble Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(derive ::EMAIL :czlab.wabbit.io.loops/ThreadedTimer)
(derive ::IMAP ::EMAIL)
(derive ::POP3 ::EMAIL)
(def ^:dynamic ^String *mock-mail-provider* "")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; POP3
(def CZ_POP3S  "com.sun.mail.pop3.POP3SSLStore")
(def CZ_POP3  "com.sun.mail.pop3.POP3Store")
(def POP3S "pop3s")
(def POP3C "pop3")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; IMAP
(def CZ_IMAPS "com.sun.mail.imap.IMAPSSLStore")
(def CZ_IMAP "com.sun.mail.imap.IMAPStore")
(def IMAPS "imaps")
(def IMAP "imap")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeFolder
  ""
  [^Folder fd]
  (if (some? fd)
    (try!
      (if (.isOpen fd) (.close fd true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeStore
  ""
  [^IoService co]
  (let [{:keys [store folder]}
        (.impl (.getx co))]
    (closeFolder folder)
    (if (some? store)
      (try!
        (.close ^Store store)))
    (doto (.getx co)
      (.unsetv :store)
      (.unsetv :folder))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resolveProvider
  ""
  [^IoService co ^String cz ^String proto]
  (let
    [demo? (= "true" (sysProp "wabbit.demo.flag"))
     ss (-> (doto (Properties.)
              (.put  "mail.store.protocol" proto))
            (Session/getInstance nil))
     ps (.getProviders ss)
     [^Provider sun ^String pz ^String proto]
     (if demo?
       [(Provider. Provider$Type/STORE
                   *mock-mail-provider*
                   POP3S "test" "1")
        *mock-mail-provider* POP3S]
       [(some #(if (= cz (.getClassName ^Provider %)) %)
              (seq ps))
        cz proto])]
    (if (nil? sun)
      (throwIOE (str "Failed to find store: " pz)))
    (log/debug "mail store impl = '%s'" pz)
    (.setProvider ss sun)
    (doto (.getx co)
      (.setv :proto proto)
      (.setv :session ss))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- emailEvent<>
  ""
  [^IoService co msg]
  (let [eeid (str "event#" (seqint2))]
    (with-meta
      (reify EmailEvent
        (checkAuthenticity [_] false)
        (id [_] eeid)
        (source [_] co)
        (message [_] msg))
      {:typeid ::EmailEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>
  ::EMAIL
  [^IoService co {:keys [msg]}]

  (emailEvent<> co msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- connectPop3
  ""
  [^IoService co]
  (let [{:keys [^Session session
                ^String proto]}
        (.impl (.getx co))
        {:keys [^String host
                port
                ^String user
                ^String passwd]}
        (.config co)]
    (when-some [s (.getStore session proto)]
      (.connect s
                host
                ^long port
                user
                (stror passwd nil))
      (doto (.getx co)
        (.setv :folder
               (some-> (.getDefaultFolder s)
                       (.getFolder "INBOX")))
        (.setv :store s))
      (let [fd (.getv (.getx co) :folder)]
        (when (or (nil? fd)
                  (not (.exists ^Folder fd)))
          (.unsetv (.getx co) :store)
          (try! (.close s))
          (throwIOE "cannot find inbox"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readPop3
  ""
  [^IoService co msgs]
  (let [d? (.getv (.getx co) :deleteMsg?)]
    (doseq [^MimeMessage mm  msgs]
      (doto mm
        (.getAllHeaders)
        (.getContent))
      (.dispatch co (ioevent<> co {:msg mm}))
      (when d? (.setFlag mm Flags$Flag/DELETED true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanPop3
  ""
  [^IoService co]
  (let [{:keys [^Folder folder
                ^Store store]}
        (.impl (.getx co))]
    (if (and (some? folder)
             (not (.isOpen folder)))
      (.open folder Folder/READ_WRITE))
    (when (and (some? folder)
               (.isOpen folder))
      (try
        (let [cnt (.getMessageCount folder)]
          (log/debug "count of new mail-messages: %d" cnt)
          (if (spos? cnt)
            (readPop3 co (.getMessages folder))))
        (finally
          (try! (.close folder true)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup
  ::POP3
  [^IoService co _]

  (try
    (connectPop3 co)
    (scanPop3 co)
    (catch Throwable e#
      (log/exception e#))
    (finally
      (closeStore co)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sanitize
  ""
  [^IoService co cfg0]

  (let [{:keys [port deleteMsg?
                host user ssl? passwd]}
        cfg0
        pkey (.podKey (.server co))]
    (-> cfg0
        (assoc :ssl (if (false? ssl?) false true))
        (assoc :deleteMsg (true? deleteMsg?))
        (assoc :host (str host))
        (assoc :port (if (spos? port) port 995))
        (assoc :user (str user ))
        (assoc :passwd (.text (passwd<> passwd pkey))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init
  ::POP3
  [^IoService co cfg0]

  (logcomp "comp->init" co)
  (let [c2 (merge (.config co)
                  (sanitize cfg0))
        [z p]
        (if (:ssl? c2)
          [CZ_POP3S POP3S] [CZ_POP3 POP3C])]
    (.setv (.getx co) :emcfg c2)
    (resolveProvider co z p)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ^:private connectIMAP "" [co] `(connectPop3 ~co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ^:private scanIMAP "" [co] `(scanPop3 ~co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup
  ::IMAP
  [^IoService co _]

  (try
    (connectIMAP co)
    (scanIMAP co)
    (catch Throwable e#
      (log/exception e#))
    (finally
      (closeStore co)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init
  ::IMAP
  [^IoService co cfg0]

  (logcomp "comp->init" co)
  (let [c2 (merge (.config co)
                  (sanitize cfg0))
        [z p]
        (if (:ssl? c2)
          [CZ_IMAPS IMAPS] [CZ_IMAP IMAP])]
    (.setv (.getx co) :emcfg c2)
    (resolveProvider co z p)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


