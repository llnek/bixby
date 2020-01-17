;; Copyright © 2013-2020, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.plugs.jms

  "Implementation for JMS service."

  (:require [czlab.blutbad.core :as b]
            [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.twisty.codec :as co]
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
  c/Idable
  (id [_] (:id _))
  c/Hierarchical
  (parent [_] (:source _)))

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
  (b/dispatch (evt<> co msg)))

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

  (let [{:keys [provider-url
                context-factory
                jndi-user jndi-pwd conn-factory]} (:conf co)
        ctx (InitialContext.
              (init-map ["jndi.user" jndi-user
                         "jndi.password" jndi-pwd
                         Context/PROVIDER_URL provider-url
                         Context/INITIAL_CONTEXT_FACTORY (name context-factory)]))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord JMSPlugin [server _id info conf]
  c/Hierarchical
  (parent [_] server)
  c/Idable
  (id [_] _id)
  c/Initable
  (init [me arg]
    (update-in me
               [:conf]
               #(-> (c/parent me)
                    b/pkey
                    i/x->chars
                    (sanitize (c/merge+ % arg))
                    b/expand-vars* b/prevar-cfg)))
  c/Finzable
  (finz [_] (c/stop _))
  c/Startable
  (stop [me]
    (c/try! (i/klose (:conn me))) me)
  (start [_]
    (c/start _ nil))
  (start [me arg]
    (assoc me :conn (start2 me))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn iniz-fac

  [plug ctx cf]

  (let [{:keys [destination jms-pwd jms-user]} (:conf plug)
        c (.lookup ^InitialContext ctx ^String destination)
        pwd (->> (c/parent plug)
                 b/pkey i/x->chars (co/pwd<> jms-pwd) co/pw-text)]
    (c/do-with [^Connection
                conn (if (c/nichts? jms-user)
                       (.createConnection ^ConnectionFactory cf)
                       (.createConnection ^ConnectionFactory cf
                                          ^String jms-user
                                          (c/stror (i/x->str pwd) nil)))]
      (c/info "conn== %s" conn)
      (c/info "c === %s" c)
      (c/info "pwd === %s" (i/x->str pwd))
      ;;TODO ? ack always ?
      (u/assert-IOE (c/is? Destination c)
                    "Object not of Destination type!")
      (-> (.createSession conn false Session/CLIENT_ACKNOWLEDGE)
          (.createConsumer c)
          (.setMessageListener
            (reify MessageListener
              (onMessage [_ m] (on-msg plug m))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn iniz-topic

  [plug ctx cf]

  (let [{:keys [destination
                jms-user durable? jms-pwd]} (:conf plug)
        pwd (->> (c/parent plug)
                 b/pkey i/x->chars (co/pwd<> jms-pwd) co/pw-text)]
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
        (u/assert-IOE (c/is? Topic t)
                      "Object not of Topic type!")
        (-> (if-not durable?
              (.createSubscriber s t)
              (.createDurableSubscriber s t (u/jid<>)))
            (.setMessageListener
              (reify MessageListener
                (onMessage [_ m] (on-msg plug m)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn iniz-queue

  [plug ctx cf]

  (let [{:keys [destination jms-user jms-pwd]} (:conf plug)
        pwd (->> (c/parent plug)
                 b/pkey i/x->chars (co/pwd<> jms-pwd) co/pw-text)]
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
        (u/assert-IOE (c/is? Queue q)
                      "Object not of Queue type!")
        (-> (.createReceiver s ^Queue q)
            (.setMessageListener
              (reify MessageListener
                (onMessage [_ m] (on-msg plug m)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def
  ^{:doc ""}

  JMSSpec

  {:info {:name "JMS Client"
          :version "1.0.0"}
   :conf {:context-factory "czlab.blutbad.mock.jms.MockContextFactory"
          :$pluggable ::jms<>
          :$error nil
          :$action nil
          :provider-url "java://aaa"
          :conn-factory "tcf"
          :destination "topic.abc"
          :jndi-user "root"
          :jndi-pwd "root"
          :jms-user "anonymous"
          :jms-pwd "anonymous"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn jms<>

  "Create a JMS Plugin."
  {:arglists '([server id]
               [server id options])}

  ([_ id]
   (jms<> _ id JMSSpec))

  ([server id {:keys [info conf]}]
   (JMSPlugin. server id info conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

