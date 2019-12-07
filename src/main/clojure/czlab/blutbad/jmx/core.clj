;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.wabbit.jmx.core

  (:require [clojure.string :as cs]
            [czlab.wabbit.core :as b]
            [czlab.wabbit.xpis :as xp]
            [czlab.basal.core :as c]
            [czlab.basal.log :as l]
            [czlab.basal.util :as u]
            [czlab.basal.xpis :as po]
            [czlab.wabbit.jmx.bean :as bn])

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
(defn- mk-jmxrror

  [^String msg ^Throwable e]

  (throw (doto (JMException. msg) (.initCause e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- start-rmi

  [plug]

  (try (let [{:keys [registry-port]} (xp/gconf plug)]
         {:rmi (LocateRegistry/createRegistry ^long registry-port)})
       (catch Throwable _ (mk-jmxrror "Failed to create RMI registry" _))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- SVC "service:jmx:rmi://{{h}}:{{s}}/jndi/rmi://{{h}}:{{r}}/jmxrmi")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/def- CFC "com.sun.jndi.rmi.registry.RegistryContextFactory")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- start-jmx

  [plug]

  (let [{:keys [registry-port server-port
                host url context-factory]} (xp/gconf plug)
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
    (l/debug "jmx service url: %s." endpt)
    (.start conn)
    {:conn conn :bean-svr (.getMBeanServer conn)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- doreg

  [^MBeanServer svr ^ObjectName objName ^DynamicMBean mbean]

  (c/doto->> objName
             (.registerMBean svr mbean) (l/info "jmx-bean: %s.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- pluglet

  [server _id spec]

  (let [impl (atom {:info (:info spec)
                    :conf (:conf spec)})]
    (reify
      xp/Pluglet
      (user-handler [_] (get-in @impl [:conf :$handler]))
      (gconf [_] (:conf @impl))
      (err-handler [_]
        (get-in @impl [:conf :$error]))
      xp/JmxPluglet
      (jmx-dereg [_ nname]
        (-> ^MBeanServer
            (:bean-svr @impl) (.unregisterMBean nname)))
      (jmx-reg [_ obj domain nname paths]
        (let [{:keys [bean-svr]} @impl
              nm (object-name<> domain nname paths)]
          (swap! impl
                 update-in
                 [:obj-names]
                 conj
                 (doreg bean-svr
                        nm
                        (bn/jmx-bean<> obj))) nm))
      po/Hierarchical
      (parent [_] server)
      po/Idable
      (id [_] _id)
      po/Resetable
      (reset [me]
        (doseq [nm (:obj-names @impl)]
          (c/try! (xp/jmx-dereg me nm)))
        (swap! impl assoc :obj-names []))
      po/Initable
      (init [me arg]
        (swap! impl
               update-in
               [:conf]
               #(-> (merge % arg)
                    b/expand-vars* b/prevar-cfg)) me)
      po/Finzable
      (finz [me]
        (po/stop me)
        (l/info "jmx pluglet disposed - ok.") me)
      po/Startable
      (start [_] (po/start _ nil))
      (start [me _]
        (swap! impl
               merge (start-rmi me) (start-jmx me))
        (l/info "JmxPluglet started - ok.")
        me)
      (stop [me]
        (let [{:keys [^Registry rmi
                      ^JMXConnectorServer conn]} @impl]
          (po/reset me)
          (c/try! (some-> conn (po/stop)))
          (u/try!!!
            (some-> rmi
                    (UnicastRemoteObject/unexportObject  true)))
          (l/info "jmx pluglet stopped - ok.")
          me)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def JMXSpec
  {:info {:name "JMX Server"
          :version "1.0.0"}
   :conf {:$pluggable ::jmx-monitor<>
          :registry-port 7777
          :server-port 7778
          :host ""
          :$handler nil}})

;;:host (-> (InetAddress/getLocalHost) .getHostName)})
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn jmx-monitor<>

  [ctr pid] (pluglet ctr pid JMXSpec))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; jconsole port
;;(setRegistryPort [_ p] (.setv impl :registryPort p))
;;(setServerPort[_ p] (.setv impl :serverPort p))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

