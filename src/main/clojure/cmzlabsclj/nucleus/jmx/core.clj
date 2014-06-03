;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  cmzlabsclj.nucleus.jmx.core

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.util.core :only [MakeMMap Try! TryC] ])
  (:use [cmzlabsclj.nucleus.util.str :only [hgl? ] ])
  (:use [cmzlabsclj.nucleus.jmx.names])
  (:use [cmzlabsclj.nucleus.jmx.bean])
  (:import (java.lang.management ManagementFactory))
  (:import (java.net InetAddress MalformedURLException))
  (:import (java.rmi NoSuchObjectException))
  (:import (com.zotohlab.frwk.core Startable))
  (:import (java.rmi.registry LocateRegistry Registry))
  (:import (java.rmi.server UnicastRemoteObject))
  (:import (javax.management DynamicMBean JMException
                             MBeanServer ObjectName))
  (:import (javax.management.remote JMXConnectorServer
                                    JMXConnectorServerFactory
                                    JMXServiceURL))
  (:import (org.apache.commons.lang3 StringUtils)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkJMXrror ""

  [^String msg ^Throwable e]

  (throw (doto (JMException. msg) (.initCause e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startRMI ""

  [ ^cmzlabsclj.nucleus.util.core.MubleAPI impl]

  (let [ ^long port (.getf impl :regoPort) ]
    (try
      (.setf! impl :rmi (LocateRegistry/createRegistry port))
    (catch Throwable e#
      (mkJMXrror (str "Failed to create RMI registry: " port) e#)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startJMX ""

  [ ^cmzlabsclj.nucleus.util.core.MubleAPI impl]

  (let [ hn (-> (InetAddress/getLocalHost)(.getHostName))
         ^long regoPort (.getf impl :regoPort)
         ^long port (.getf impl :port)
         ^String host (.getf impl :host)
         endpt (-> "service:jmx:rmi://{{host}}:{{sport}}/jndi/rmi://:{{rport}}/jmxrmi"
                   (StringUtils/replace "{{host}}" (if (hgl? host) host hn))
                   (StringUtils/replace "{{sport}}" (str "" port))
                   (StringUtils/replace "{{rport}}" (str "" regoPort)))
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

    (.setf! impl :beanSvr (.getMBeanServer conn))
    (.setf! impl :conn conn)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doReg ""

  [ ^MBeanServer svr ^ObjectName objName ^DynamicMBean mbean ]

  (try
    (.registerMBean svr mbean objName)
    (catch Throwable e#
      (mkJMXrror (str "Failed to register bean: " objName) e#)))
  (log/info "registered jmx-bean: " objName)
  objName)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol JMXServer

  ""

  (reg [_ obj domain nname paths] )
  (setRegistryPort [_ port])
  (setServerPort [_ port])
  (reset [_] )
  (dereg [_ nm] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeJmxServer ""

  [ ^String host]

  (let [ objNames (atom [])
         impl (MakeMMap) ]
    (.setf! impl :regoPort 7777)
    (.setf! impl :port 0)
    (reify

      JMXServer

      (reset [_]
        (let [ ^MBeanServer bs (.getf impl :beanSvr) ]
          (doseq [ nm (seq @objNames) ]
            (Try!
               (.unregisterMBean bs nm)) )
          (reset! objNames [])))

      (dereg [_ objName]
        (let [ ^MBeanServer bs (.getf impl :beanSvr) ]
          (.unregisterMBean bs objName)))

      (reg [_ obj domain nname paths]
        (let [ ^MBeanServer bs (.getf impl :beanSvr) ]
          (try
            (reset! objNames
                    (conj @objNames
                          (doReg bs (MakeObjectName domain nname paths)
                                 (MakeJmxBean obj))))
            (catch Throwable e#
              (mkJMXrror (str "Failed to register object: " obj) e#)))))

      ;; jconsole port
      (setRegistryPort [_ port] (.setf! impl :regoPort port))

      (setServerPort[_ port] (.setf! impl :port port))

      Startable

      (start [_]
        (let [ p1 (.getf impl :regoPort)
               p2 (.getf impl :port) ]
          (when-not (> p2 0) (.setf! impl :port (inc p1)))
          (startRMI impl)
          (startJMX impl)) )

      (stop [this]
        (let [ ^JMXConnectorServer c (.getf impl :conn)
               ^Registry r (.getf impl :rmi) ]
          (reset this)
          (when-not (nil? c)
            (TryC (.stop c)))
          (.setf! impl :conn nil)
          (when-not (nil? r)
            (TryC
              (UnicastRemoteObject/unexportObject r true)))
          (.setf! impl :rmi nil)))

      )))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

