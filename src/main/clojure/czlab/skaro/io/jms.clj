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

  czlab.skaro.io.jms

  (:require
    [czlab.xlib.core
     :refer [throwIOE
             seqint2
             inst?
             muble<>
             juid
             try!]]
    [czlab.crypto.codec :refer [passwd<>]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.str :refer [hgl? stror]])

  (:use [czlab.skaro.sys.core]
        [czlab.skaro.io.core])

  (:import
    [java.util Hashtable Properties ResourceBundle]
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
    [javax.naming Context InitialContext]
    [java.io IOException]
    [czlab.skaro.server Container Service]
    [czlab.skaro.io JMSEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>

  ::JMS
  [^Service co & args]

  (log/info "ioevent: JMS: %s" (.id co))
  (let [msg (first args)
        eeid (seqint2)
        impl (muble<>)]
    (with-meta
      (reify
        JMSEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] )
        (session [_] )
        (id [_] eeid)
        (source [_] co)
        (message [_] msg))

      {:typeid ::JMSEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onMsg

  ""
  [^Service co msg]

  ;;if (msg!=null) block { () => msg.acknowledge() }
  (.dispatch co (ioevent<> co msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::JMS
  [^Service co & xs]

  (log/info "comp->initialize: JMS: %s" (.id co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizFac

  ""
  ^Connection
  [^Service co
   ^InitialContext ctx
   ^ConnectionFactory cf]

  (let
    [{:keys [^String destination
             ^String jmsPwd
             ^String jmsUser]}
     (.config co)
     pwd (->> ^Container (.server co)
             (.appKey )
             (passwd<> jmsPwd ))
     c (.lookup ctx destination)
     ^Connection
     conn (if (hgl? jmsUser)
            (.createConnection
              cf jmsUser
              ^String (stror pwd nil))
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
  [^Service co
   ^InitialContext ctx
   ^TopicConnectionFactory cf]

  (let
    [{:keys [^String destination
             ^String jmsUser
             durable
             ^String jmsPwd]}
     (.config co)
     pwd (->> ^Container (.server co)
              (.appKey)
              (passwd<> jmsPwd))
     conn (if (hgl? jmsUser)
            (.createTopicConnection
              cf jmsUser
              ^String (stror pwd nil))
            (.createTopicConnection cf))
     s (.createTopicSession
         conn false Session/CLIENT_ACKNOWLEDGE)
     t (.lookup ctx destination)]
    (when-not (inst? Topic t)
      (throwIOE "Object not of Topic type"))
    (-> (if durable
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
  [^Service co
   ^InitialContext ctx
   ^QueueConnectionFactory cf]

  (let
    [{:keys [^String destination
             ^String jmsUser
             ^String jmsPwd]}
     (.config co)
     pwd (->> ^Container (.server co)
              (.appKey)
              (passwd<> jmsPwd))
     conn (if (hgl? jmsUser)
            (.createQueueConnection
              cf jmsUser
              ^String (stror pwd nil))
            (.createQueueConnection cf))
     s (.createQueueSession conn
                            false Session/CLIENT_ACKNOWLEDGE)
     q (.lookup ctx destination)]
    (when-not (inst? Queue q)
      (throwIOE "Object not of Queue type"))
    (-> (.createReceiver s ^Queue q)
        (.setMessageListener
          (reify MessageListener
            (onMessage [_ m] (onMsg co m)))))
    conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::JMS
  [^Service co & args]

  (log/info "io->start: JMS: %s" (.id co))
  (let
    [{:keys [contextFactory providerUrl
             jndiUser jndiPwd connFactory]}
     (.config co)
     pwd (->> ^Container (.server co)
              (.appKey)
              (passwd<> jndiPwd))
     vars (Hashtable.) ]
    (when (hgl? providerUrl)
      (.put vars Context/PROVIDER_URL providerUrl))
    (when (hgl? contextFactory)
      (.put vars
            Context/INITIAL_CONTEXT_FACTORY
            contextFactory))
    (when (hgl? jndiUser)
      (.put vars "jndi.user" jndiUser)
      (.put vars "jndi.password" (stror pwd nil)))
    (let
      [ctx (InitialContext. vars)
       obj (->> (str connFactory)
                (.lookup ctx))
       c (condp instance? obj
           QueueConnectionFactory
           (inizQueue co ctx obj)
           TopicConnectionFactory
           (inizTopic co ctx obj)
           ConnectionFactory
           (inizFac co ctx obj)
           nil)]
      (when (nil? c)
        (throwIOE "Unsupported JMS Connection Factory"))
      (.setv (.getx co) :conn c)
      (.start c)
      (io<started> co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::JMS
  [^Service co & args]

  (log/info "io->stop: JMS: %s" (.id co))
  (when-some
    [^Connection c
     (.getv (.getx co) :conn)]
    (try! (.close c))
    (.unsetv (.getx co) :conn)
    (io<stopped> co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


