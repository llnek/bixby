;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.plugs.mails

  "Implementation for email services."

  (:require [czlab.blutbad.core :as b]
            [czlab.basal.io :as i]
            [czlab.basal.core :as c]
            [czlab.basal.util :as u]
            [czlab.twisty.codec :as co]
            [czlab.blutbad.plugs.loops :as l])

  (:import [javax.mail.internet MimeMessage]
           [clojure.lang APersistentMap]
           [javax.mail
            Flags$Flag
            Flags
            Store
            Folder
            Session
            Provider
            Provider$Type]
           [java.util Properties]
           [java.io IOException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def
  ^:dynamic
  *mock-mail-provider*
  {:pop3s "czlab.blutbad.mock.mail.MockPop3SSLStore"
   :imaps "czlab.blutbad.mock.mail.MockIMapSSLStore"
   :pop3 "czlab.blutbad.mock.mail.MockPop3Store"
   :imap "czlab.blutbad.mock.mail.MockIMapStore"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; POP3
(c/def- cz-pop3s  "com.sun.mail.pop3.POP3SSLStore")
(c/def- cz-pop3  "com.sun.mail.pop3.POP3Store")
(c/def- pop3s "pop3s")
(c/def- pop3 "pop3")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; IMAP
(c/def- cz-imaps "com.sun.mail.imap.IMAPSSLStore")
(c/def- cz-imap "com.sun.mail.imap.IMAPStore")
(c/def- imaps "imaps")
(c/def- imap "imap")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- close-folder

  [^Folder fd]

  (and fd
       (c/try! (if (.isOpen fd) (.close fd true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- close-store

  [^Store store]

  (c/try! (some-> store .close)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord MailMsg []
  c/Idable
  (id [_] (:id _))
  c/Hierarchical
  (parent [me] (:source me)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>

  [co msg]

  (c/object<> MailMsg
              :source co
              :message msg
              :id (str "MailMsg#" (u/seqint2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- sanitize

  [pkey {:keys [port delete-msg?
                host user ssl? passwd] :as cfg0}]

  (-> cfg0
      (assoc :ssl? (c/!false? ssl?))
      (assoc :delete-msg? (true? delete-msg?))
      (assoc :host (str host))
      (assoc :port (if (c/spos? port) port 995))
      (assoc :user (str user))
      (assoc :passwd (co/pw-text (co/pwd<> passwd pkey)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init2

  [plug [cz proto :as arg]]

  (let [mockp (u/get-sys-prop "blutbad.mock.mail.proto")
        demo? (c/hgl? mockp)
        cz (name cz)
        proto (name (if demo?
                      mockp proto))
        demop ((keyword proto)
               *mock-mail-provider*)
        ss (Session/getInstance
             (doto (Properties.)
               (.put  "mail.store.protocol" proto)) nil)
        [^Provider sun ^String pz]
        (if demo?
          [(Provider. Provider$Type/STORE
                      proto demop "czlab" "1.1.7") demop]
          [(some #(if (c/eq? cz
                             (.getClassName ^Provider %)) %)
                 (.getProviders ss)) cz])]
    (u/assert-IOE (some? sun) "Failed to find store: %s" pz)
    (c/info "mail store impl = %s" sun)
    (.setProvider ss sun)
    (assoc plug :proto proto :pz pz :session ss)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord MailPlugin [server _id info conf wakerFunc]
  c/Connectable
  (disconnect [_] _)
  (connect [_]
    (c/connect _ nil))
  (connect [me _]
    (let [{:keys [proto session]} me
          {:keys [host user port passwd]} conf
          s (.getStore ^Session session ^String proto)]
      (if (nil? s)
        (c/do#nil (c/warn "failed to get session store [%s]." proto))
        (let [_ (c/debug "connecting to session store [%s]..." proto)
              _ (.connect s
                          ^String host
                          ^long port
                          ^String user
                          (c/stror (i/x->str passwd) nil))
              fd (some-> (.getDefaultFolder s)
                         (.getFolder "INBOX"))]
          (when (or (nil? fd)
                    (not (.exists fd)))
            (c/warn "bad mail store folder#%s." proto)
            (c/try! (.close s))
            (u/throw-IOE "Cannot find inbox!"))
          [s fd]))))
  c/Hierarchical
  (parent [_] server)
  c/Idable
  (id [_] _id)
  c/Initable
  (init [me arg]
    (let [{:keys [sslvars vars]} me
          pk (i/x->chars (-> me c/parent b/pkey))]
      (-> (update-in me
                     [:conf]
                     #(sanitize pk
                                (-> (c/merge+ % arg)
                                    b/expand-vars* b/prevar-cfg)))
          (init2 (if (:ssl? conf) sslvars vars)))))
  c/Finzable
  (finz [_] (c/stop _))
  c/Startable
  (stop [me]
    (l/stop-threaded-loop! (:loopy me)) me)
  (start [_]
    (c/start _ nil))
  (start [me _]
    (assoc me :loopy (l/schedule-threaded-loop me wakerFunc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- email-xxx

  [server id {:keys [info conf]} sslvars vars wakerFunc]

  (-> (MailPlugin. server id
                   info conf wakerFunc)
      (assoc :sslvars sslvars :vars vars)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def POP3Spec
  {:info {:name "POP3 Client"
          :version "1.0.0"}
   :conf {:$pluggable ::pop3<>
          :host "pop.gmail.com"
          :port 995
          :delete-msg? false
          :username "joe"
          :passwd "secret"
          :interval-secs 300
          :delay-secs 0
          :ssl? true
          :$error nil
          :$action nil}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- scan*

  [plug ^Store store ^Folder folder]

  (letfn
    [(read* [msgs]
       (let [d? (get-in plug [:conf :delete-msg?])]
         (doseq [^MimeMessage mm msgs]
           (doto mm .getAllHeaders .getContent)
           (when d?
             (.setFlag mm Flags$Flag/DELETED true))
           (b/dispatch (evt<> plug mm)))))]
    (when folder
      (if-not (.isOpen folder)
        (.open folder Folder/READ_WRITE)))
    (when (.isOpen folder)
      (try
        (let [cnt (.getMessageCount folder)]
          (c/debug "count of new mail-messages: %d." cnt)
          (if (c/spos? cnt)
            (read* (.getMessages folder))))
        (finally (c/try! (.close folder true)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- wake-pop3

  [co] (let [[store folder] (c/connect co)]
         (try (scan* co store folder)
              (catch Throwable _
                (c/exception _))
              (finally
                (close-folder folder)
                (close-store store)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- wake-imap

  [co] (let [[store folder] (c/connect co)]
         (try (scan* co store folder)
              (catch Throwable _
                (c/exception _))
              (finally
                (close-folder folder)
                (close-store store)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def IMAPSpec
  {:info {:name "IMAP Client"
          :version "1.0.0"}
   :conf {:$pluggable ::imap<>
          :host "imap.gmail.com"
          :port 993
          :delete-msg? false
          :ssl? true
          :username "joe"
          :passwd "secret"
          :interval-secs 300
          :delay-secs 0
          :$error nil
          :$action nil}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn imap<>

  ([_ id]
   (imap _ id IMAPSpec))

  ([_ id spec]
   (email-xxx _ id spec [cz-imaps imaps] [cz-imap imap] wake-imap)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn pop3<>

  ([_ id]
   (pop3<> _ id POP3Spec))

  ([_ id spec]
   (email-xxx _ id spec [cz-pop3s pop3s] [cz-pop3 pop3] wake-pop3)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

