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
    [czlab.xlib.util.core :refer [MubleObj! try! tryc]]
    [czlab.xlib.util.str :refer [hgl? stror]]
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
(defn JmxServer* ""

  ^JMXServer
  [^String host]

  (let
    [impl (MubleObj! {:regoPort 7777
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
                               (ObjectName* domain nname paths)
                               (JmxBean* obj))))))

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
          (.setv impl :rmi nil))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

