;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  cmzlabclj.tardis.io.jms

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [cmzlabclj.xlib.util.core
         :only
         [ThrowIOE MubleAPI MakeMMap juid TryC]]
        [cmzlabclj.xlib.crypto.codec :only [Pwdify]]
        [cmzlabclj.xlib.util.seqnum :only [NextLong]]
        [cmzlabclj.xlib.util.str :only [hgl? nsb]]
        [cmzlabclj.tardis.core.sys]
        [cmzlabclj.tardis.io.core])

  (:import  [java.util Hashtable Properties ResourceBundle]
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
            [com.zotohlab.gallifrey.io JMSEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeJMSClient ""

  [container]

  (MakeEmitter container :czc.tardis.io/JMS))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/JMS

  [co & args]

  (let [eeid (NextLong)
        impl (MakeMMap)
        msg (first args) ]
    (with-meta
      (reify

        Identifiable

        (id [_] eeid)

        JMSEvent

        (bindSession [_ s] (.setf! impl :ios s))
        (getSession [_] (.getf impl :ios))
        (checkAuthenticity [_] false)
        (getId [_] eeid)
        (emitter [_] co)
        (getMsg [_] msg))

      { :typeid :czc.tardis.io/JMSEvent }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onMsg ""

  [^cmzlabclj.tardis.io.core.EmitterAPI co msg]

      ;;if (msg!=null) block { () => msg.acknowledge() }
  (.dispatch co (IOESReifyEvent co msg) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/JMS

  [^cmzlabclj.tardis.core.sys.Element co cfg0]

  (let [cfg (merge (.getAttr co :dftOptions) cfg0)
        pkey (:app.pkey cfg)
        p1 (:jndiPwd cfg)
        p2 (:jmsPwd cfg) ]
    (.setAttr! co :emcfg
    (-> cfg
        (assoc :jndiPwd (Pwdify p1 pkey))
        (assoc :jmsPwd (Pwdify p2 pkey))))
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizFac ""

  ^Connection

  [^cmzlabclj.tardis.core.sys.Element co
   ^InitialContext ctx
   ^ConnectionFactory cf]

  (let [cfg (.getAttr co :emcfg)
        ^String des (:destination cfg)
        jp (nsb (:jmsPwd cfg))
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
      (ThrowIOE "Object not of Destination type."))
    conn
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizTopic ""

  ^Connection

  [^cmzlabclj.tardis.core.sys.Element co
   ^InitialContext ctx
   ^TopicConnectionFactory cf]

  (let [cfg (.getAttr co :emcfg)
        ^String des (:destination cfg)
        ^String ju (:jmsUser cfg)
        jp (nsb (:jmsPwd cfg))
        conn (if (hgl? ju)
               (.createTopicConnection cf ju (if (hgl? jp) jp nil))
               (.createTopicConnection cf))
        s (.createTopicSession conn false Session/CLIENT_ACKNOWLEDGE)
        t (.lookup ctx des) ]
    (when-not (instance? Topic t)
      (ThrowIOE "Object not of Topic type."))
    (-> (if (:durable cfg)
          (.createDurableSubscriber s t (juid))
          (.createSubscriber s t))
        (.setMessageListener (reify MessageListener
                               (onMessage [_ m] (onMsg co m))) ))
    conn
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inizQueue ""

  ^Connection
  [^cmzlabclj.tardis.core.sys.Element co
   ^InitialContext ctx
   ^QueueConnectionFactory cf]

  (let [cfg (.getAttr co :emcfg)
        ^String des (:destination cfg)
        ^String ju (:jmsUser cfg)
        jp (nsb (:jmsPwd cfg))
        conn (if (hgl? ju)
               (.createQueueConnection cf ju (if (hgl? jp) jp nil))
               (.createQueueConnection cf))
        s (.createQueueSession conn false Session/CLIENT_ACKNOWLEDGE)
        q (.lookup ctx des) ]
    (when-not (instance? Queue q)
      (ThrowIOE "Object not of Queue type."))
    (-> (.createReceiver s ^Queue q)
        (.setMessageListener (reify MessageListener
                               (onMessage [_ m] (onMsg co m)))))
    conn
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/JMS

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [cfg (.getAttr co :emcfg)
        ^String cf (:contextFactory cfg)
        ^String ju (:jndiUser cfg)
        jp (nsb (:jndiPwd cfg))
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
          obj (.lookup ctx ^String (:connFactory cfg))
          c (condp instance? obj
              QueueConnectionFactory (inizQueue co ctx obj)
              TopicConnectionFactory (inizTopic co ctx obj)
              ConnectionFactory (inizFac co ctx obj)
              nil) ]
      (when (nil? c)
        (ThrowIOE "Unsupported JMS Connection Factory"))
      (.setAttr! co :conn c)
      (.start c)
      (IOESStarted co))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/JMS

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [^Connection c (.getAttr co :conn) ]
    (when-not (nil? c)
      (TryC (.close c)))
    (.setAttr! co :conn nil)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private jms-eof nil)

