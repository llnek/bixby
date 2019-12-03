;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.wabbit.plugs.jms

  "Implementation for JMS service."

  (:require [czlab.wabbit.core :as b]
            [czlab.wabbit.xpis :as xp]
            [czlab.basal.util :as u]
            [czlab.basal.log :as l]
            [czlab.basal.io :as i]
            [czlab.basal.xpis :as po]
            [czlab.twisty.codec :as co]
            [czlab.wabbit.plugs.core :as pc]
            [czlab.basal.core :as c :refer [is?]])

  (:import [java.util Hashtable Properties ResourceBundle]
           [javax.jms
            ConnectionFactory
            Connection
            Destination
            Connection
            Message
            MessageConsumer
            MessageListener
            Queue
            QueueConnection
            QueueConnectionFactory
            QueueReceiver
            QueueSession
            Session
            Topic
            TopicConnection
            TopicConnectionFactory
            TopicSession
            TopicSubscriber]
           [java.io IOException]
           [javax.naming Context InitialContext]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(defprotocol JMSApi
  (iniz-fac [_ ctx cf] "")
  (iniz-queue [_ ctx cf] "")
  (iniz-topic [_ ctx cf] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord JmsMsg []
  po/Idable
  (id [_] (:id _))
  xp/PlugletMsg
  (get-pluglet [_] (:source _)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>

  [co msg]
  (c/object<> JmsMsg
              :source co
              :message msg
              :id (str "JmsMsg#" (u/seqint2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-msg

  [co msg]
  ;;if (msg!=null) block { () => msg.acknowledge() }
  (pc/dispatch! (evt<> co msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- sanitize

  [pkey {:keys [jndi-pwd jms-pwd] :as cfg}]

  (-> cfg
      (assoc :jndi-pwd (co/pw-text (co/pwd<> jndi-pwd pkey)))
      (assoc :jms-pwd (co/pw-text (co/pwd<> jms-pwd pkey)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- init-map

  [args]
  (c/do-with [m (Hashtable.)]
    (doseq [[k v]
            (partition 2 args)] (if (c/hgl? v) (.put m k v)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- start2

  [co]

  (let [{:keys [context-factory
                provider-url
                jndi-user jndi-pwd conn-factory]} (xp/gconf co)
        ctx (InitialContext.
              (init-map ["jndi.user" jndi-user
                         "jndi.password" jndi-pwd
                         Context/PROVIDER_URL provider-url
                         Context/INITIAL_CONTEXT_FACTORY context-factory]))
        obj (.lookup ctx (str conn-factory))]
    (c/do-with [^Connection
                c (some-> (c/condp?? instance? obj
                            QueueConnectionFactory iniz-queue
                            TopicConnectionFactory iniz-topic
                            ConnectionFactory iniz-fac)
                          (apply co ctx obj []))]
      (if c
        (.start c)
        (u/throw-IOE "Unsupported JMS Connection Factory!")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- pluglet

  [plug _id spec]

  (let [impl (atom {:info (:info spec)
                    :conf (:conf spec)})]
    (reify
      JMSApi
      (iniz-fac [me ctx cf]
        (let [{:keys [destination jms-pwd jms-user]} (:conf @impl)
              c (.lookup ^InitialContext ctx ^String destination)
              pwd (->> me po/parent
                       xp/pkey-chars (co/pwd<> jms-pwd) co/pw-text)]
          (c/do-with [^Connection
                      conn (if (c/nichts? jms-user)
                             (.createConnection ^ConnectionFactory cf)
                             (.createConnection ^ConnectionFactory cf
                                                ^String jms-user
                                                (c/stror (i/x->str pwd) nil)))]
          (l/info "conn== %s" conn)
          (l/info "c === %s" c)
          (l/info "pwd === %s" (i/x->str pwd))
            ;;TODO ? ack always ?
            (if-not (is? Destination c)
              (u/throw-IOE "Object not of Destination type!")
              (-> (.createSession conn false Session/CLIENT_ACKNOWLEDGE)
                  (.createConsumer c)
                  (.setMessageListener
                    (reify MessageListener
                      (onMessage [_ m] (on-msg me m)))))))))
      (iniz-topic [me ctx cf]
        (let [{:keys [destination
                      jms-user durable? jms-pwd]} (:conf @impl)
              pwd (->> me po/parent
                       xp/pkey-chars (co/pwd<> jms-pwd) co/pw-text)]
          (c/do-with [^TopicConnection
                      conn
                      (if (c/nichts? jms-user)
                        (.createTopicConnection ^TopicConnectionFactory cf)
                        (.createTopicConnection ^TopicConnectionFactory cf
                                                ^String jms-user
                                                (c/stror (i/x->str pwd) nil)))]
            (let [s (.createTopicSession
                      conn false Session/CLIENT_ACKNOWLEDGE)
                  t (.lookup ^InitialContext ctx ^String destination)]
              (if-not (is? Topic t)
                (u/throw-IOE "Object not of Topic type!"))
              (-> (if-not durable?
                    (.createSubscriber s t)
                    (.createDurableSubscriber s t (u/jid<>)))
                  (.setMessageListener
                    (reify MessageListener
                      (onMessage [_ m] (on-msg me m)))))))))
      (iniz-queue [me ctx cf]
        (let [{:keys [destination jms-user jms-pwd]} (:conf @impl)
              pwd (->> me po/parent
                       xp/pkey-chars (co/pwd<> jms-pwd) co/pw-text)]
          (c/do-with [^QueueConnection
                      conn
                      (if (c/nichts? jms-user)
                        (.createQueueConnection ^QueueConnectionFactory cf)
                        (.createQueueConnection ^QueueConnectionFactory cf
                                                ^String jms-user
                                                (c/stror (i/x->str pwd) nil)))]
            (let [s (.createQueueSession conn
                                         false Session/CLIENT_ACKNOWLEDGE)
                  q (.lookup ^InitialContext ctx ^String destination)]
              (if-not (is? Queue q)
                (u/throw-IOE "Object not of Queue type!"))
              (-> (.createReceiver s ^Queue q)
                  (.setMessageListener
                    (reify MessageListener
                      (onMessage [_ m] (on-msg me m)))))))))
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
        (swap! impl
               update-in
               [:conf]
               #(-> me
                    po/parent
                    xp/pkey-chars
                    (sanitize (merge % arg))
                    b/expand-vars* b/prevar-cfg)) me)
      po/Finzable
      (finz [_] (po/stop _) _)
      po/Startable
      (stop [me]
        (c/try! (i/klose (:conn @impl))) me)
      (start [_] (po/start _ nil))
      (start [me arg]
        (swap! impl
               assoc :conn (start2 me)) me))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def JMSSpec
  {:info {:name "JMS Client"
          :version "1.0.0"}
   :conf {:context-factory "czlab.wabbit.mock.jms.MockContextFactory"
          :$pluggable ::jms<>
          :provider-url "java://aaa"
          :conn-factory "tcf"
          :destination "topic.abc"
          :jndi-user "root"
          :jndi-pwd "root"
          :jms-user "anonymous"
          :jms-pwd "anonymous"
          :$handler nil}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn jms<>

  ([_ id]
   (jms<> _ id JMSSpec))
  ([_ id spec]
   (pluglet _ id spec)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

