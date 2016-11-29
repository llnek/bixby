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

  czlab.wabbit.sys.jmx

  (:require
    [czlab.xlib.logging :as log]
    [clojure.string :as cs])

  (:use [czlab.wabbit.sys.bean]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import
    [java.net InetAddress MalformedURLException]
    [java.rmi.registry LocateRegistry Registry]
    [java.lang.management ManagementFactory]
    [java.rmi.server UnicastRemoteObject]
    [czlab.wabbit.server JmxServer]
    [java.rmi NoSuchObjectException]
    [czlab.xlib Startable Muble]
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
;;
(defn objectName<>

  "paths: [ \"a=b\" \"c=d\" ]
   domain: com.acme
   beanName: mybean"
  {:tag ObjectName}

  ([domain beanName] (objectName<> domain beanName nil))
  ([^String domain ^String beanName paths]
   (let [cs (seq (or paths []))
         sb (strbf<>)]
     (doto sb
       (.append domain)
       (.append ":")
       (.append (cs/join "," cs)))
     (if-not (empty? cs) (.append sb ","))
     (doto sb
       (.append "name=")
       (.append beanName))
     (ObjectName. (.toString sb)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkJMXrror

  ""
  [^String msg ^Throwable e]

  (throw (doto (JMException. msg) (.initCause e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startRMI

  ""
  [^Muble impl]

  (try
    (->> (long (.getv impl :registryPort))
         (LocateRegistry/createRegistry )
         (.setv impl :rmi ))
    (catch Throwable e#
      (mkJMXrror "Failed to create RMI registry" e#))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startJMX

  ""
  [^Muble impl]

  (let
    [cfc "com.sun.jndi.rmi.registry.RegistryContextFactory"
     svc (str "service:jmx:rmi://{{h}}:{{s}}"
              "/jndi/rmi://{{h}}:{{r}}/jmxrmi")
     {:keys [registryPort serverPort
             host url contextFactory]}
     (.impl impl)
     env (HashMap.)
     endpt (-> (cs/replace (stror url svc) "{{h}}" host)
               (cs/replace "{{s}}" (str serverPort))
               (cs/replace "{{r}}" (str registryPort)))]
    (log/debug "jmx service url: %s" endpt)
    (.put env
          "java.naming.factory.initial"
          (stror contextFactory cfc))
    (let
      [conn (JMXConnectorServerFactory/newJMXConnectorServer
              (JMXServiceURL. endpt)
              env
              (ManagementFactory/getPlatformMBeanServer))]
      (.start conn)
      (doto impl
        (.setv :beanSvr (.getMBeanServer conn))
        (.setv :conn conn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doReg

  ""
  [^MBeanServer svr ^ObjectName objName ^DynamicMBean mbean ]

  (.registerMBean svr mbean objName)
  (log/info "jmx-bean: %s" objName)
  objName)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn jmxServer<>

  ""
  ^JmxServer
  [cfg]

  (log/debug "jmxServer config =\n%s" cfg)
  (let
    [impl (muble<> (merge
                     {:registryPort 7777
                      :serverPort 7778
                      :host (-> (InetAddress/getLocalHost)
                                (.getHostName))} cfg))
     objNames (atom [])]
    (reify JmxServer

      (reset [this]
        (let [bs (.getv impl :beanSvr)]
          (doseq [nm @objNames]
            (try!
              (.dereg this nm)))
          (reset! objNames [])))

      (dereg [_ objName]
        (let [bs (.getv impl :beanSvr)]
          (-> ^MBeanServer bs
              (.unregisterMBean objName))))

      (reg [_ obj domain nname paths]
        (let [nm (objectName<> domain nname paths)
              bs (.getv impl :beanSvr)]
          (->> (doReg bs nm (mkJmxBean obj))
               (swap! objNames conj))
          nm))

      ;; jconsole port
      (setRegistryPort [_ p] (.setv impl :registryPort p))
      (setServerPort[_ p] (.setv impl :serverPort p))

      (start [_] (startRMI impl) (startJMX impl))

      (stop [this]
        (let [^JMXConnectorServer c (.getv impl :conn)
              ^Registry r (.getv impl :rmi)]
          (.reset this)
          (if (some? c) (try! (.stop c)))
          (.setv impl :conn nil)
          (if (some? r)
            (try!
              (UnicastRemoteObject/unexportObject r true)))
          (.unsetv impl :rmi))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

