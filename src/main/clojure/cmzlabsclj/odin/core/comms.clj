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


  cmzlabsclj.odin.core.comms

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])

  (:import (io.nadron.handlers.netty.LoginProtocol))
  (:import (io.nadron.handlers.netty.ProtocolMultiplexerDecoder))
  (:import (io.netty.channel ChannelHandler ChannelInitializer
                             Channel ChannelPipeline))
  (:import (io.netty.handler.timeout IdleStateHandler))
  (:import (com.zotohlab.frwk.netty ServerSide PipelineConfigurator
                                     SSLServerHShake))
  (:import (com.zotohlab.frwk.netty NettyFW))
  (:import (com.google.gson JsonObject JsonElement)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

(def ^:private MAX-IDLE-SECS 60)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn makeProtocolMuxInitializer ""

  ^PipelineConfigurator
  []

  (proxy [PipelineConfigurator][]
    (assemble [p o]
      (let [ ^ChannelPipeline pipe p
             ^JsonObject options o
             ssl (SSLServerHShake/getInstance options) ]
        (doto pipe
          (.addLast "idleStateCheck" (IdleStateHandler. MAX-IDLE-SECS,
                                                        MAX-IDLE-SECS,
                                                        MAX-IDLE-SECS))
          (.addLast "multiplexer" (ProtocolMuxDecoder. bytesForProtocolCheck loginProtocol)))))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn makeUDPInitializer ""

  ^PipelineConfigurator
  [updstreamHandler updEncoder]

  (proxy [PipelineConfigurator][]
    (assemble [p o]
      (let [ ^ChannelPipeline pipe p
             ^JsonObject options ]
        (doto pipe
          (.addLast "upstream" (updstreamHandler options) )
          (.addLast "updEncoder" (updEncoder options)))))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private comms-eof nil)


