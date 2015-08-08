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
    [czlab.xlib.util.str :refer [hgl? ]])

  (:require
    [czlab.xlib.util.logging :as log])

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

        (bindSession [_ s] (.setv impl :ios s))
        (getSession [_] (.getv impl :ios))
        (checkAuthenticity [_] false)
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
  (let [cfg (merge (.getv co :dftOptions) cfg0)
        pkey (:app.pkey cfg)
        p1 (:jndiPwd cfg)
        p2 (:jmsPwd cfg) ]
    (.setv co :emcfg
    (-> cfg
        (assoc :jndiPwd (Pwdify p1 pkey))
        (assoc :jmsPwd (Pwdify p2 pkey))))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizFac ""

  ^Connection

  [^Muble co
   ^InitialContext ctx
   ^ConnectionFactory cf]

  (let [cfg (.getv co :emcfg)
        ^String des (:destination cfg)
        jp (str (:jmsPwd cfg))
        ^String ju (:jmsUser cfg)
        c (.lookup ctx des)
        ^Connection
        conn (if (hgl? ju)
               (.createConnection cf ju (if (hgl? jp) jp nil))
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

  (let [cfg (.getv co :emcfg)
        des (str (:destination cfg))
        ju (str (:jmsUser cfg))
        jp (str (:jmsPwd cfg))
        conn (if (hgl? ju)
               (.createTopicConnection cf ju (if (hgl? jp) jp nil))
               (.createTopicConnection cf))
        s (.createTopicSession conn false Session/CLIENT_ACKNOWLEDGE)
        t (.lookup ctx des) ]
    (when-not (instance? Topic t)
      (ThrowIOE "Object not of Topic type"))
    (-> (if (:durable cfg)
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

  (let [cfg (.getv co :emcfg)
        des (str (:destination cfg))
        ju (str (:jmsUser cfg))
        jp (str (:jmsPwd cfg))
        conn (if (hgl? ju)
               (.createQueueConnection cf ju (if (hgl? jp) jp nil))
               (.createQueueConnection cf))
        s (.createQueueSession conn false Session/CLIENT_ACKNOWLEDGE)
        q (.lookup ctx des) ]
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
  (let [cfg (.getv co :emcfg)
        cf (str (:contextFactory cfg))
        ju (str (:jndiUser cfg))
        jp (str (:jndiPwd cfg))
        pl (:providerUrl cfg)
        vars (Hashtable.) ]

    (when (hgl? cf)
      (.put vars Context/INITIAL_CONTEXT_FACTORY cf))

    (when (hgl? pl)
      (.put vars Context/PROVIDER_URL pl))

    (when (hgl? ju)
      (.put vars "jndi.user" ju)
      (when (hgl? jp)
        (.put vars "jndi.password" jp)))

    (let [ctx (InitialContext. vars)
          obj (->> (str (:connFactory cfg))
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

