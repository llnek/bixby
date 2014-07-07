(ns ^{
      }

  cmzlabclj.odin.event.core

  (:import (com.zotohlab.odin.event Events EventContext 
                                    NetworkEvent Event))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 
(defn ReifyContextualEvent ""

  ^Event
  [^Object source eventType ^EventContext ctx]

  (doto (DefaultEvent.)
    (.setSource source)
    (.setType eventType)
    (.setContext ctx)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 
(defn ReifyEvent ""

  (^Event [^Object source eventType] (ReifyEvent source eventType nil))
  
  (^Event [^Object source eventType ^Session session]
          (ReifyContextualEvent source eventType
                                (if (nil? session)
                                  nil
                                  (DefaultEventContext.)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 
(defn ReifyNetworkEvent ""

  (^NetworkEvent [^Object source] (ReifyNetworkEvent source true))

  (^NetworkEvent [^Object source reliable?]
    (doto (DefaultNetworkEvent.)
      (.setReliable reliable?)
      (.setSource source))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyConnectEvent ""

  ^ConnectEvent 
  [^TCPSender tcp ^UDPSender udp]

  (DefaultConnectEvent. tcp udp))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;  
(defn ReifySessionMessage ""

  ^Event
  [^Object source]

  (ReifyEvent source Events/SESSION_MESSAGE))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 
(defn ReifyChangeAttrEvent ""

  [^Object attr ^Object value]

  (ChangeAttributeEvent. attr value))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onNetMsg ""

  [^Session session ^NetworkEvent evt]

  (cond
    (or (nil? session)
        (not (.isWritable session)))
    nil

    (.isReliable evt)
    (if-let [ s (.getTCPSender session) ]
      (.sendMessage s evt))

    :else
    (if-let [ s (.getUDPSender session) ]
      (.sendMessage s evt))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onData ""

  [^PlayerSession session ^Event evt]

  (when-not (nil? session)
    (let [ ne (ReifyNetworkEvent evt) ]
      (when (.isUDPEnabled session)
        (.setReliable ne false))
      (-> session (.getGameRoom)(.sendBroadcast ne)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onConnect ""

  [^Session session ^ConnectEvent evt]

  (when-not (nil? session)
    (let [ ss (.getTCPSender session)
           es (.getTCPSender evt) ]
      (if (notnil? es)
        (.setTCPSender session es)
        (if (nil? ss)
          (log/warn "TCP connection not fully established yet.")
          (do
            (.setUDPEnabled session true)
            (.setUDPSender session (.getUDPSender evt))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onReconnect

  [^Session session ^ConnectEvent evt]

  ;; To synchronize with task for closing session in ReconnectRegistry service.
  (when-not (nil? session)
    (CoreUtils/syncExec
      session
      (reify Callable
        (call [_]
          (if-let [ ^SessionRegistry reg (.getAttr Config/RECONNECT_REGISTRY) ]
            (when (not= Session/STATUS_CLOSED (.getStatus session))
              (.removeSession reg (.getAttr session Config/RECONNECT_KEY)))))))
    (onConnect session evt)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onError ""

  [^Session session ^Event evt]

  (when-not (nil? session)
    (.setStatus session Session/STATUS_NOT_CONNECTED)
    (.setWriteable session false)
    ;; will be set to true by udpupstream handler on connect event.
    (.setUDPEnabled session false)
    (let [ ^SessionRegistry rego (.getAttr session Config/RECONNECT_REGISTRY)
           ^String rckey (.getAttr session Config/RECONNECT_KEY) ]
      (if (and (hgl? rckey)
               (notnil? rego))
        (if (nil? (.getSession rego rckey))
          (.putSession rego  rckey session)
          (log/debug "received exception/disconnect event in session; "
                     "puting session in reconnection registry."))
        (do
          (log/debug "received exception/disconnect event in session; "
                     "closing session.")
          (.session close))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onEventXXX ""

  [^Session session ^Event evt]

  (let [ t (.getType evt) ]
    (cond
      (= t Events/SESSION_MESSAGE)
      (onData session evt)

      (= t Events/NETWORK_MESSAGE)
      (onNetMsg session evt)

      (or (= t Events/LOGIN_ERROR)
          (= t Events/LOGIN_OK))
      (when-not (nil? session)
        (if-let [ s (.getTCPSender session) ]
          (.sendMessage s evt)))

      (= t Events/LOGOUT)
      (when-not (nil? session)
        (.close session))

      (= t Events/CONNECT_ERROR)
      (log/error "connection failed!")

      (= t Events/CONNECT)
      (onConnect session evt)

      (= t Events/DISCONNECT)
      (onError session evt)

      (= t Events/RECONNECT)
      (onReconnect session evt)

      (or (= t Events/START)
          (= t Events/STOP))
      (when-not (nil? session)
        (if-let [ s (.getTCPSender session) ]
          (.sendMessage s evt)))

      (= t Events/CHANGE)
      (when-not (nil? session)
        (.setAttr session (.getKey evt) (.getValue evt)))

      (= t Events/ERROR)
      (onError session evt)

      :else
      (log/warn "Unknown event type")
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifySessionEventHandler ""

  [session]

  (let []
    (reify SessionEventHandler
      (onEvent [_ evt] (onEventXXX session evt))
      (getEventType [_] Events/ANY)
      (getSession [_] session)
      (setSession [_ s] (ThrowUOE "Can't reset session")))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

