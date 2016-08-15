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

  czlab.skaro.jmx.core

  (:require
    [czlab.xlib.core :refer [mubleObj! try! tryc]]
    [czlab.xlib.str :refer [hgl? stror]]
    [czlab.xlib.logging :as log]
    [clojure.string :as cs])

  (:use [czlab.skaro.jmx.names]
        [czlab.skaro.jmx.bean])

  (:import
    [java.net InetAddress MalformedURLException]
    [java.rmi.registry LocateRegistry Registry]
    [java.lang.management ManagementFactory]
    [czlab.skaro.runtime JMXServer]
    [java.rmi NoSuchObjectException]
    [czlab.xlib Startable Muble]
    [java.rmi.server UnicastRemoteObject]
    [javax.management DynamicMBean
     JMException
     MBeanServer ObjectName]
    [javax.management.remote JMXConnectorServer
     JMXConnectorServerFactory JMXServiceURL]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkJMXrror ""

  [^String msg ^Throwable e]

  (throw (doto (JMException. msg) (.initCause e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startRMI ""

  [^Muble impl]

  (try
    (->> (long (.getv impl :regoPort))
         (LocateRegistry/createRegistry )
         (.setv impl :rmi ))
    (catch Throwable e#
      (mkJMXrror "Failed to create RMI registry" e#))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startJMX ""

  [^Muble impl]

  (let [hn (-> (InetAddress/getLocalHost)
               (.getHostName))
        regoPort (.getv impl :regoPort)
        port (.getv impl :port)
        host (.getv impl :host)
        endpt (-> (str "service:jmx:rmi://{{h}}:{{s}}/"
                       "jndi/rmi://:{{r}}/jmxrmi")
                  (cs/replace "{{h}}" (stror host hn))
                  (cs/replace "{{s}}" (str "" port))
                  (cs/replace "{{r}}" (str "" regoPort)))
        url (JMXServiceURL. endpt)
        conn (JMXConnectorServerFactory/newJMXConnectorServer
               url nil
               (ManagementFactory/getPlatformMBeanServer))]
    (-> ^JMXConnectorServer
        conn (.start ))
    (.setv impl :beanSvr (.getMBeanServer conn))
    (.setv impl :conn conn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doReg ""

  [^MBeanServer svr ^ObjectName objName ^DynamicMBean mbean ]

  (.registerMBean svr mbean objName)
  (log/info "registered jmx-bean: %s" objName)
  objName)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn mkJmxServer ""

  ^JMXServer
  [^String host]

  (let
    [impl (mubleObj! {:regoPort 7777
                     :port 0})
     objNames (atom []) ]
    (reify

      JMXServer

      (reset [this]
        (let [bs (.getv impl :beanSvr) ]
          (doseq [nm @objNames]
            (try!
              (.dereg this nm)))
          (reset! objNames [])))

      (dereg [_ objName]
        (let [bs (.getv impl :beanSvr) ]
          (-> ^MBeanServer bs
              (.unregisterMBean objName))))

      (reg [_ obj domain nname paths]
        (let [bs (.getv impl :beanSvr) ]
          (reset! objNames
                  (conj @objNames
                        (doReg bs
                               (objectName domain nname paths)
                               (mkJmxBean obj))))))

      ;; jconsole port
      (setRegistryPort [_ port] (.setv impl :regoPort port))

      (setServerPort[_ port] (.setv impl :port port))

      (start [_]
        (let [p1 (.getv impl :regoPort)
              p2 (.getv impl :port) ]
          (when-not (> p2 0) (.setv impl :port (inc p1)))
          (startRMI impl)
          (startJMX impl)) )

      (stop [this]
        (let [^JMXConnectorServer c (.getv impl :conn)
              ^Registry r (.getv impl :rmi) ]
          (.reset this)
          (when (some? c) (tryc (.stop c)))
          (.setv impl :conn nil)
          (when (some? r)
            (tryc
              (UnicastRemoteObject/unexportObject r true)))
          (.setv impl :rmi nil))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

