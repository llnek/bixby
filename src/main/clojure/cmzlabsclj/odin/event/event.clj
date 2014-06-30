(ns ^{
      }

  cmzlabsclj.odin.event.event

  (:import (com.zotoh.odin.event Events EventContext NetworkEvent Event))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyEventContext ""

  (^EventContext [] (ReifyEventContext nil nil))
  (^EventContext [session tag]
    (let [ impl (MakeMMap) ]
      (.setf! impl :session session)
      (.setf! impl :tag tag)
      (reify EventContext
        (setSession [_ s] (.setf! impl :session s))
        (getSession [_] (.getf impl :session))
        (.setTag [_ obj] (.setf! impl :tag obj))
        (.getTag [_] (.getf impl :tag)))
    )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyEvent ""

  ^Event
  []

  (let [ impl (MakeMMap) ]
    (reify Event
      (setType [_ t] (.setf! impl :type t))
      (getType [_] (.getf impl :type))
      (setData [_ s] (.setf! impl :data s))
      (getData [_] (.getf impl :data))
      (setContext [_ x] (.setf! impl :ctx x))
      (getContext [_] (.getf impl :ctx))
      (setTimestamp[_ t] (.setf! impl :ts t))
      (getTimestamp [_] (.getf impl :ts)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyNetworkEvent ""

  (^NetworkEvent [^Event evt reliable?]
    (let [ ne (ReifyNetworkEvent) ]
      (.setReliable ne (if reliable? true false))
      (.setTimestamp ne (.getTimestamp evt))
      (.setData ne (.getData evt))
      (.setContext ne (.getContext evt))
      ne))
  (^NetworkEvent []
    (let [ impl (MakeMMap) ]
      (.setf! impl :type Events/NETWORK_MESSAGE)
      (.setf! impl :reliable true)
      (reify
        NetworkEvent
        (setReliable [_ r] (.setf! impl :reliable r))
        (isReliable [_] (.getf impl :reliable))

        Event
        (setType [_ t] (.setf! impl :type t))
        (getType [_] (.getf impl :type))
        (setData [_ s] (.setf! impl :data s))
        (getData [_] (.getf impl :data))
        (setContext [_ x] (.setf! impl :ctx x))
        (getContext [_] (.getf impl :ctx))
        (setTimestamp[_ t] (.setf! impl :ts t))
        (getTimestamp [_] (.getf impl :ts)))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeConnectXXXEvent ""

  []

  (let [ impl (MakeMMap) ]
    [
    (reify
      ConnectEvent
      (setSender [_ s] (.setf! impl :sender s))
      (getSender [_] (.getf impl :sender))

      Event
      (setType [_ t] nil) ;; can't do that
      (getType [_] (.getf impl :type))
      (setData [_ s] (.setf! impl :data s))
      (getData [_] (.getf impl :data))
      (setContext [_ x] (.setf! impl :ctx x))
      (getContext [_] (.getf impl :ctx))
      (setTimestamp[_ t] (.setf! impl :ts t))
      (getTimestamp [_] (.getf impl :ts)))
    impl
    ]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyReconnectEvent ""

  ^ReconnectEvent
  [sender]

  (let [ [evt impl] (makeConnectXXXEvent) ]
    (.setf! impl :type Events/RECONNECT)
    (.setSender evt sender)
    evt
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyConnectEvent ""

  ^ConnectEvent
  []

  (let [ [evt impl] (makeConnectXXXEvent) ]
    (.setf! impl :type Events/CONNECT)
    evt
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyChangeAttrEvent ""

  ^ChangeAttrEvent
  [attr value]

  (let [ impl (MakeMMap) ]
    (.setf! impl :type Events/CHANGE_ATTRIBUTE)
    (.setf! impl :attrvalue value)
    (.setf! impl :attr attr)
    (reify
      ChangeAttrEvent
      (getAttr [_]
        (if-let [ a (.getf impl :attr) ]
          [a (.getf impl :attrvalue)]
          nil))
      (setAttr [_ k v]
        (.setf! impl :attrvalue v)
        (.setf! impl :attr k))

      Event
      (setType [_ t] (.setf! impl :type t))
      (getType [_] (.getf impl :type))
      (setData [_ s] (.setf! impl :data s))
      (getData [_] (.getf impl :data))
      (setContext [_ x] (.setf! impl :ctx x))
      (getContext [_] (.getf impl :ctx))
      (setTimestamp[_ t] (.setf! impl :ts t))
      (getTimestamp [_] (.getf impl :ts)))
  ))



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
      (reify Runnable
        (run [_]
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
(def ^:private event-eof nil)

