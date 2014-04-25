;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2014 Cherimoia, LLC. All rights reserved.


(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.netty.server

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:import (java.net InetAddress InetSocketAddress))
  (:import (io.netty.channel.nio NioEventLoopGroup))
  (:import (io.netty.bootstrap ServerBootstrap))
  (:import (io.netty.channel ChannelHandlerContext Channel ChannelOption
                             ChannelFutureListener ChannelFuture
                             ChannelPipeline ChannelHandler ))
  (:import (io.netty.handler.codec.http HttpHeaders HttpVersion ))
  (:use [comzotohlabscljc.util.core :only [notnil? Try! TryC] ])
  (:use [comzotohlabscljc.netty.comms])
  (::use [comzotohlabscljc.util.str :only [strim nsb hgl? nichts?] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Netty NIO Server
(defn BootstrapNetty ""

  ;; map
  [initor options]

  (let [ gp (NioEventLoopGroup.) gc (NioEventLoopGroup.)
         bs (doto (ServerBootstrap.)
                  (.group gp gc)
                  (.channel io.netty.channel.socket.nio.NioServerSocketChannel)
                  (.option ChannelOption/SO_REUSEADDR true)
                  (.option ChannelOption/SO_BACKLOG 100)
                  (.childOption ChannelOption/SO_RCVBUF (int (* 2 1024 1024)))
                  (.childOption ChannelOption/TCP_NODELAY true))
         opts (if-let [ x (:netty options) ] x {} ) ]
    (doseq [ [k v] (seq opts) ]
      (if (= :child k)
        (doseq [ [x y] (seq v) ]
          (.childOption bs x y))
        (.option bs k v)))
    (.childHandler bs (NettyChannelInitor initor options))
    { :bootstrap bs }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; start netty on host/port
(defn StartNetty ""

  [^String host port netty]

  (let [ ^InetAddress ip (if (nichts? host)
                             (InetAddress/getLocalHost)
                             (InetAddress/getByName host))
         ^ServerBootstrap boot (:bootstrap netty)
         ch (-> boot
                (.bind (InetSocketAddress. ip (int port)))
                (.sync)
                (.channel)) ]
    (log/debug "netty-xxx-server: running on host " ip ", port " port)
    (merge netty { :channel ch })
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn StopNetty "Clean up resources used by a netty server."

  [netty]

  (let [ ^ServerBootstrap bs (:bootstrap netty)
         gc (.childGroup bs)
         gp (.group bs)
         ^Channel ch (:channel netty) ]
    (-> (.close ch)
        (.addListener (reify ChannelFutureListener
                        (operationComplete [_ cff]
                          (when-not (nil? gp) (Try! (.shutdownGracefully gp)))
                          (when-not (nil? gc) (Try! (.shutdownGracefully gc))) )) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private server-eof nil)

