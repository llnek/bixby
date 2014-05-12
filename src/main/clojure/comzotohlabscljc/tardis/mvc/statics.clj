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

  comzotohlabscljc.tardis.mvc.statics

  (:use [comzotohlabscljc.util.core :only [notnil? spos? ToJavaInt MubleAPI Try! NiceFPath] ])
  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.tardis.io.triggers])
  (:use [comzotohlabscljc.tardis.io.http :only [HttpBasicConfig] ])
  (:use [comzotohlabscljc.tardis.io.netty])
  (:use [comzotohlabscljc.tardis.io.core])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [comzotohlabscljc.tardis.core.constants])

  (:use [comzotohlabscljc.tardis.mvc.templates :only [SetCacheAssetsFlag GetLocalFile ReplyFileAsset] ])
  (:use [comzotohlabscljc.tardis.mvc.comms])
  (:use [comzotohlabscljc.util.str :only [hgl? nsb strim] ])
  (:use [comzotohlabscljc.util.meta :only [MakeObj] ])
  (:use [comzotohlabscljc.net.routes])

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
(defn- handleStatic ""

  [src ^Channel ch info ^HTTPEvent evt ^File file]

  (let [ rsp (NettyFW/makeHttpReply ) ]
    (try
      (if (or (nil? file)
              (not (.exists file)))
        (ServeError src ch 404)
        (do
          (log/debug "serving static file: " (NiceFPath file))
          (addETag src evt info rsp file)
          ;; 304 not-modified
          (if (= (-> rsp (.getStatus)(.code)) 304)
            (do
              (HttpHeaders/setContentLength rsp 0)
              (NettyFW/closeCF (.writeAndFlush ch rsp) (.isKeepAlive evt) ))
            (ReplyFileAsset src ch info rsp file))))
      (catch Throwable e#
        (log/error "failed to get static resource " (.getUri evt) e#)
        (Try!  (ServeError src ch 500))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeStatic ""

  [ ^Emitter src
    ^comzotohlabscljc.net.routes.RouteInfo ri
    ^Matcher mc ^Channel ch info ^HTTPEvent evt]

  (let [ ^File appDir (-> src (.container)(.getAppDir))
         mpt (nsb (.getf ^comzotohlabscljc.util.core.MubleAPI ri :mountPoint))
         ps (NiceFPath (File. appDir ^String DN_PUBLIC))
         uri (.getUri evt)
         gc (.groupCount mc) ]
    (with-local-vars [ mp (StringUtils/replace mpt "${app.dir}" (NiceFPath appDir)) ]
      (if (> gc 1)
        (doseq [ i (range 1 gc) ]
          (var-set mp (StringUtils/replace ^String @mp "{}" (.group mc (int i)) 1))) )

      ;; ONLY serve static assets from *public folder*
      (var-set mp (NiceFPath (File. ^String @mp)))
      (log/debug "request to serve static file: " @mp)

      (if (.startsWith ^String @mp ps)
        (handleStatic src ch info evt (File. ^String @mp))
        (do
          (log/warn "attempt to access non public file-system: " @mp)
          (ServeError src ch 403))
      ))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype StaticAssetHandler [] PipelineDelegate

  (getStartActivity [_  pipe]
    (PTask. (reify Work
              (perform [_ fw job arg]
                (let [ ^HTTPEvent evt (.event job)
                       ^HTTPResult res (.getResultObj evt) ]
                  (.setStatus res 200)
                  (.setContent res "hello world")
                  (.setHeader res "content-type" "text/plain")
                  (.replyResult evt))))))

  (onStop [_ pipe]
    (log/debug "nothing to be done here, just stop please."))

  (onError [ _ err curPt]
    (log/error "Oops, I got an error!")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private statics-eof nil)

