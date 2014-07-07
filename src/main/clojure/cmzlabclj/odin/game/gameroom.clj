
(ns ^{

      }

  cmzlabclj.odin.game.room

  (:import ()))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postConnect ""

  [^GameRoom rm ^PlayerSession ps]

  (if-let [ sm (.getStateManager rm) ]
    (if-let [ obj (.getState sm) ]
      (.onEvent ps (ReifyNetworkEvent obj)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeRoom ""

  ^GameRoom
  [^GameRoom rm ^Player py]

  (let [ id (GenerateUID (class GameRoom))
         created (System/currentTimeMillis)
         stateMgr (StateManager.)
         sessions (MakeMMap)
         impl (MakeMMap) ]
    (.setf! impl :status Session$Status/NOT_CONNECTED)
    (.setf! impl :shutting-down false)
    (.setf! impl :write true)
    (.setf! impl :udpok false)
    (.setf! impl :created created)
    (.setf! impl :lastrw created)
    [
    (reify GameRoom
      (disconnect [this ps]
        (CoreUtils/syncExec this
          (reify Callable
            (call [_]
              (.removeHandlers disp ps)
              (.clrf! sessions (.getId ps))))))

      (connect [this ps]
        (if (.isShuttingDown this)
          (do
            (log/debug "room is shutting down, refuse to connect " ps)
            false)
          (CoreUtils/syncExec this (reify Callable (call [_]
            (cond
              (notnil? (.getf sessions (.getId ps)))
              (do
                (log/debug "attempt to connect to room twice, refuse to connect " ps)
                false)
              :else
              (do
                (.setStatus ps Session$Status/CONNECTING)
                (.setf! sessions (.getId ps) ps)
                (.setGameRoom ps this)
                (.applyProtocol protocol ps true)
                (createAndAddHandlers ps)
                (.setStatus ps Session$Status/CONNECTED)
                (postConnect this ps)
                true)))))))

      (broadcast [this evt] (.onEvent this evt))
      (post [this evt] (.onEvent this evt))

      Session
      (.isShuttingDown [_] (.getf impl :shutting-down))
      (setAttr [_ k v] (setf! impl k v))
      (removeAttr [_ n] (clrf! impl n))
      (getAttr [_ k] (getf impl k))
      (getId [_] id)
      (getEventDispatcher [_] disp)
      (onEvent [_ evt]
        (when-not (getf impl :shutting-down)
          (.fireEvent disp evt)))
      (removeHandler [_ h] (.remove disp h))
      (addHandler [_ h] (.add disp h))
      (getHandlers [_ etype] (.getHandlers disp etype))
      (.setTcpSender [_ s] (.setf! impl :tcp s))
      (.getTcpSender [_] (.getf impl :tcp))
      (.setUdpSender [_ s] (.setf! impl :udp s))
      (.getUdpSender [_] (.getf impl :udp))
      (.setStatus [_ s] (.setf! impl :status s))
      (.getStatus [_] (.getf impl :status))
      (.setUdpEnabled [_ b] (.setf! impl :udpok b))
      (.isUdpEnabled [_] (.getf impl :udpok))
      (.setWritable [_ w] (.setf! impl :write w))
      (.isWritable [_] (.getf impl :write))
      (.isConnected [_] (= Session$Status/CONNECTED
                           (.getf impl :status)))
      (.isClosed [_] (= Session$Status/CONNECTED
                        (.getf impl :status)))
      (.getLastRWTime [_] (.getf impl :lastrw))
      (close [this]
        (CoreUtils/syncExec
          this
          (reify Callable
            (call [_]
              (when-not (.isClosed this)
                (.setf! impl :shutting-down true)
                (.close disp)
                (when-let [ s (.getf impl :tcp) ]
                  (.shutdown s))
                (.clrf! impl :tcp)
                (when-let [ s (.getf impl :udp) ]
                  (.shutdown s))
                (.clrf! impl :udp)
                (doseq [ [ k ps] (.seq* sessions) ]
                  (.close ps))
                (.clear! sessions)
                (.setf! impl :shutting-down false)
                (.setf! impl :status Session$Status/CLOSED))))))

      Object

      (hashCode [this]
        (if-let [ n (.getId this) ]
          (.hashCode n)
          31))

      (equals [this obj]
        (if (nil? obj)
          false
          (or (identical? this obj)
              (and (= (.getClass this)
                      (.getClass obj))
                   (= (.getId ^Session obj)
                      (.getId this))))))

    )
    impl
    ]
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyGameRoom ""

  ^GameRoom
  [^GameRoom rm ^Player py]

  (let [ [ rm impl ] (makeRoom rm py)
         disp (ReifyJetlangEventDispatcher rm LaneStrategy/GROUP_BY_ROOM) ]
    (.setf! impl :disp disp)
    rm
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private room-eof nil)

