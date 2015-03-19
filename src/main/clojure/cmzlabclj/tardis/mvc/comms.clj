;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  cmzlabclj.tardis.mvc.comms

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [cmzlabclj.xlib.util.core :only [MubleAPI Try! NiceFPath]]
        [cmzlabclj.tardis.io.triggers]
        [cmzlabclj.tardis.io.http]
        [cmzlabclj.tardis.io.netty]
        [cmzlabclj.tardis.io.core]
        [cmzlabclj.tardis.core.sys]
        [cmzlabclj.tardis.core.constants]
        [cmzlabclj.tardis.mvc.templates
         :only
         [MakeWebAsset GetLocalFile]]
        [cmzlabclj.xlib.util.str :only [hgl? nsb strim]]
        [cmzlabclj.xlib.util.meta :only [MakeObj]])

  (:import  [com.zotohlab.gallifrey.io HTTPEvent HTTPResult Emitter]
            [com.zotohlab.gallifrey.mvc HTTPErrorHandler
             MVCUtils WebAsset WebContent]
            [com.zotohlab.frwk.core Hierarchial Identifiable]
            [com.zotohlab.gallifrey.runtime AuthError]
            [org.apache.commons.lang3 StringUtils]
            [com.zotohlab.frwk.netty NettyFW]
            [java.util Date]
            [java.io File]
            [com.zotohlab.frwk.io XData]
            [io.netty.handler.codec.http HttpRequest
             HttpResponseStatus HttpResponse
             CookieDecoder ServerCookieEncoder
             DefaultHttpResponse HttpVersion
             HttpMessage
             HttpHeaders LastHttpContent
             HttpHeaders Cookie QueryStringDecoder]
            [io.netty.buffer Unpooled]
            [io.netty.channel Channel ChannelHandler
             ChannelFuture
             ChannelPipeline ChannelHandlerContext]
            [com.google.gson JsonObject]
            [jregex Matcher Pattern]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isModified ""

  [^String eTag lastTm info]

  (with-local-vars [modd true ]
    (cond
      (HasHeader? info "if-none-match")
      (var-set modd (not= eTag (GetHeader info "if-none-match")))

      (HasHeader? info "if-unmodified-since")
      (when-let [s (GetHeader info "if-unmodified-since") ]
        (Try! (when (>= (.getTime (.parse (MVCUtils/getSDF) s)) lastTm)
                (var-set modd false))))
      :else nil)
    @modd
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddETag ""

  [^cmzlabclj.tardis.core.sys.Element src
   info
   ^File file
   ^HTTPResult res ]

  (let [cfg (.getAttr src :emcfg)
        maxAge (:cacheMaxAgeSecs cfg)
        lastTm (.lastModified file)
        eTag  (str "\""  lastTm  "-" (.hashCode file)  "\"") ]
    (if (isModified eTag lastTm info)
      (.setHeader res "last-modified"
                  (.format (MVCUtils/getSDF) (Date. lastTm)))
      (if (= (:method info) "GET")
        (.setStatus res (.code HttpResponseStatus/NOT_MODIFIED))))
    (.setHeader res "cache-control"
                (if (= maxAge 0) "no-cache" (str "max-age=" maxAge)))
    (when (:useETag cfg) (.setHeader res "etag" eTag))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeStripUrlCrap

  "Want to handle case where the url has stuff after the file name.
   For example:  /public/blab&hhh or /public/blah?ggg"

  ^String
  [^String path]

  (let [pos (.lastIndexOf path (int \/)) ]
    (if (> pos 0)
      (let [p1 (.indexOf path (int \?) pos)
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

  [src info ^HTTPEvent evt ^HTTPResult res ^File file]

  (with-local-vars [crap false ]
    (try
      (log/debug "Serving static file: " (NiceFPath file))
      (if (or (nil? file)
              (not (.exists file)))
        (do
          (.setStatus res 404)
          (.replyResult evt))
        (do
          (.setContent res (MakeWebAsset file))
          (.setStatus res 200)
          (AddETag src info file res)
          (var-set crap true)
          (.replyResult evt)))
      (catch Throwable e#
        (log/error "Failed to get static resource "
                   (nsb (:uri2 info))
                   e#)
        (when-not @crap
          (.setStatus res 500)
          (.replyResult evt))
        ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HandleStatic ""

  [^Emitter src ^HTTPEvent evt ^HTTPResult res options ]

  (let [^File appDir (-> src (.container)(.getAppDir))
        ps (NiceFPath (File. appDir DN_PUBLIC))
        cfg (.getAttr ^cmzlabclj.tardis.core.sys.Element
                      src
                      :emcfg)
        ckAccess (:fileAccessCheck cfg)
        fpath (nsb (:path options))
        info (:info options) ]
    (log/debug "Request to serve static file: " fpath)
    (if (or (.startsWith fpath ps)
            (false? ckAccess))
      (handleStatic2 src info evt res
                     (File. (maybeStripUrlCrap fpath)))
      (do
        (log/warn "Attempt to access non public file-system: " fpath)
        (.setStatus res 403)
        (.replyResult evt)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reply-error ""

  [^Emitter src code]

  (let [ctr (.container src)
        appDir (.getAppDir ctr) ]
    (GetLocalFile appDir (str "pages/errors/" code ".html"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeError ""

  [^cmzlabclj.tardis.core.sys.Element src
   ^Channel ch
   code ]

  (with-local-vars [rsp (NettyFW/makeHttpReply code) bits nil wf nil]
    (try
      (let [cfg (.getAttr src :emcfg)
            h (:errorHandler cfg)
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
                                      (if (nil? @bits)
                                        0
                                        (alength ^bytes @bits)))
        (var-set wf (.writeAndFlush ch @rsp))
        (when-not (nil? @bits)
          (var-set wf (.writeAndFlush ch
                                      (Unpooled/wrappedBuffer ^bytes @bits))))
        (NettyFW/closeCF @wf false))
      (catch Throwable e#
        (NettyFW/closeChannel ch)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeStatic ""

  [^cmzlabclj.xlib.util.core.MubleAPI
   ri
   ^Emitter src
   ^Matcher mc ^Channel ch info ^HTTPEvent evt]

  (try
    (-> evt (.getSession)(.handleEvent evt))
    (catch AuthError e#
      (ServeError src ch 403)))
  (let [^File appDir (-> src (.container)(.getAppDir))
        mpt (nsb (.getf ri :mountPoint))
        ps (NiceFPath (File. appDir DN_PUBLIC))
        gc (.groupCount mc) ]
    (with-local-vars [mp (.replace mpt "${app.dir}" (NiceFPath appDir)) ]
      (if (> gc 1)
        (doseq [i (range 1 gc) ]
          (var-set mp (StringUtils/replace ^String @mp "{}" (.group mc (int i)) 1))) )
      (var-set mp (NiceFPath (File. ^String @mp)))
      (let [^cmzlabclj.tardis.io.core.EmitterAPI co src
            cfg (.getAttr ^cmzlabclj.tardis.core.sys.Element src :emcfg)
            ^cmzlabclj.tardis.io.core.WaitEventHolder
            w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
        (.timeoutMillis w (:waitMillis cfg))
        (.hold co w)
        (.dispatch co evt {:router "cmzlabclj.tardis.mvc.statics.StaticAssetHandler"
                           :info info
                           :path @mp } )))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeRoute ""

  [^cmzlabclj.xlib.net.routes.RouteInfo ri
   ^cmzlabclj.tardis.core.sys.Element src
   ^Matcher mc
   ^Channel ch
   ^HTTPEvent evt ]
    ;;^cmzlabclj.xlib.util.core.MubleAPI evt]

  (try
    (-> evt (.getSession)(.handleEvent evt))
    (catch AuthError e#
      (ServeError src ch 403)))
  (let [pms (.collect ri mc)
        cfg (.getAttr src :emcfg)
        options {:router (.getHandler ri)
                 :params (merge {} pms)
                 :template (.getTemplate ri) } ]
    (let [^cmzlabclj.tardis.io.core.EmitterAPI co src
          ^cmzlabclj.tardis.io.core.WaitEventHolder
          w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
      (.timeoutMillis w (:waitMillis cfg))
      (.hold co w)
      (.dispatch co evt options))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private comms-eof nil)

