(ns ^:{}

  cmzlabclj.odin.handler.wsstrategy

  (:use [cmzlabclj.odin.services])

  (:import (com.zotohlab.odin.game GameRoom Player PlayerSession Session))
  (:import (com.zotohlab.odin.network NettyTCPMessageSender))
  (:import (com.zotohlab.odin.event Event Events ReconnetEvent))
  (:import (com.zotohlab.odin.core Config))
  (:import (com.zotohlab.odin.handler WebSockStrategy))
  (:import (io.netty.channel Channel ChannelFuture ChannelFutureListener
                             ChannelHandlerContext SimpleChannelInboundHandler))
  (:import (io.netty.handler.codec.http.websocketx TextWebSocketFrame))
  (:import (java.util List)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- eventToFrame ""
  
  [code body]

  (let [ evt (ReifyEvent code body) ]
    (TextWebSocketFrame (Jsonify evt))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doLoginError ""

  [^Channel ch]

  (-> (.writeAndFlush ch (eventToFrame Events/LOG_IN_ERROR nil))
      (.addListener ChannelFutureListener/CLOSE)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doRejoin ""
  
  [^PlayerSession ps ^GameRoom rm ^Channel ch]

  (let [ sender (NettyTCPMessageSender. ch) ]
    (.setTcpSender ps sender)
    (.connectSession rm ps)
    (.writeAndFlush ch (eventToFrame Events/GAME_ROOM_JOIN_OK, nil))
    (.setWriteable ps true)
    // send the re-connect event so that it will in turn send the START event.
    (.onEvent ps (ReconnectEvent. sender))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doReconnect ""

  [^PlayerSession ps ^Channel ch]

  (if (nil? ps)
    (doLoginError ch)
    (let [ f (eventToFrame Events/LOG_IN_OK nil)
           cf (.writeAndFlush ch f)
           rm (doto (.getGameRoom ps)
                    (.disconnectSession ps))
           tcp (.getTcpSender ps) ]
      (when-not (nil? tcp)
        (.close tcp))
      (doRejoin ps rm ch))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doLogin ""
  
  [^Player player ^Channel ch]

  (if (nil? player)
    (doLoginError ch)
    (.writeAndFlush ch (eventToFrame Events/LOG_IN_OK nil))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- connectToGameRoom ""
  
  [^GameRoom rm ^PlayerSession ps ^ChannelFuture cf]

  (.addListener
    cf
    (reify ChannelFutureListener
      (operationComplete [cff]
        (let [ ch (.channel cf) ]
          (if (.isSuccess ^ChannelFuture cff)
            (let [ tcp (NettyTCPMessageSender. ch) ]
              (log/debug "GAME_ROOM_JOIN_OK successfully sent to " ch)
              (.setTcpSender ps tcp)
              (.connectSession rm ps)
              (.sendMessage tcp (Events/event nil Events/START))
              (.onLogin rm ps))
            (do
              (log/error "GAME_ROOM_JOIN_OK to client failed.")
              (.close ch))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doJoinRoom ""

  [^Player player ^Channel ch options]

  (cond
    (nil? player)
    nil
    :else
    (if-let [ rm (LookupGameRoom (:refKey options)) ]
      (let [ ps (.createPlayerSession ps player)
             rcKey (GenerateReconKey) ]
        (.setAttr ps (Config/RECONNECT_KEY rcKey))
        (.setAttr ps (Config/RECONNECT_REGISTRY reconnectRegistry))
        (log/debug "sending GAME_ROOM_JOIN_OK to channel " ch)
        (->> (.writeAndFlush ch (eventToFrame Events/GAME_ROOM_JOIN_OK rcKey))
             (connectToGameRoom rm ps)))
      (do
        (log/error "invalid reconnect key provided " options " by " ch)
        (-> (.writeAndFlush ch (eventToFrame Events/GAME_ROOM_JOIN_ERROR nil))
            (.addListener ChannelFutureListener/CLOSE))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyWebSockStrategy ""

  ^WebSockStrategy
  []

  (let []
    (proxy WebSockStrategy [][]
      (channelRead0 [ctx msg]
        (let [ ch (.channel ^ChannelHandlerContext ctx)
               who (.remoteAddress ch)
               data (.text ^TextWebSocketFrame msg)
               event (ReifyJsonEvent data)
               opts (.getData event) ]
          (log/debug "websocket event-json-str: " data)
          (case (.getType event)
            Events/RECONNECT
            (do
              (log/debug "reconnect request from " who)
              (-> (LookupSession (nsb opts))
                  (doReconnect ch)))
            Events/LOG_IN
            (do
              (log/debug "login request from " who)
              (doto (Finder/getPlayer opts)
                (doLogin ch)
                (doJoinRoom ch opts)))
            (do
              (log/error "invalid event " data " from " who)
              (doLoginError ch)))))

      )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private wsstrategy-eof nil)

