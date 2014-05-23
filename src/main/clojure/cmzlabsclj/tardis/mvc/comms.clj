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

  cmzlabsclj.tardis.mvc.comms

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.util.core :only [MubleAPI Try! NiceFPath] ])
  (:use [cmzlabsclj.tardis.io.triggers])
  (:use [cmzlabsclj.tardis.io.http])
  (:use [cmzlabsclj.tardis.io.netty])
  (:use [cmzlabsclj.tardis.io.core])
  (:use [cmzlabsclj.tardis.core.sys])
  (:use [cmzlabsclj.tardis.core.constants])
  (:use [cmzlabsclj.tardis.mvc.templates
         :only [MakeWebAsset GetLocalFile] ])
  (:use [cmzlabsclj.nucleus.util.str :only [hgl? nsb strim] ])
  (:use [cmzlabsclj.nucleus.util.meta :only [MakeObj] ])
  (:import (com.zotohlabs.gallifrey.mvc HTTPErrorHandler
                                        MVCUtils WebAsset WebContent))
  (:import (com.zotohlabs.frwk.core Hierarchial Identifiable))
  (:import (com.zotohlabs.gallifrey.io HTTPEvent HTTPResult Emitter))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import [com.zotohlabs.frwk.netty NettyFW])
  (:import (java.util Date))
  (:import (java.io File))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (io.netty.handler.codec.http HttpRequest HttpResponseStatus HttpResponse
                                        CookieDecoder ServerCookieEncoder
                                        DefaultHttpResponse HttpVersion
                                        HttpServerCodec HttpMessage
                                        HttpHeaders LastHttpContent
                                        HttpHeaders Cookie QueryStringDecoder))
  (:import (io.netty.buffer Unpooled))
  (:import (io.netty.channel Channel ChannelHandler ChannelFuture
                             ChannelPipeline ChannelHandlerContext))
  (:import (com.zotohlabs.frwk.netty NettyFW))
  (:import (com.google.gson JsonObject))
  (:import (jregex Matcher Pattern)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isModified ""

  [^String eTag lastTm ^JsonObject info]

  (with-local-vars [ modd true ]
    (cond
      (HasHeader? info "if-none-match")
      (var-set modd (not= eTag (GetHeader info "if-none-match")))

      (HasHeader? info "if-unmodified-since")
      (if-let [ s (GetHeader info "if-unmodified-since") ]
          (Try! (when (>= (.getTime (.parse (MVCUtils/getSDF) s)) lastTm)
                      (var-set modd false))))
      :else nil)
    @modd
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddETag ""

  [ ^cmzlabsclj.tardis.core.sys.Element src
    ^JsonObject info
    ^File file
    ^HTTPResult res ]

  (let [ maxAge (.getAttr src :cacheMaxAgeSecs)
         lastTm (.lastModified file)
         eTag  (str "\""  lastTm  "-" (.hashCode file)  "\"") ]
    (if (isModified eTag lastTm info)
        (.setHeader res "last-modified"
                    (.format (MVCUtils/getSDF) (Date. lastTm)))
        (if (= (-> (.get info "method")(.getAsString)) "GET")
            (.setStatus res (.code HttpResponseStatus/NOT_MODIFIED))))
    (.setHeader res "cache-control"
                (if (= maxAge 0) "no-cache" (str "max-age=" maxAge)))
    (when (.getAttr src :useETag) (.setHeader res "etag" eTag))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeStripUrlCrap

  "Want to handle case where the url has stuff after the file name.
   For example:  /public/blab&hhh or /public/blah?ggg"

  ^String
  [^String path]

  (let [ pos (.lastIndexOf path (int \/)) ]
    (if (> pos 0)
      (let [ p1 (.indexOf path (int \?) pos)
             p2 (.indexOf path (int \&) pos)
             p3 (cond
                  (and (> p1 0) (> p2 0)) (Math/min p1 p2)
                  (> p1 0) p1
                  (> p2 0) p2
                  :else -1) ]
        (if (> p3 0)
           (.substring path 0 p3)
           path))
      path)
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
        (do (.setStatus res 404)
            (.replyResult evt))
        (do
          (.setContent res (MakeWebAsset file))
          (.setStatus res 200)
          (AddETag src info file res)
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
(defn HandleStatic ""

  [ ^Emitter src ^HTTPEvent evt ^HTTPResult res options ]

  (let [ ^File appDir (-> src (.container)(.getAppDir))
         ps (NiceFPath (File. appDir DN_PUBLIC))
         fpath (nsb (:path options))
         info (:info options) ]
    (log/debug "request to serve static file: " fpath)
    (if (.startsWith fpath ps)
        (handleStatic2 src info evt res
                       (File. (maybeStripUrlCrap fpath)))
        (do
          (log/warn "attempt to access non public file-system: " fpath)
          (.setStatus res 403)
          (.replyResult evt)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reply-error ""

  [^Emitter src code]

  (let [ ctr (.container src)
         appDir (.getAppDir ctr) ]
    (GetLocalFile appDir (str "pages/errors/" code ".html"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeError ""

  [ ^cmzlabsclj.tardis.core.sys.Element src
    ^Channel ch
    code ]

  (with-local-vars [ rsp (NettyFW/makeHttpReply code) bits nil wf nil]
    (try
      (let [ h (.getAttr src :errorHandler)
             ^HTTPErrorHandler
             cb (if (hgl? h) (MakeObj h) nil)
             ^WebContent
             rc (if (nil? cb)
                    (reply-error src code)
                    (.getErrorResponse cb code)) ]
        (when-not (nil? rc)
          (HttpHeaders/setHeader ^HttpMessage @rsp "content-type" (.contentType rc))
          (var-set bits (.body rc)))
        (HttpHeaders/setContentLength @rsp
                                      (if (nil? @bits) 0 (alength ^bytes @bits)))
        (var-set wf (.writeAndFlush ch @rsp))
        (when-not (nil? @bits)
          (var-set wf (.writeAndFlush ch (Unpooled/wrappedBuffer ^bytes @bits))))
        (NettyFW/closeCF @wf false))
      (catch Throwable e#
        (NettyFW/closeChannel ch)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeStatic ""

  [ ^Emitter src
    ri
    ^Matcher mc ^Channel ch info ^HTTPEvent evt]

  (let [ mpt (nsb (.getf ^cmzlabsclj.nucleus.util.core.MubleAPI ri :mountPoint))
        ^File appDir (-> src (.container)(.getAppDir))
         ps (NiceFPath (File. appDir DN_PUBLIC))
         gc (.groupCount mc) ]
    (with-local-vars [ mp (StringUtils/replace mpt "${app.dir}" (NiceFPath appDir)) ]
      (if (> gc 1)
        (doseq [ i (range 1 gc) ]
          (var-set mp (StringUtils/replace ^String @mp "{}" (.group mc (int i)) 1))) )
      (var-set mp (NiceFPath (File. ^String @mp)))
      (let [ ^cmzlabsclj.tardis.io.core.EmitterAPI co src
             ^cmzlabsclj.tardis.io.core.WaitEventHolder
             w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
        (.timeoutMillis w (.getAttr ^cmzlabsclj.tardis.core.sys.Element src :waitMillis))
        (.hold co w)
        (.dispatch co evt { :router "cmzlabsclj.tardis.mvc.statics.StaticAssetHandler"
                            :info info
                            :path @mp } )))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeRoute ""

  [ ^cmzlabsclj.tardis.core.sys.Element src
    ^cmzlabsclj.nucleus.net.routes.RouteInfo ri
    ^Matcher mc
    ^Channel ch
    ^cmzlabsclj.nucleus.util.core.MubleAPI evt]

  (let [ pms (.collect ri mc)
         options { :router (.getHandler ri)
                   :params (merge {} pms)
                   :template (.getTemplate ri) } ]
    (let [ ^cmzlabsclj.tardis.io.core.EmitterAPI co src
           ^cmzlabsclj.tardis.io.core.WaitEventHolder
           w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
      (.timeoutMillis w (.getAttr src :waitMillis))
      (.hold co w)
      (.dispatch co evt options))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private comms-eof nil)

