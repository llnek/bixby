;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.


(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.mvc.comms

  (:require
    [czlab.xlib.util.core :refer [try! FPath]]
    [czlab.xlib.util.wfs :refer [SimPTask]]
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [czlab.skaro.mvc.assets
    :refer [WebAsset* GetLocalFile]]
    [czlab.xlib.util.str :refer [hgl? strim]]
    [czlab.xlib.util.meta :refer [NewObj*]])

  (:use [czlab.xlib.util.consts]
        [czlab.xlib.netty.io]
        [czlab.skaro.io.http]
        [czlab.skaro.io.netty]
        [czlab.skaro.io.core]
        [czlab.skaro.core.sys]
        [czlab.skaro.core.consts])

  (:import
    [com.zotohlab.frwk.server Emitter EventHolder EventTrigger]
    [com.zotohlab.skaro.io HTTPEvent HTTPResult]
    [com.zotohlab.skaro.runtime RouteInfo RouteCracker]
    [com.zotohlab.skaro.mvc HTTPErrorHandler
    MVCUtils WebAsset WebContent]
    [com.zotohlab.frwk.core Hierarchial Identifiable]
    [com.zotohlab.wflow FlowDot Activity
    Job WHandler PTask Work]
    [com.zotohlab.skaro.runtime AuthError]
    [com.zotohlab.skaro.core Muble Container]
    [org.apache.commons.lang3 StringUtils]
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
    [jregex Matcher Pattern]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isModified ""

  [^String eTag lastTm info]

  (with-local-vars
    [unmod "if-unmodified-since"
     none "if-none-match"
     modd true ]
    (cond
      (HasInHeader? info @unmod)
      (when-some [s (GetInHeader info @unmod)]
        (try!
          (when (>= (.getTime (.parse (MVCUtils/getSDF) s))
                    lastTm)
            (var-set modd false))))

      (HasInHeader? info @none)
      (var-set modd (not= eTag
                          (GetInHeader info @none)))

      :else nil)
    @modd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddETag

  "Add a ETag"

  [^Muble src info ^File file ^HTTPResult res]

  (let [lastTm (.lastModified file)
        cfg (.getv src :emcfg)
        maxAge (:maxAgeSecs cfg)
        eTag  (str "\""  lastTm  "-"
                   (.hashCode file)  "\"")]
    (if (isModified eTag lastTm info)
      (->> (Date. lastTm)
           (.format (MVCUtils/getSDF))
           (.setHeader res "last-modified" ))
      (when (= (:method info) "GET")
        (->> HttpResponseStatus/NOT_MODIFIED
             (.code )
             (.setStatus res ))))
    (->> (if (== maxAge 0)
           "no-cache"
           (str "max-age=" maxAge))
         (.setHeader res "cache-control" ))
    (when
      (:useETag cfg)
      (.setHeader res "etag" eTag))))

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
      path)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleStatic2 ""

  [src info ^HTTPEvent evt ^File file]

  (log/debug "serving static file: %s" (FPath file))
  (with-local-vars [crap false]
    (let [^HTTPResult
          res (.getResultObj evt)]
      (try
        (if (or (nil? file)
                (not (.exists file)))
          (do
            (.setStatus res 404)
            (.replyResult evt))
          (do
            (.setContent res (WebAsset* file))
            (.setStatus res 200)
            (AddETag src info file res)
            (var-set crap true)
            (.replyResult evt)))
        (catch Throwable e#
          (log/error "failed to get static resource %s"
                     (:uri2 info)
                     e#)
          (when-not @crap
            (.setContent res nil)
            (.setStatus res 500)
            (.replyResult evt))
          )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HandleStatic

  "Handle static file resource"

  [^Emitter src ^HTTPEvent evt options]

  (let [appDir (-> ^Container
                   (.container src) (.getAppDir))
        ps (FPath (io/file appDir DN_PUBLIC))
        ^HTTPResult res (.getResultObj evt)
        cfg (-> ^Muble src (.getv :emcfg))
        ckAccess (:fileAccessCheck cfg)
        fpath (str (:path options))
        info (:info options) ]
    (log/debug "request to serve static file: %s" fpath)
    (if (or (.startsWith fpath ps)
            (false? ckAccess))
      (handleStatic2 src info evt
                     (io/file (maybeStripUrlCrap fpath)))
      (do
        (log/warn "attempt to access non public file-system: %s" fpath)
        (.setStatus res 403)
        (.replyResult evt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- replyError ""

  [^Emitter src code]

  (-> ^Container
      (.container src)
      (.getAppDir )
      (GetLocalFile (str "pages/errors/" code ".html"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeError

  "Reply back an error"

  [^Muble src ^Channel ch code]

  (with-local-vars
    [rsp (HttpReply* code)
     bits nil wf nil
     ctype "text/plain"]
    (try
      (let [cfg (.getv src :emcfg)
            h (:errorHandler cfg)
            ^HTTPErrorHandler
            cb (if (hgl? h) (NewObj* h) nil)
            ^WebContent
            rc (if (nil? cb)
                 (replyError src code)
                 (.getErrorResponse cb code)) ]
        (when (some? rc)
          (var-set ctype (.contentType rc))
          (var-set bits (.body rc)))
        (SetHeader @rsp "content-type" @ctype)
        (->> (if (nil? @bits)
                 0 (alength ^bytes @bits))
             (HttpHeaders/setContentLength @rsp ))
        (var-set wf (.writeAndFlush ch @rsp))
        (when (some? @bits)
          (->> (Unpooled/wrappedBuffer ^bytes @bits)
               (.writeAndFlush ch )
               (var-set wf )))
        (CloseCF @wf false))
      (catch Throwable e#
        (.close ch)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeStatic

  "Reply back with a static file content"

  [^Muble ri ^Emitter src ^Matcher mc
   ^Channel ch
   info ^HTTPEvent evt]

  (with-local-vars
    [ok true mp nil]
    (try
      (-> evt (.getSession)(.handleEvent evt))
      (catch AuthError e#
        (var-set ok false)
        (ServeError src ch 403)))
    (when @ok
      (let [appDir (-> ^Container
                       (.container src) (.getAppDir))
            ps (FPath (io/file appDir DN_PUBLIC))
            mpt (str (.getv ri :mountPoint))
            gc (.groupCount mc)]
        (var-set mp (.replace mpt "${app.dir}" (FPath appDir)))
        (when (> gc 1)
          (doseq [i (range 1 gc)]
            (var-set mp (StringUtils/replace ^String @mp
                                             "{}"
                                             (.group mc (int i)) 1))))
        (var-set mp (FPath (File. ^String @mp)))
        (let [cfg (-> ^Muble src (.getv :emcfg))
              w (-> (NettyTrigger* ch evt src)
                    (AsyncWaitHolder*  evt))]
          (.timeoutMillis w (:waitMillis cfg))
          (doto src
            (.hold w)
            (.dispatch evt
                       {:router "czlab.skaro.mvc.comms/AssetHandler"
                        :info info
                        :path @mp})))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeRoute

  "Handle a match route"

  [^RouteInfo ri
   ^Muble src
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
      (let [cfg (.getv src :emcfg)
            pms (.collect ri mc)
            options {:router (.getHandler ri)
                     :params (merge {} pms)
                     :template (.getTemplate ri)}
            w (-> (NettyTrigger* ch evt src)
                  (AsyncWaitHolder* evt))]
        (.timeoutMillis w (:waitMillis cfg))
        (doto ^Emitter src
          (.hold  w)
          (.dispatch  evt options))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private  AssetHandler!
  (reify WHandler
    (run [_  j _]
      (let [evt (.event ^Job j)]
        (HandleStatic (.emitter evt)
                      evt
                      (.getv ^Job j EV_OPTS))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AssetHandler "" ^WHandler [] AssetHandler!)

;;(ns-unmap *ns* '->AssetHandler)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

