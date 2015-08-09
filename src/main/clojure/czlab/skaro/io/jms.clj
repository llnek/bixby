;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.io.jms

  (:require
    [czlab.xlib.util.core
    :refer [NextLong ThrowIOE MakeMMap juid tryc]]
    [czlab.xlib.crypto.codec :refer [Pwdify]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.str :refer [hgl? ]])

  (:use [czlab.skaro.core.sys]
        [czlab.skaro.io.core])

  (:import
    [java.util Hashtable Properties ResourceBundle]
    [org.apache.commons.lang3 StringUtils]
    [com.zotohlab.frwk.core Identifiable]
    [javax.jms Connection ConnectionFactory
    Destination Connection
    Message MessageConsumer MessageListener Queue
    QueueConnection QueueConnectionFactory QueueReceiver
    QueueSession Session Topic TopicConnection
    TopicConnectionFactory TopicSession TopicSubscriber]
    [javax.naming Context InitialContext]
    [java.io IOException]
    [com.zotohlab.skaro.core Muble]
    [com.zotohlab.skaro.io JMSEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.skaro.io/JMS

  [co & args]

  (log/info "IOESReifyEvent: JMS: %s" (.id ^Identifiable co))
  (let [msg (first args)
        eeid (NextLong)
        impl (MakeMMap)]
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

  [^czlab.skaro.io.core.EmitAPI co msg]

  ;;if (msg!=null) block { () => msg.acknowledge() }
  (.dispatch co (IOESReifyEvent co msg) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.skaro.io/JMS

  [^Muble co cfg0]

  (log/info "compConfigure: JMS: %s" (.id ^Identifiable co))
  (let [{:keys [appkey jndiPwd jmsPwd]
         :as cfg}
        (merge (.getv co :dftOptions) cfg0)]
    (.setv co :emcfg
           (-> cfg
               (assoc :jndiPwd (Pwdify jndiPwd appkey))
               (assoc :jmsPwd (Pwdify jmsPwd appkey))))
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
      (ThrowIOE "Object not of Destination type"))
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
        conn (if (hgl? jmsUser)
               (.createTopicConnection cf
                                       ^String jmsUser
                                       ^String (if (hgl? jmsPwd) jmsPwd nil))
               (.createTopicConnection cf))
        s (.createTopicSession conn false Session/CLIENT_ACKNOWLEDGE)
        t (.lookup ctx ^String destination) ]
    (when-not (instance? Topic t)
      (ThrowIOE "Object not of Topic type"))
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
        conn (if (hgl? jmsUser)
               (.createQueueConnection cf
                                       ^String jmsUser
                                       ^String (if (hgl? jmsPwd) jmsPwd nil))
               (.createQueueConnection cf))
        s (.createQueueSession conn false Session/CLIENT_ACKNOWLEDGE)
        q (.lookup ctx ^String destination) ]
    (when-not (instance? Queue q)
      (ThrowIOE "Object not of Queue type"))
    (-> (.createReceiver s ^Queue q)
        (.setMessageListener (reify MessageListener
                               (onMessage [_ m] (onMsg co m)))))
    conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.skaro.io/JMS

  [^Muble co]

  (log/info "IOESStart: JMS: %s" (.id ^Identifiable co))
  (let [{:keys [contextFactory providerUrl
                jndiUser jndiPwd connFactory]}
        (.getv co :emcfg)
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
        (ThrowIOE "Unsupported JMS Connection Factory"))
      (.setv co :conn c)
      (.start c)
      (IOESStarted co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.skaro.io/JMS

  [^Muble co]

  (log/info "IOESStop: JMS: %s" (.id ^Identifiable co))
  (when-some [^Connection c (.getv co :conn) ]
    (tryc (.close c))
    (.setv co :conn nil)
    (IOESStopped co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

