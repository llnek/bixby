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

  czlab.xlib.jmx.core

  (:require
    [czlab.xlib.util.core :refer [MubleObj try! tryc]]
    [czlab.xlib.util.str :refer [hgl? ]])

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs])

  (:use [czlab.xlib.jmx.names]
        [czlab.xlib.jmx.bean])

  (:import
    [java.net InetAddress MalformedURLException]
    [java.lang.management ManagementFactory]
    [com.zotohlab.skaro.runtime JMXServer]
    [java.rmi NoSuchObjectException]
    [com.zotohlab.frwk.core Startable]
    [com.zotohlab.skaro.core Muble]
    [java.rmi.registry LocateRegistry Registry]
    [java.rmi.server UnicastRemoteObject]
    [javax.management DynamicMBean
        JMException MBeanServer ObjectName]
    [javax.management.remote JMXConnectorServer
        JMXConnectorServerFactory JMXServiceURL]
    [org.apache.commons.lang3 StringUtils]))

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

  (let [^long port (.getv impl :regoPort) ]
    (try
      (.setv impl :rmi (LocateRegistry/createRegistry port))
      (catch Throwable e#
        (mkJMXrror (str "Failed to create RMI registry: " port) e#)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startJMX ""

  [^Muble impl]

  (let [hn (-> (InetAddress/getLocalHost)
               (.getHostName))
        ^long regoPort (.getv impl :regoPort)
        ^long port (.getv impl :port)
        ^String host (.getv impl :host)
        endpt (-> "service:jmx:rmi://{{h}}:{{s}}/jndi/rmi://:{{r}}/jmxrmi"
                  (cs/replace "{{h}}" (if (hgl? host) host hn))
                  (cs/replace "{{s}}" (str "" port))
                  (cs/replace "{{r}}" (str "" regoPort)))
        url (try
              (JMXServiceURL. endpt)
              (catch Throwable e#
                (mkJMXrror (str "Malformed url: " endpt) e#)))
        ^JMXConnectorServer
        conn (try
               (JMXConnectorServerFactory/newJMXConnectorServer
                 url
                 nil
                 (ManagementFactory/getPlatformMBeanServer))
               (catch Throwable e#
                 (mkJMXrror (str "Failed to connect JMX") e#))) ]
    (try
      (.start conn)
      (catch Throwable e#
        (mkJMXrror (str "Failed to start JMX") e#)))

    (.setv impl :beanSvr (.getMBeanServer conn))
    (.setv impl :conn conn)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doReg ""

  [^MBeanServer svr ^ObjectName objName ^DynamicMBean mbean ]

  (try
    (.registerMBean svr mbean objName)
    (catch Throwable e#
      (mkJMXrror (str "Failed to register bean: " objName) e#)))
  (log/info "registered jmx-bean: %s" objName)
  objName)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn JmxServer* ""

  ^JMXServer
  [^String host]

  (let
    [impl (MubleObj {:regoPort 7777
                     :port 0})
     objNames (atom []) ]
    (reify

      JMXServer

      (reset [_]
        (let [^MBeanServer
              bs (.getv impl :beanSvr) ]
          (doseq [nm @objNames]
            (try!
               (.unregisterMBean bs nm)) )
          (reset! objNames [])))

      (dereg [_ objName]
        (let [^MBeanServer
              bs (.getv impl :beanSvr) ]
          (.unregisterMBean bs objName)))

      (reg [_ obj domain nname paths]
        (let [^MBeanServer
              bs (.getv impl :beanSvr) ]
          (try
            (reset! objNames
                    (conj @objNames
                          (doReg bs (MakeObjectName domain nname paths)
                                 (MakeJmxBean obj))))
            (catch Throwable e#
              (mkJMXrror (str "Failed to register object: " obj) e#)))))

      ;; jconsole port
      (setRegistryPort [_ port] (.setv impl :regoPort port))

      (setServerPort[_ port] (.setv impl :port port))

      Startable

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
          (.setv impl :rmi nil))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

