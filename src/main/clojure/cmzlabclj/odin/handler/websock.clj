(ns

  cmzlabclj.odin.handlers

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.List;

  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- eventToFrame ""

  ^TextWebSocketFrame
  [opcode payload]

  (let [ event (Events/event payload opcode) ]
    (TextWebSocketFrame. (json/write-str event))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeChannelWithConnectFailure ""

  [^Channel ch]

  (-> (.writeAndFlush ch (eventToFrame Events/LOG_IN_FAILURE nil))
      (.addListener ChannelFutureListener/CLOSE)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn LookupPlayer ""

  ^Player
  [^LookupService svc options]

  (let [ player (.playerLookup svc options) ]
    (when (nil? player)
      (log/error "invalid credentials provided by user " options))
    player
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn LookupSession ""

  ^PlayerSession
  [^String reconnectKey]

  (when-let [ playerSession  (reconnectRegistry/getSession reconnectKey) ]
      ;;synchronized(playerSession)
        ;; if its an already active session then do not allow a
        ;; reconnect. So the only state in which a client is allowed to
        ;; reconnect is if it is "NOT_CONNECTED"
    (if (= (.status playerSession) Session/STATUS_NOT_CONNECTED)
      (do
        (.setStatus! playerSession Session/STATUS_CONNECTING)
        playerSession)
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HandleLogin ""

  [^Player player ^Channel ch]

  (if (nil? player)
    (closeChannelWithConnectFailure ch)
    (.writeAndFlush ch (eventToFrame Events/LOG_IN_SUCCESS nil))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HandleReconnect ""

  [^PlayerSession playerSession ^Channel ch]

  (if (nil? playerSession)
    (closeChannelWithLoginFailure ch)
    (do
      (.writeAndFlush ch (eventToFrame Events/LOG_IN_SUCCESS nil))
      (when-let [ room  (playerSession/getGameRoom) ]
        (.disconnectSession room playerSession))
      (when-let [ tcp (.getTcpSender playerSession) ]
        (.close tcp))
      (handleReJoin playerSession room ch))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HandleReJoin ""

  [^PlayerSession playerSession ^GameRoom room ^Channel ch]

  (let [ sender (NettyTCPMessageSender. ch) ]
    (.setTcpSender playSession sender)
    ;; connect the pipeline to the game room.
    (.connectSession room playerSession)
    (.writeAndFlush ch Events/GAME_ROOM_JOIN_SUCCESS nil);;assumes that the protocol applied will take care of event objects.
    (.setWriteable playerSession true) ;; TODO remove if unnecessary. It should be done in start event
    ;; send the re-connect event so that it will in turn send the START event.
    (.onEvent playerSession (ReconnetEvent. sender))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HandleGameRoomJoin ""

  [^LookupService svc ^Player player ^Channel ch ^String refKey]

  (if-let [ room (.gameRoomLookup svc refKey) ]
    (let [ playerSession  (.createPlayerSession room  player)
           reconnectKey (IdGeneratorService/generateFor (.getClass playerSession)) ]
      (.setAttribute playerSession Config/RECONNECT_KEY reconnectKey)
      (.setAttribute playerSession Config/RECONNECT_REGISTRY reconnectRegistry)
      (log/info "sending GAME_ROOM_JOIN_SUCCESS to channel " ch)
      (->> (.writeAndFlush ch (eventToFrame Events/GAME_ROOM_JOIN_SUCCESS
                                           reconnectKey))
        (ConnectToGameRoom room playerSession)))
    (do
      (log/error "invalid ref key provided by client: " refKey)
      (-> (.writeAndFlush ch (eventToFrame Events/GAME_ROOM_JOIN_FAILURE
                                           nil))
          (addListener ChannelFutureListener/CLOSE)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ConnectToGameRoom ""

  [^GameRoom room ^PlayerSession playerSession ^ChannelFuture cf]

  (.addListener
    cf
    (reify ChannelFutureListener
      (operationComplete [cff]
        (let [ ch (.channel ^ChannelFuture cff) ]
          (log/info "completed GAME_ROOM_JOIN_SUCCESS to channel " ch)
          (if (.isSuccess cff)
            (let [ tcp (NettyTCPMessageSender. ch) ]
              (.setTcpSender playerSession tcp)
              (.connectSession room playerSession)
              (.sendMessage tcp (Events/event nil Events/START))
              (.onLogin room playerSession))
            (do
              (log/error "failed GAME_ROOM_JOIN_SUCCESS to client " ch)
              (.close ch))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeWebSockHandler ""

  []

  (let []
    (proxy WEBSockHandler[][]
      (channelRead0  [ctx frame]
        (let [ ch (.channel ^ChannelHandlerContext ctx)
               data (.text ^TextWebSocketFrame frame)
               json  (TryC (json/read-str data :keyword))
               client (.removeAddress ch)
               evt (:type json) ]
          (log/debug "from websocket-text-frame = " data)
          (cond
            (= evt Events/LOG_IN)
            (let [ creds (:source evt) ]
              (log/debug "login attempt from " client)
              (if-let [ player (LookupPlayer (:0 creds) (:1 creds)) ]
                (do
                  (HandleLogin player ch)
                  (HandleGameRoomJoin player ch (:2 creds)))
                (do fail)))

            (= evt Events/RECONNECT)
            (do
              (log/debug "reconnect attempt from " client)
              (if-let [ playerSession (LookupSession (:source evt)) ]
                (HandleReconnect playerSession ch)
                (do fail)))

            :else
            (do
              (log/error "invalid event sent from remote address " client)
              (closeChannelWithConnectFailure ch)))
          ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private websock-eof nil)

