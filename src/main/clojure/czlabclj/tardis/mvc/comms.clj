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

  czlabclj.tardis.mvc.comms

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.core :only [MubleAPI Try! NiceFPath]]
        [czlabclj.tardis.io.triggers]
        [czlabclj.tardis.io.http]
        [czlabclj.tardis.io.netty]
        [czlabclj.tardis.io.core]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.core.wfs]
        [czlabclj.tardis.core.constants]
        [czlabclj.tardis.mvc.assets
         :only
         [MakeWebAsset GetLocalFile]]
        [czlabclj.xlib.util.str :only [hgl? nsb strim]]
        [czlabclj.xlib.util.meta :only [MakeObj]])

  (:import  [com.zotohlab.gallifrey.io HTTPEvent HTTPResult Emitter]
            [com.zotohlab.gallifrey.mvc HTTPErrorHandler
             MVCUtils WebAsset WebContent]
            [com.zotohlab.frwk.core Hierarchial Identifiable]
            [com.zotohlab.wflow FlowNode Activity Pipeline
             PDelegate PTask Work]
            [com.zotohlab.wflow.core Job]
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

  (with-local-vars [unmod "if-unmodified-since"
                    none "if-none-match"
                    modd true ]
    (cond
      (HasHeader? info @unmod)
      (when-let [s (GetHeader info @unmod)]
        (Try!
          (when (>= (.getTime (.parse (MVCUtils/getSDF) s))
                    lastTm)
            (var-set modd false))))

      (HasHeader? info @none)
      (var-set modd (not= eTag
                          (GetHeader info @none)))

      :else nil)
    @modd
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddETag ""

  [^czlabclj.tardis.core.sys.Element
   src info
   ^File file
   ^HTTPResult res]

  (let [lastTm (.lastModified file)
        cfg (.getAttr src :emcfg)
        maxAge (:maxAgeSecs cfg)
        eTag  (str "\""  lastTm  "-"
                   (.hashCode file)  "\"")]
    (if (isModified eTag lastTm info)
      (.setHeader res "last-modified"
                  (.format (MVCUtils/getSDF) (Date. lastTm)))
      (when (= (:method info) "GET")
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
                 :else -1)]
        (if (> p3 0)
          (.substring path 0 p3)
          path))
      path)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleStatic2 ""

  [src info ^HTTPEvent evt ^File file]

  (log/debug "Serving static file: " (NiceFPath file))
  (with-local-vars [crap false]
    (let [^HTTPResult res (.getResultObj evt)]
      (try
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
            (.setContent res nil)
            (.setStatus res 500)
            (.replyResult evt))
          )))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HandleStatic ""

  [^Emitter src ^HTTPEvent evt options]

  (let [^File appDir (-> src (.container)(.getAppDir))
        ps (NiceFPath (File. appDir DN_PUBLIC))
        ^HTTPResult res (.getResultObj evt)
        cfg (.getAttr ^czlabclj.tardis.core.sys.Element
                      src
                      :emcfg)
        ckAccess (:fileAccessCheck cfg)
        fpath (nsb (:path options))
        info (:info options) ]
    (log/debug "Request to serve static file: " fpath)
    (if (or (.startsWith fpath ps)
            (false? ckAccess))
      (handleStatic2 src info evt
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

  [^czlabclj.tardis.core.sys.Element src
   ^Channel ch
   code ]

  (with-local-vars [rsp (NettyFW/makeHttpReply code)
                    bits nil wf nil
                    ctype "text/plain"]
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
          (var-set ctype (.contentType rc))
          (var-set bits (.body rc)))
        (HttpHeaders/setHeader ^HttpMessage
                                 @rsp
                                 "content-type" @ctype)
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

  [^czlabclj.xlib.util.core.MubleAPI
   ri
   ^Emitter src
   ^Matcher mc ^Channel ch info ^HTTPEvent evt]

  (with-local-vars [ok true mp nil]
    (try
      (-> evt (.getSession)(.handleEvent evt))
      (catch AuthError e#
        (var-set ok false)
        (ServeError src ch 403)))
    (when @ok
      (let [^File appDir (-> src (.container)(.getAppDir))
            ps (NiceFPath (File. appDir DN_PUBLIC))
            mpt (nsb (.getf ri :mountPoint))
            gc (.groupCount mc)]
        (var-set mp (.replace mpt "${app.dir}" (NiceFPath appDir)))
        (when (> gc 1)
          (doseq [i (range 1 gc)]
            (var-set mp (StringUtils/replace ^String @mp
                                             "{}"
                                             (.group mc (int i)) 1))))
        (var-set mp (NiceFPath (File. ^String @mp)))
        (let [cfg (.getAttr ^czlabclj.tardis.core.sys.Element src :emcfg)
              ^czlabclj.tardis.io.core.EmitAPI co src
              ^czlabclj.tardis.io.core.WaitEventHolder
              w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt)]
          (.timeoutMillis w (:waitMillis cfg))
          (.hold co w)
          (.dispatch co
                     evt
                     {:router "czlabclj.tardis.mvc.comms.AssetHandler"
                      :info info
                      :path @mp}))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeRoute ""

  [^czlabclj.xlib.net.routes.RouteInfo ri
   ^czlabclj.tardis.core.sys.Element src
   ^Matcher mc
   ^Channel ch
   ^HTTPEvent evt]

  (with-local-vars [ok true]
    (try
      (-> evt (.getSession)(.handleEvent evt))
      (catch AuthError e#
        (var-set ok false)
        (ServeError src ch 403)))
    (when @ok
      (let [^czlabclj.tardis.io.core.EmitAPI co src
            cfg (.getAttr src :emcfg)
            pms (.collect ri mc)
            options {:router (.getHandler ri)
                     :params (merge {} pms)
                     :template (.getTemplate ri)}
            ^czlabclj.tardis.io.core.WaitEventHolder
            w
            (MakeAsyncWaitHolder
              (MakeNettyTrigger ch evt co) evt)]
        (.timeoutMillis w (:waitMillis cfg))
        (.hold co w)
        (.dispatch co evt options)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype AssetHandler [] PDelegate

  (getStartActivity [_ pipe]
    (DefWFTask
      (fn [cur ^Job job arg]
        (let [^HTTPEvent evt (.event job)]
          (HandleStatic (.emitter evt)
                        evt
                        (.getv job EV_OPTS))
          nil))))

  (onStop [_ pipe]
    (log/debug "Nothing to be done here, just stop please."))

  (onError [ _ err curPt]
    (log/error "Oops, I got an error!")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private comms-eof nil)
