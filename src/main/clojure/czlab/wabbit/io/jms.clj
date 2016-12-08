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


(ns ^{:doc "Implementation for JMS service."
      :author "Kenneth Leung" }

  czlab.wabbit.io.jms

  (:require [czlab.twisty.codec :refer [passwd<>]]
            [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.etc.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wabbit.io.core])

  (:import [java.util Hashtable Properties ResourceBundle]
           [czlab.xlib Muble Identifiable]
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
           [javax.naming Context InitialContext]
           [czlab.wabbit.server Container]
           [czlab.wabbit.io IoService JmsEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(derive ::JMS :czlab.wabbit.io.core/Service)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>
  ::JMS
  [^IoService co {:keys [msg]}]

  (logcomp "ioevent" co)
  (let [eeid (str "event#" (seqint2))
        impl (muble<>)]
    (with-meta
      (reify JmsEvent
        (checkAuthenticity [_] false)
        (id [_] eeid)
        (source [_] co)
        (message [_] msg))
      {:typeid ::JmsEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onMsg
  ""
  [^IoService co msg]
  ;;if (msg!=null) block { () => msg.acknowledge() }
  (.dispatch co (ioevent<> co {:msg msg})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizFac
  ""
  ^Connection
  [^IoService co ^InitialContext ctx ^ConnectionFactory cf]
  (let
    [{:keys [^String destination
             ^String jmsPwd
             ^String jmsUser]}
     (.config co)
     pwd (->> (.server co)
              (.podKey )
              (passwd<> jmsPwd))
     c (.lookup ctx destination)
     ^Connection
     conn (if (hgl? jmsUser)
            (.createConnection
              cf
              jmsUser
              (stror pwd nil))
            (.createConnection cf))]
    (if (inst? Destination c)
      ;;TODO ? ack always ?
      (-> (.createSession conn false Session/CLIENT_ACKNOWLEDGE)
          (.createConsumer c)
          (.setMessageListener
            (reify MessageListener
              (onMessage [_ m] (onMsg co m)))))
      (throwIOE "Object not of Destination type"))
    conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizTopic
  ""
  ^Connection
  [^IoService co ^InitialContext ctx ^TopicConnectionFactory cf]
  (let
    [{:keys [^String destination
             ^String jmsUser
             durable?
             ^String jmsPwd]}
     (.config co)
     pwd (->> (.server co)
              (.podKey)
              (passwd<> jmsPwd))
     conn (if (hgl? jmsUser)
            (.createTopicConnection
              cf
              jmsUser
              (stror pwd nil))
            (.createTopicConnection cf))
     s (.createTopicSession
         conn false Session/CLIENT_ACKNOWLEDGE)
     t (.lookup ctx destination)]
    (if-not (inst? Topic t)
      (throwIOE "Object not of Topic type"))
    (-> (if durable?
          (.createDurableSubscriber s t (juid))
          (.createSubscriber s t))
        (.setMessageListener
          (reify MessageListener
            (onMessage [_ m] (onMsg co m)))))
    conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizQueue
  ""
  ^Connection
  [^IoService co ^InitialContext ctx ^QueueConnectionFactory cf]
  (let
    [{:keys [^String destination
             ^String jmsUser
             ^String jmsPwd]}
     (.config co)
     pwd (->> (.server co)
              (.podKey)
              (passwd<> jmsPwd))
     conn (if (hgl? jmsUser)
            (.createQueueConnection
              cf
              jmsUser
              (stror pwd nil))
            (.createQueueConnection cf))
     s (.createQueueSession conn
                            false Session/CLIENT_ACKNOWLEDGE)
     q (.lookup ctx destination)]
    (if-not (inst? Queue q)
      (throwIOE "Object not of Queue type"))
    (-> (.createReceiver s ^Queue q)
        (.setMessageListener
          (reify MessageListener
            (onMessage [_ m] (onMsg co m)))))
    conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sanitize
  ""
  [^IoService co cfg0]
  (let [{:keys [jndiPwd jmsPwd]}
        cfg0
        pkey (.podKey (.server co))]
    (-> cfg0
        (assoc :jndiPwd (.text (passwd<> jndiPwd pkey)))
        (assoc :jmsPwd (.text (passwd<> jmsPwd pkey))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init
  ::JMS
  [^IoService co cfg0]

  (logcomp "comp->init" co)
  (->> (merge (.config co)
              (sanitize cfg0))
       (.setv (.getx co) :emcfg ))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start
  ::JMS
  [^IoService co]

  (logcomp "io->start" co)
  (let
    [{:keys [contextFactory providerUrl
             jndiUser jndiPwd connFactory]}
     (.config co)
     vars (Hashtable.)]
    (if (hgl? providerUrl)
      (.put vars Context/PROVIDER_URL providerUrl))
    (if (hgl? contextFactory)
      (.put vars
            Context/INITIAL_CONTEXT_FACTORY
            contextFactory))
    (when (hgl? jndiUser)
      (.put vars "jndi.user" jndiUser)
      (.put vars "jndi.password" (stror jndiPwd nil)))
    (let
      [ctx (InitialContext. vars)
       obj (->> (str connFactory)
                (.lookup ctx))
       ^Connection
       c (condp instance? obj
           QueueConnectionFactory
           (inizQueue co ctx obj)
           TopicConnectionFactory
           (inizTopic co ctx obj)
           ConnectionFactory
           (inizFac co ctx obj))]
      (if (nil? c)
        (throwIOE "Unsupported JMS Connection Factory"))
      (.setv (.getx co) :conn c)
      (.start c)
      (io<started> co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop
  ::JMS
  [^IoService co]

  (logcomp "io->stop" co)
  (when-some
    [^Connection c
     (.getv (.getx co) :conn)]
    (try! (.close c))
    (.unsetv (.getx co) :conn)
    (io<stopped> co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


