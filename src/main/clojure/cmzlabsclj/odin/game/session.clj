
(ns ^{}

  cmzlabsclj.odin.game.session

  (:import (com.zotohlab.frwk.core CoreUtils))
  (:import (com.zotohlab.odin.game Session Session$Status)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;

(defn ReifySession ""

  ^Session
  []

  (let [ disp (ReifyJetlangEventDispatcher nil nil)
         id (GenerateUID (class Session)) 
         created (System/currentTimeMillis)
         impl (MakeMMap) ]
    (.setf! impl :status Session$Status/NOT_CONNECTED)
    (.setf! impl :shutting-down false)
    (.setf! impl :write true)
    (.setf! impl :udpok false)
    (.setf! impl :created created)
    (.setf! impl :lastrw created)
    (reify Session
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
      (.getLastRWTime [_] (.getf impl :lastrw))
      (close [this]
        (CoreUtils/syncExec
          this
          (reify Callable
            (call [_]
              (.setf! impl :shutting-down true)
              (.close disp)
              (when-let [ s (.getf impl :tcp) ]
                (.shutdown s))
              (.clrf! impl :tcp)
              (when-let [ s (.getf impl :udp) ]
                (.shutdown s))
              (.clrf! impl :udp)
              (.setf! impl :status Session$Status/CLOSED)))))

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

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private session-eof nil)

