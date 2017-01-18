;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Implementation for JMS service."
      :author "Kenneth Leung" }

  czlab.wabbit.io.jms

  (:require [czlab.twisty.codec :refer [passwd<>]]
            [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.base.core]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- evt<>
  ""
  [^IoService co {:keys [msg]}]
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
  (.dispatch co (evt<> co {:msg msg})))

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
(defn- start
  ""
  ^Connection
  [co {:keys [contextFactory
              providerUrl
              jndiUser jndiPwd connFactory]}]
  (let
    [vars (Hashtable.)]
    (if (hgl? providerUrl)
      (.put vars Context/PROVIDER_URL providerUrl))
    (if (hgl? contextFactory)
      (.put vars
            Context/INITIAL_CONTEXT_FACTORY
            contextFactory))
    (when (hgl? jndiUser)
      (.put vars "jndi.user" jndiUser)
      (if (hgl? jndiPwd)
        (.put vars "jndi.password" jndiPwd)))
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
      (.start c)
      c)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn JMS
  ""
  [co {:keys [conf] :as spec}]
  (let
    [cee (keyword (juid))
     impl (muble<>)]
    (reify
      LifeCycle
      (init [_ arg]
        (->> (merge conf
                    (sanitize co arg))
             (.copyEx impl)))
      (parent [_] co)
      (config [_] (.intern impl))
      (start [_ _]
        (let [c (start co (.intern impl))]
          (.setv impl cee c)))
      (stop [_]
        (when-some
          [^Connection c (.getv impl cee)]
          (try! (.close c))
          (.unsetv impl cee))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


