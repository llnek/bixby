(ns ^:{

       }

  cmzlabsclj.odin.network.senders

  (:import (com.zotohlab.odin.network MessageSender))
  (:import (com.zotohlab.odin.event Events Event))
  (:import (io.netty.channel Channel ChannelFutureListener)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyReliableSender ""

  ^TCPSender
  [^Channel ch]

  (let []
    (reify TCPSender
      (sendMessage [_ msg]
        (.writeAndFlush ch msg))
      (isReliable [_] true)
      (shutdown [this]
        (let [ evt (ReifyEvent nil Events/DISCONNECT) ]
          (log/debug "going to close tcp connection in object: " this)
          (if (.isActive ch)
            (-> (.write ch evt)
                (.addListener ChannelFutureListener/CLOSE))
            (do
              (log/warn "failed to write to socket with event " evt)
              (.close ch))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReifyFastSender ""

  ^UDPSender
  [^SocketAddress remote
   ^DatagramChannel ch
   ^SessionRegistry rego]

  (let []
    (reify UDPSender
      (sendMessage [_ msg]
        (.writeAndFlush ch msg))
      (isReliable [_] false)
      (shutdown [_]
        (.removeSession rego remote)))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private senders-eof nil)

