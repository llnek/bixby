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
             try!!]]
    [czlab.crypto.codec :refer [passwd<>]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.str :refer [hgl? ]])

  (:use [czlab.skaro.core.sys]
        [czlab.skaro.io.core])

  (:import
    [java.util Hashtable Properties ResourceBundle]
    [czlab.server EventEmitter]
    [czlab.xlib Muble Identifiable]
    [javax.jms
     Connection
     ConnectionFactory
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
    [czlab.skaro.io JMSEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>

  ::JMS
  [^EventEmitter co & args]

  (log/info "ioevent: JMS: %s" (.id ^Identifiable co))
  (let [msg (first args)
        eeid (seqint2)
        impl (muble<>)]
    (with-meta
      (reify

        Identifiable

        (id [_] eeid)

        JMSEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] )
        (getSession [_] )
        (id [_] eeid)
        (emitter [_] co)
        (getMsg [_] msg))

      {:typeid ::JMSEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onMsg

  ""
  [^EventEmitter co msg]

  ;;if (msg!=null) block { () => msg.acknowledge() }
  (.dispatch co (ioevent<> co msg) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->configure

  ::JMS
  [^Context co cfg0]

  (log/info "compConfigure: JMS: %s" (.id ^Identifiable co))
  (let [{:keys [appkey jndiPwd jmsPwd]
         :as cfg}
        (merge (.getv (.getx co) :dftOptions) cfg0)]
    (.setv (.getx co)
           :emcfg
           (-> cfg
               (assoc :jndiPwd
                      (-> (passwd<> jndiPwd appkey)
                          (.text)))
               (assoc :jmsPwd
                      (-> (passwd<> jmsPwd appkey)
                          (.text)))))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizFac

  ""
  ^Connection
  [^Context co
   ^InitialContext ctx
   ^ConnectionFactory cf]

  (let [{:keys [^String destination
                ^String jmsPwd
                ^String jmsUser]}
        (.getv (.getx co) :emcfg)
        c (.lookup ctx ^String destination)
        ^Connection
        conn (if (hgl? jmsUser)
               (.createConnection cf
                                  jmsUser
                                  ^String
                                  (stror jmsPwd nil))
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
  [^Context co
   ^InitialContext ctx
   ^TopicConnectionFactory cf]

  (let [{:keys [^String destination
                ^String jmsUser
                durable
                ^String jmsPwd]}
        (.getv (.getx co) :emcfg)
        conn (if (hgl? jmsUser)
               (.createTopicConnection cf
                                       jmsUser
                                       ^String
                                       (stror jmsPwd nil))
               (.createTopicConnection cf))
        s (.createTopicSession conn false Session/CLIENT_ACKNOWLEDGE)
        t (.lookup ctx destination) ]
    (when-not (inst? Topic t)
      (throwIOE "Object not of Topic type"))
    (-> (if durable
          (.createDurableSubscriber s t (juid))
          (.createSubscriber s t))
        (.setMessageListener
          (reify MessageListener
            (onMessage [_ m] (onMsg co m))) ))
    conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizQueue

  ""
  ^Connection
  [^Context co
   ^InitialContext ctx
   ^QueueConnectionFactory cf]

  (let [{:keys [^String destination
                ^String jmsUser
                ^String jmsPwd]}
        (.getv (.getx co) :emcfg)
        conn (if (hgl? jmsUser)
               (.createQueueConnection cf
                                       jmsUser
                                       ^String
                                       (stror jmsPwd nil))
               (.createQueueConnection cf))
        s (.createQueueSession conn false Session/CLIENT_ACKNOWLEDGE)
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
  [^Context co & args]

  (log/info "ioStart: JMS: %s" (.id ^Identifiable co))
  (let [{:keys [contextFactory providerUrl
                jndiUser jndiPwd connFactory]}
        (.getv (.getx co) :emcfg)
        vars (Hashtable.) ]

    (when (hgl? contextFactory)
      (.put vars Context/INITIAL_CONTEXT_FACTORY contextFactory))

    (when (hgl? providerUrl)
      (.put vars Context/PROVIDER_URL providerUrl))

    (when (hgl? jndiUser)
      (.put vars "jndi.user" jndiUser)
      (.put vars "jndi.password" jndiPwd))

    (let [ctx (InitialContext. vars)
          obj (->> (str connFactory)
                   (.lookup ctx))
          c (condp instance? obj
              QueueConnectionFactory (inizQueue co ctx obj)
              TopicConnectionFactory (inizTopic co ctx obj)
              ConnectionFactory (inizFac co ctx obj)
              nil)]
      (when (nil? c)
        (throwIOE "Unsupported JMS Connection Factory"))
      (.setv (.getx co) :conn c)
      (.start c)
      (io->started co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::JMS
  [^Context co & args]

  (log/info "ioStop: JMS: %s" (.id ^Identifiable co))
  (when-some [^Connection c
              (.getv (.getx co) :conn)]
    (try! (.close c))
    (.unsetv (.getx co) :conn)
    (io->stopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


