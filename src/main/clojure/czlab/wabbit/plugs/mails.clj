;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.wabbit.plugs.mails

  "Implementation for email services."

  (:require [czlab.wabbit.core :as b]
            [czlab.wabbit.xpis :as xp]
            [czlab.basal.log :as l]
            [czlab.basal.io :as i]
            [czlab.basal.core :as c]
            [czlab.basal.util :as u]
            [czlab.basal.xpis :as po]
            [czlab.twisty.codec :as co]
            [czlab.wabbit.plugs.core :as pc]
            [czlab.wabbit.plugs.loops :as pl])

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
  {:pop3s "czlab.wabbit.mock.mail.MockPop3SSLStore"
   :imaps "czlab.wabbit.mock.mail.MockIMapSSLStore"
   :pop3 "czlab.wabbit.mock.mail.MockPop3Store"
   :imap "czlab.wabbit.mock.mail.MockIMapStore"})

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
  (if fd (c/try! (if (.isOpen fd) (.close fd true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol MailStoreAPI
  (connect-pop3 [_] "")
  (read-pop3 [_ msgs] "")
  (scan-pop3 [_] "")
  (close-store [_] "")
  (resolve-provider [_ arg] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord MailMsg []
  po/Idable
  (id [_] (:id _))
  xp/PlugletMsg
  (get-pluglet [me] (:source me)))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- pluglet

  [plug _id spec options]

  (let [impl (atom (merge {:conf (:conf spec)
                           :info (:info spec)} options))]
    (reify
      MailStoreAPI
      (resolve-provider [me arg]
        (let [mockp (u/get-sys-prop "wabbit.mock.mail.proto")
              demo? (c/hgl? mockp)
              [cz proto] arg
              proto (if demo? mockp proto)
              demop (*mock-mail-provider* (keyword proto))
              ss (Session/getInstance
                   (doto (Properties.)
                     (.put  "mail.store.protocol" proto)) nil)
              [^Provider sun ^String pz]
              (if demo?
                [(Provider. Provider$Type/STORE
                      proto demop "czlab" "1.1.7") demop]
                [(some #(if (.equals ^String cz
                                     (.getClassName ^Provider %)) %)
                       (.getProviders ss)) cz])]
          (if (nil? sun)
            (u/throw-IOE "Failed to find store: %s" pz))
          (l/info "mail store impl = %s" sun)
          (.setProvider ss sun)
          (swap! impl
                 assoc
                 :proto proto :pz pz :session ss)))
      (scan-pop3 [me]
        (let [{:keys [^Folder folder ^Store store]} @impl]
          (when folder
            (if-not (.isOpen folder)
              (.open folder Folder/READ_WRITE))
            (when (.isOpen folder)
              (try (let [cnt (.getMessageCount folder)]
                     (l/debug "count of new mail-messages: %d." cnt)
                     (if (c/spos? cnt)
                       (read-pop3 me (.getMessages folder))))
                   (finally (c/try! (.close folder true))))))))
      (read-pop3 [me msgs]
        (let [d? (get-in @impl [:conf :delete-msg?])]
          (doseq [^MimeMessage mm msgs]
            (doto mm .getAllHeaders .getContent)
            (when d?
              (.setFlag mm Flags$Flag/DELETED true))
            (pc/dispatch! (evt<> me mm)))))
      (connect-pop3 [me]
        (let [{:keys [conf proto session]} @impl
              {:keys [host user port passwd]} conf
              s (.getStore ^Session session ^String proto)]
          (if (nil? s)
            (l/warn "failed to get session store#%s." proto)
            (do (l/debug "connect to session store#%s..." proto)
                (.connect s
                          ^String host
                          ^long port
                          ^String user
                          (c/stror (i/x->str passwd) nil))
                (swap! impl
                       assoc
                       :store s
                       :folder (some-> (.getDefaultFolder s)
                                       (.getFolder "INBOX")))
                (let [^Folder fd (:folder @impl)]
                  (when (or (nil? fd)
                            (not (.exists fd)))
                    (l/warn "bad mail store folder#%s." proto)
                    (swap! impl assoc :store nil :folder nil)
                    (c/try! (.close s))
                    (u/throw-IOE "Cannot find inbox!")))))))
      (close-store [_]
        (let [{:keys [^Store store folder]} @impl]
          (close-folder folder)
          (c/try! (some-> store .close))
          (swap! impl assoc :store nil :folder nil)))
      xp/Pluglet
      (user-handler [_] (get-in @impl [:conf :$handler]))
      (err-handler [_] (get-in @impl [:conf :$error]))
      (gconf [_] (:conf @impl))
      po/Hierarchical
      (parent [_] plug)
      po/Idable
      (id [_] _id)
      po/Initable
      (init [me arg]
        (let [{:keys [sslvars vars]} @impl
              pk (-> me po/parent xp/pkey-chars)]
          (swap! impl
                 update-in
                 [:conf]
                 #(->> (merge % arg)
                       (sanitize pk)
                       b/expand-vars* b/prevar-cfg))
          (resolve-provider me
                            (if (:ssl? (:conf @impl)) sslvars vars)) me))
      po/Finzable
      (finz [_] (po/stop _) _)
      po/Startable
      (stop [_]
        (pl/stop-threaded-loop! (:loopy @impl)) _)
      (start [_] (po/start _ nil))
      (start [me arg]
        (swap! impl
               assoc
               :loopy
               (pl/schedule-threaded-loop me (:waker @impl))) me))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- email-xxx

  [_ id spec sslvars vars wakerFunc]

  (pluglet _ id spec {:sslvars sslvars
                      :vars vars
                      :waker wakerFunc}))

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
          :$handler nil}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- wake-pop3

  [co] (try (connect-pop3 co)
            (scan-pop3 co)
            (catch Throwable _ (l/exception _))
            (finally (close-store co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/defmacro- connect-imap

  [co] `(connect-pop3 ~co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/defmacro- scan-imap

  [co] `(scan-pop3 ~co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- wake-imap

  [co] (try (connect-imap co)
            (scan-imap co)
            (catch Throwable _ (l/exception _))
            (finally (close-store co))))

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
          :$handler nil}})

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

