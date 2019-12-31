;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.jmx.core

  (:require [clojure.string :as cs]
            [czlab.basal.core :as c]
            [czlab.basal.util :as u]
            [czlab.blutbad.core :as b]
            [czlab.blutbad.jmx.bean :as bn])

  (:import [java.net InetAddress MalformedURLException]
           [java.rmi.registry LocateRegistry Registry]
           [java.lang.management ManagementFactory]
           [java.rmi.server UnicastRemoteObject]
           [java.rmi NoSuchObjectException]
           [java.util HashMap]
           [javax.management.remote
            JMXConnectorServer
            JMXServiceURL
            JMXConnectorServerFactory]
           [javax.management
            JMException
            MBeanServer
            ObjectName
            DynamicMBean]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn object-name<>

  "paths: [ \"a=b\" \"c=d\" ]
   domain: com.acme
   beanName: mybean."
  {:tag ObjectName}

  ([domain beanName]
   (object-name<> domain beanName nil))

  ([^String domain ^String beanName paths]
   (let [cs (or paths [])
         sb (c/sbf<> domain ":" (cs/join "," cs))]
     (if-not (empty? cs) (c/sbf+ sb ","))
     (ObjectName. (str (c/sbf+ sb "name=" beanName))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- start-rmi

  [{:keys [conf]}]

  (let [{:keys [registry-port]} conf]
    {:rmi (LocateRegistry/createRegistry ^long registry-port)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- SVC "service:jmx:rmi://{{h}}:{{s}}/jndi/rmi://{{h}}:{{r}}/jmxrmi")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- CFC "com.sun.jndi.rmi.registry.RegistryContextFactory")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- start-jmx

  [{:keys [conf]}]

  (let [{:keys [registry-port
                server-port
                host url
                context-factory]} conf
        host (c/stror host
                      (-> (InetAddress/getLocalHost) .getHostName))
        endpt (-> (cs/replace (c/stror url SVC) "{{h}}" host)
                  (cs/replace "{{s}}" (str server-port))
                  (cs/replace "{{r}}" (str registry-port)))
        conn (JMXConnectorServerFactory/newJMXConnectorServer
               (JMXServiceURL. endpt)
               ^Map (u/x->java {"java.naming.factory.initial"
                                (c/stror context-factory CFC)})
               (ManagementFactory/getPlatformMBeanServer))]
    (c/debug "jmx service url: %s." endpt)
    (.start conn)
    {:conn conn :bean-svr (.getMBeanServer conn)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- doreg

  [^MBeanServer svr ^ObjectName objName ^DynamicMBean mbean]

  (c/info "JMX registering object: %s." objName)
  (c/doto->> objName
             (.registerMBean svr mbean) (c/info "jmx-bean: %s.")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord JMXPlugin [server _id info conf]
  b/JmxAPI
  (jmx-dereg [me nname]
    (some-> ^MBeanServer
            (:bean-svr me) (.unregisterMBean nname)) me)
  (jmx-reg [me obj domain nname paths]
    (let [nm (object-name<> domain nname paths)]
      [nm (update-in me
                     [:obj-names]
                     conj
                     (doreg (:bean-svr me)
                            nm
                            (bn/jmx-bean<> obj)))]))
  c/Hierarchical
  (parent [_] server)
  c/Idable
  (id [_] _id)
  c/Resetable
  (reset [me]
    (doseq [nm (:obj-names me)]
      (c/try! (b/jmx-dereg me nm)))
    (assoc me :obj-names []))
  c/Initable
  (init [me arg]
    (update-in me
               [:conf]
               #(-> (c/merge+ % arg)
                    b/expand-vars* b/prevar-cfg)))
  c/Finzable
  (finz [me]
    (c/stop me)
    (c/info "jmx plugin disposed - ok.") me)
  c/Startable
  (start [_]
    (c/start _ nil))
  (start [me _]
    (try (merge me
                (start-rmi me) (start-jmx me))
         (finally
           (c/info "JmxPlugin started - ok."))))
  (stop [me]
    (let [{:as me2
           :keys [rmi conn]} (c/reset me)]
      (c/try! (some-> ^JMXConnectorServer conn .stop))
      (c/try!
        (some-> ^Registry rmi
                (UnicastRemoteObject/unexportObject true)))
      (c/info "jmx plugin stopped - ok.") me2)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def JMXSpec
  {:info {:name "JMX Server"
          :version "1.0.0"}
   :conf {:$pluggable ::jmx-monitor<>
          :registry-port 7777
          :server-port 7778
          :host ""
          :$error nil
          :$action nil}})

;;:host (-> (InetAddress/getLocalHost) .getHostName)})
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn jmx-monitor<>

  ([server _id]
   (jmx-monitor<> server _id JMXSpec))

  ([server _id {:keys [info conf]}]
   (JMXPlugin. server _id info conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; jconsole port
;;(setRegistryPort [_ p] (.setv impl :registryPort p))
;;(setServerPort[_ p] (.setv impl :serverPort p))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

