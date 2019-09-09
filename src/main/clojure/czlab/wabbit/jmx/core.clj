;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.jmx.core

  (:require [czlab.wabbit.jmx.bean :as bn]
            [czlab.basal.log :as l]
            [clojure.string :as cs]
            [czlab.wabbit.core :as b]
            [czlab.wabbit.xpis :as xp]
            [czlab.basal.core :as c]
            [czlab.basal.str :as s]
            [czlab.basal.util :as u]
            [czlab.basal.proto :as po])

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
         sb (s/sbf<> domain ":" (cs/join "," cs))]
     (if-not (empty? cs) (s/sbf+ sb ","))
     (ObjectName. (str (s/sbf+ sb "name=" beanName))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- mk-jmxrror
  [^String msg ^Throwable e]
  (throw (doto (JMException. msg) (.initCause e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- start-rmi
  [plug]
  (try (let [{:keys [registry-port]} (xp/get-conf plug)]
         {:rmi (LocateRegistry/createRegistry ^long registry-port)})
       (catch Throwable _ (mk-jmxrror "Failed to create RMI registry" _))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^:private SVC "service:jmx:rmi://{{h}}:{{s}}/jndi/rmi://{{h}}:{{r}}/jmxrmi")
(def ^:private CFC "com.sun.jndi.rmi.registry.RegistryContextFactory")
(defn- start-jmx
  [plug]
  (let [{:keys [registry-port server-port
                host url context-factory]} (xp/get-conf plug)
        host (s/stror host
                      (-> (InetAddress/getLocalHost) .getHostName))
        endpt (-> (cs/replace (s/stror url SVC) "{{h}}" host)
                  (cs/replace "{{s}}" (str server-port))
                  (cs/replace "{{r}}" (str registry-port)))
        conn (JMXConnectorServerFactory/newJMXConnectorServer
               (JMXServiceURL. endpt)
               ^Map (u/x->java {"java.naming.factory.initial"
                                (s/stror context-factory CFC)})
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
      (user-handler [_] (get-in @impl [:conf :handler]))
      (get-conf [_] (:conf @impl))
      (err-handler [_]
        (or (get-in @impl
                    [:conf :error]) (:error spec)))
      xp/JmxPluglet
      (jmx-dereg [_ nname]
        (-> ^MBeanServer
            (:bean-svr @impl) (.unregisterMBean nname)))
      (jmx-reg [_ obj domain nname paths]
        (let [{:keys [bean-svr]} @impl
              nm (object-name<> domain nname paths)]
          (swap! impl
                 (c/fn_1 (update-in ____1
                                    [:obj-names]
                                    conj
                                    (doreg bean-svr
                                           nm
                                           (bn/jmx-bean<> obj))))) nm))
      po/Hierarchical
      (parent [_] server)
      po/Idable
      (id [_] _id)
      po/Resetable
      (reset! [me]
        (doseq [nm (:obj-names @impl)]
          (c/try! (xp/jmx-dereg me nm)))
        (swap! impl #(assoc % :obj-names [])))
      po/Initable
      (init [_ arg]
        (swap! impl
               (c/fn_1 (update-in ____1
                                  [:conf]
                                  #(b/prevar-cfg (merge % arg))))))
      po/Finzable
      (finz [me]
        (po/stop me)
        (l/info "jmx pluglet disposed - ok."))
      po/Startable
      (start [_] (po/start _ nil))
      (start [me _]
        (let [rmi (start-rmi me)
              [conn bsvr] (start-jmx me)]
          (swap! impl
                 #(merge % (start-rmi me) (start-jmx me)))
          (l/info "JmxPluglet started - ok.")))
      (stop [me]
        (let [{:keys [^Registry rmi
                      ^JMXConnectorServer conn]} @impl]
          (po/reset! me)
          (c/try! (some-> conn (po/stop)))
          (u/try!!!
            (some-> rmi
                    (UnicastRemoteObject/unexportObject  true)))
          (l/info "jmx pluglet stopped - ok."))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def JMXSpec {:info {:name "JMX Server"
                     :version "1.0.0"}
              :conf {:$pluggable ::jmx-monitor<>
                     :registry-port 7777
                     :server-port 7778
                     :host ""
                     :handler nil}})

;;:host (-> (InetAddress/getLocalHost) .getHostName)})
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn jmx-monitor<>
  "" [ctr pid]
  (pluglet ctr pid (update-in JMXSpec
                              [:conf] b/expand-vars-in-form)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; jconsole port
;;(setRegistryPort [_ p] (.setv impl :registryPort p))
;;(setServerPort[_ p] (.setv impl :serverPort p))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

