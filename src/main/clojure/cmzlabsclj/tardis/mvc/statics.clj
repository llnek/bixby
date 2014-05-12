;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  cmzlabsclj.tardis.mvc.statics

  (:use [cmzlabsclj.util.core :only [notnil? spos? ToJavaInt MubleAPI Try! NiceFPath] ])
  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.tardis.io.triggers])
  (:use [cmzlabsclj.tardis.io.http :only [HttpBasicConfig] ])
  (:use [cmzlabsclj.tardis.io.netty])
  (:use [cmzlabsclj.tardis.io.core])
  (:use [cmzlabsclj.tardis.core.sys])
  (:use [cmzlabsclj.tardis.core.constants])

  (:use [cmzlabsclj.tardis.mvc.templates :only [MakeWebAsset] ])
  (:use [cmzlabsclj.tardis.mvc.comms])
  (:use [cmzlabsclj.util.str :only [hgl? nsb strim] ])
  (:use [cmzlabsclj.util.meta :only [MakeObj] ])
  (:use [cmzlabsclj.net.routes])

  (:import ( com.zotohlabs.wflow FlowPoint Activity Pipeline PipelineDelegate PTask Work))
  (:import (com.zotohlabs.gallifrey.io HTTPEvent HTTPResult))
  (:import (com.zotohlabs.wflow.core Job))

  (:import [com.zotohlabs.frwk.netty NettyFW])
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (java.util Date))
  (:import (java.io File))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (com.google.gson JsonObject))
  (:import (com.zotohlabs.frwk.core Hierarchial Identifiable))
  (:import (com.zotohlabs.gallifrey.io HTTPEvent Emitter))
  (:import (com.zotohlabs.gallifrey.mvc HTTPErrorHandler MVCUtils WebAsset WebContent))
  (:import (io.netty.handler.codec.http HttpRequest HttpResponse
                                        CookieDecoder ServerCookieEncoder
                                        DefaultHttpResponse HttpVersion
                                        HttpServerCodec
                                        HttpHeaders LastHttpContent
                                        HttpHeaders Cookie QueryStringDecoder))
  (:import (io.netty.bootstrap ServerBootstrap))
  (:import (io.netty.channel Channel ChannelHandler
                             SimpleChannelInboundHandler
                             ChannelPipeline ChannelHandlerContext))
  (:import (io.netty.handler.stream ChunkedWriteHandler))
  (:import (io.netty.util AttributeKey))
  (:import (com.zotohlabs.frwk.netty NettyFW ErrorCatcher
                                     DemuxedMsg PipelineConfigurator
                                     HttpDemux FlashHandler
                                     SSLServerHShake ServerSide))
  (:import (jregex Matcher Pattern)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- eTagHelper ""

  [^HTTPResult res flag & args]

  (case flag
    :header (.setHeader res (nth args 0) (nth args 1))
    :status (.setStatus res (nth args 0))
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleStatic2 ""

  [src ^JsonObject info ^HTTPEvent evt ^HTTPResult res ^File file]

  (with-local-vars [ crap false ]
    (try
      (log/debug "serving static file: " (NiceFPath file))
      (if (or (nil? file)
              (not (.exists file)))
        (.setStatus res 404)
        (do
          (AddETag src info file (partial eTagHelper res))
          (.setContent res (MakeWebAsset file))
          (var-set crap true)
          (.replyResult evt)))
      (catch Throwable e#
        (log/error "failed to get static resource "
                   (nsb (.get info "uri2"))
                   e#)
        (when-not @crap
          (.setStatus res 500)
          (.replyResult evt))
        ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleStatic ""

  [ ^Emitter src ^HTTPEvent evt ^HTTPResult res options ]

  (let [ ^File appDir (-> src (.container)(.getAppDir))
         ps (NiceFPath (File. appDir ^String DN_PUBLIC))
         fpath (nsb (:path options))
         info (:info options) ]
    (log/debug "request to serve static file: " fpath)
    (if (.startsWith fpath ps)
        (handleStatic2 src info evt res (File. fpath))
        (do
          (log/warn "attempt to access non public file-system: " fpath)
          (.setStatus res 403)
          (.replyResult evt)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype StaticAssetHandler [] PipelineDelegate

  (getStartActivity [_  pipe]
    (PTask. (reify Work
              (perform [_ fw job arg]
                (let [ ^HTTPEvent evt (.event job)
                       ^HTTPResult res (.getResultObj evt) ]
                  (handleStatic (.emitter evt)
                                evt
                                res
                                (.getv job EV_OPTS)))))))

  (onStop [_ pipe]
    (log/debug "nothing to be done here, just stop please."))

  (onError [ _ err curPt]
    (log/error "Oops, I got an error!")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private statics-eof nil)

