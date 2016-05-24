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
      :author "kenl" }

  czlab.skaro.io.jms

  (:require
    [czlab.xlib.core
     :refer [nextLong
             throwIOE
             mubleObj!
             juid
             tryc]]
    [czlab.crypto.codec :refer [pwdify]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.str :refer [hgl? ]])

  (:use [czlab.skaro.core.sys]
        [czlab.skaro.io.core])

  (:import
    [java.util Hashtable Properties ResourceBundle]
    [org.apache.commons.lang3 StringUtils]
    [czlab.wflow.server Emitter]
    [czlab.xlib Muble Identifiable]
    [javax.jms Connection ConnectionFactory
     Destination Connection
     Message MessageConsumer MessageListener Queue
     QueueConnection QueueConnectionFactory QueueReceiver
     QueueSession Session Topic TopicConnection
     TopicConnectionFactory TopicSession TopicSubscriber]
    [javax.naming Context InitialContext]
    [java.io IOException]
    [czlab.skaro.io JMSEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioReifyEvent :czc.skaro.io/JMS

  [co & args]

  (log/info "ioReifyEvent: JMS: %s" (.id ^Identifiable co))
  (let [msg (first args)
        eeid (nextLong)
        impl (mubleObj!)]
    (with-meta
      (reify

        Identifiable

        (id [_] eeid)

        JMSEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] )
        (getSession [_] )
        (getId [_] eeid)
        (emitter [_] co)
        (getMsg [_] msg))

      {:typeid :czc.skaro.io/JMSEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onMsg ""

  [^Emitter co msg]

  ;;if (msg!=null) block { () => msg.acknowledge() }
  (.dispatch co (ioReifyEvent co msg) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod compConfigure :czc.skaro.io/JMS

  [^Muble co cfg0]

  (log/info "compConfigure: JMS: %s" (.id ^Identifiable co))
  (let [{:keys [appkey jndiPwd jmsPwd]
         :as cfg}
        (merge (.getv co :dftOptions) cfg0)]
    (.setv co :emcfg
           (-> cfg
               (assoc :jndiPwd (pwdify jndiPwd appkey))
               (assoc :jmsPwd (pwdify jmsPwd appkey))))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizFac ""

  ^Connection

  [^Muble co
   ^InitialContext ctx
   ^ConnectionFactory cf]

  (let [{:keys [destination jmsPwd jmsUser]}
        (.getv co :emcfg)
        jmsPwd (str jmsPwd)
        c (.lookup ctx ^String destination)
        ^Connection
        conn (if (hgl? jmsUser)
               (.createConnection cf
                                  ^String jmsUser
                                  ^String (if (hgl? jmsPwd) jmsPwd nil))
               (.createConnection cf)) ]
    (if (instance? Destination c)
      ;;TODO ? ack always ?
      (-> (.createSession conn false Session/CLIENT_ACKNOWLEDGE)
          (.createConsumer c)
          (.setMessageListener (reify MessageListener
                                 (onMessage [_ m] (onMsg co m)))))
      (throwIOE "Object not of Destination type"))
    conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizTopic ""

  ^Connection

  [^Muble co
   ^InitialContext ctx
   ^TopicConnectionFactory cf]

  (let [{:keys [destination jmsUser
                durable jmsPwd]}
        (.getv co :emcfg)
        jmsPwd (str jmsPwd)
        conn (if (hgl? jmsUser)
               (.createTopicConnection cf
                                       ^String jmsUser
                                       ^String (if (hgl? jmsPwd) jmsPwd nil))
               (.createTopicConnection cf))
        s (.createTopicSession conn false Session/CLIENT_ACKNOWLEDGE)
        t (.lookup ctx ^String destination) ]
    (when-not (instance? Topic t)
      (throwIOE "Object not of Topic type"))
    (-> (if durable
          (.createDurableSubscriber s t (juid))
          (.createSubscriber s t))
        (.setMessageListener (reify MessageListener
                               (onMessage [_ m] (onMsg co m))) ))
    conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizQueue ""

  ^Connection
  [^Muble co
   ^InitialContext ctx
   ^QueueConnectionFactory cf]

  (let [{:keys [destination jmsUser jmsPwd]}
        (.getv co :emcfg)
        jmsPwd (str jmsPwd)
        conn (if (hgl? jmsUser)
               (.createQueueConnection cf
                                       ^String jmsUser
                                       ^String (if (hgl? jmsPwd) jmsPwd nil))
               (.createQueueConnection cf))
        s (.createQueueSession conn false Session/CLIENT_ACKNOWLEDGE)
        q (.lookup ctx ^String destination) ]
    (when-not (instance? Queue q)
      (throwIOE "Object not of Queue type"))
    (-> (.createReceiver s ^Queue q)
        (.setMessageListener (reify MessageListener
                               (onMessage [_ m] (onMsg co m)))))
    conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStart :czc.skaro.io/JMS

  [^Muble co & args]

  (log/info "ioStart: JMS: %s" (.id ^Identifiable co))
  (let [{:keys [contextFactory providerUrl
                jndiUser jndiPwd connFactory]}
        (.getv co :emcfg)
        jndiPwd (str jndiPwd)
        vars (Hashtable.) ]

    (when (hgl? contextFactory)
      (.put vars Context/INITIAL_CONTEXT_FACTORY contextFactory))

    (when (hgl? providerUrl)
      (.put vars Context/PROVIDER_URL providerUrl))

    (when (hgl? jndiUser)
      (.put vars "jndi.user" jndiUser)
      (when (hgl? jndiPwd)
        (.put vars "jndi.password" jndiPwd)))

    (let [ctx (InitialContext. vars)
          obj (->> (str connFactory)
                   (.lookup ctx))
          c (condp instance? obj
              QueueConnectionFactory (inizQueue co ctx obj)
              TopicConnectionFactory (inizTopic co ctx obj)
              ConnectionFactory (inizFac co ctx obj)
              nil) ]
      (when (nil? c)
        (throwIOE "Unsupported JMS Connection Factory"))
      (.setv co :conn c)
      (.start c)
      (ioStarted co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioStop :czc.skaro.io/JMS

  [^Muble co & args]

  (log/info "ioStop: JMS: %s" (.id ^Identifiable co))
  (when-some [^Connection c (.getv co :conn) ]
    (tryc (.close c))
    (.setv co :conn nil)
    (ioStopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


