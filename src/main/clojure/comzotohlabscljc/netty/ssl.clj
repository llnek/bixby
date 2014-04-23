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

  comzotohlabscljc.netty.ssl

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])

  (:import (javax.net.ssl SSLEngine SSLContext))
  (:import (java.net URI URL))
  (:import (io.netty.channel Channel ChannelHandler ChannelPipeline))
  (:import (io.netty.handler.ssl SslHandler))

  (:use [comzotohlabscljc.crypto.ssl :only [MakeSslContext MakeSslClientCtx] ])
  (:use [comzotohlabscljc.util.core :only [notnil? Try! TryC] ])
  (:use [comzotohlabscljc.util.str :only [strim nsb hgl?] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; add SSL
(defn EnableUsrSSL ""

  ^ChannelHandler
  [options]

  (let [ ^SSLContext ctx (MakeSslClientCtx true)
         eg (if (notnil? ctx)
                (doto (.createSSLEngine ctx)
                      (.setUseClientMode true))) ]
    (if (nil? eg) nil (SslHandler. eg))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddEnableUsrSSL ""

  ^ChannelPipeline
  [pipe options]

  (let [ ssl (= (.getProtocol ^URL (:targetUrl options)) "https") ]
    (when ssl
      (.addLast ^ChannelPipeline pipe "ssl" (EnableUsrSSL options)))
    pipe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; add SSL
(defn EnableSvrSSL ""

  ^ChannelHandler
  [options]

  (let [ kf (:serverkey options)
         pw (:passwd options)
         ssl (if (nil? kf)
                 nil
                 (MakeSslContext kf pw))
         eg (if (nil? ssl)
                nil
                (doto (.createSSLEngine ssl)
                      (.setUseClientMode false))) ]
    (if (nil? eg) nil (SslHandler. eg))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddEnableSvrSSL ""

  ^ChannelPipeline
  [pipe options]

  (let []
    (.addLast ^ChannelPipeline pipe "ssl" (EnableSvrSSL options))
    pipe
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ssl-eof nil)

