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
      :author "Kenneth Leung" }

  czlab.skaro.mvc.comms

  (:require
    [czlab.xlib.core :refer [try! fpath]]
    [czlab.skaro.core.wfs :refer [simPTask]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.skaro.mvc.assets
     :refer [webAsset getLocalFile]]
    [czlab.xlib.str :refer [hgl? strim]]
    [czlab.xlib.meta :refer [newObj]])

  (:use [czlab.xlib.consts]
        [czlab.netty.io]
        [czlab.skaro.io.http]
        [czlab.skaro.io.netty]
        [czlab.skaro.io.core]
        [czlab.skaro.core.sys]
        [czlab.skaro.core.consts])

  (:import
    [czlab.wflow.server Emitter EventHolder]
    [czlab.skaro.server Cocoon EventTrigger]
    [czlab.skaro.io HTTPEvent HTTPResult]
    [czlab.net RouteInfo RouteCracker]
    [czlab.skaro.mvc HTTPErrorHandler
     MVCUtils WebAsset WebContent]
    [czlab.xlib XData Muble Hierarchial Identifiable]
    [czlab.wflow.dsl FlowDot Activity
     Job WHandler PTask Work]
    [czlab.skaro.runtime AuthError]
    [org.apache.commons.lang3 StringUtils]
    [java.util Date]
    [java.io File]
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
      (hasInHeader? info @unmod)
      (when-some [s (getInHeader info @unmod)]
        (try!
          (when (>= (.getTime (.parse (MVCUtils/getSDF) s))
                    lastTm)
            (var-set modd false))))

      (hasInHeader? info @none)
      (var-set modd (not= eTag
                          (getInHeader info @none)))

      :else nil)
    @modd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addETag

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

  (log/debug "serving static file: %s" (fpath file))
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
            (.setContent res (webAsset file))
            (.setStatus res 200)
            (addETag src info file res)
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
(defn handleStatic

  "Handle static file resource"

  [^Emitter src ^HTTPEvent evt options]

  (let [appDir (-> ^Cocoon
                   (.container src) (.getAppDir))
        ps (fpath (io/file appDir DN_PUBLIC))
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

  (-> ^Cocoon
      (.container src)
      (.getAppDir )
      (getLocalFile (str "pages/errors/" code ".html"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn serveError

  "Reply back an error"

  [^Muble src ^Channel ch code]

  (with-local-vars
    [rsp (httpReply code)
     bits nil wf nil
     ctype "text/plain"]
    (try
      (let [cfg (.getv src :emcfg)
            h (:errorHandler cfg)
            ^HTTPErrorHandler
            cb (if (hgl? h) (newObj h) nil)
            ^WebContent
            rc (if (nil? cb)
                 (replyError src code)
                 (.getErrorResponse cb code)) ]
        (when (some? rc)
          (var-set ctype (.contentType rc))
          (var-set bits (.body rc)))
        (setHeader @rsp "content-type" @ctype)
        (->> (if (nil? @bits)
                 0 (alength ^bytes @bits))
             (HttpHeaders/setContentLength @rsp ))
        (var-set wf (.writeAndFlush ch @rsp))
        (when (some? @bits)
          (->> (Unpooled/wrappedBuffer ^bytes @bits)
               (.writeAndFlush ch )
               (var-set wf )))
        (closeCF @wf false))
      (catch Throwable e#
        (.close ch)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn serveStatic

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
        (serveError src ch 403)))
    (when @ok
      (let [appDir (-> ^Cocoon
                       (.container src) (.getAppDir))
            ps (fpath (io/file appDir DN_PUBLIC))
            mpt (str (.getv ri :mountPoint))
            gc (.groupCount mc)]
        (var-set mp (.replace mpt "${app.dir}" (fpath appDir)))
        (when (> gc 1)
          (doseq [i (range 1 gc)]
            (var-set mp (StringUtils/replace ^String @mp
                                             "{}"
                                             (.group mc (int i)) 1))))
        (var-set mp (fpath (File. ^String @mp)))
        (let [cfg (-> ^Muble src (.getv :emcfg))
              w (-> (nettyTrigger ch evt src)
                    (asyncWaitHolder evt))]
          (.timeoutMillis w (:waitMillis cfg))
          (doto src
            (.hold w)
            (.dispatch evt
                       {:router "czlab.skaro.mvc.comms/AssetHandler"
                        :info info
                        :path @mp})))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn serveRoute

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
        (serveError src ch 403)))
    (when @ok
      (let [cfg (.getv src :emcfg)
            pms (.collect ri mc)
            options {:router (.getHandler ri)
                     :params (merge {} pms)
                     :template (.getTemplate ri)}
            w (-> (nettyTrigger ch evt src)
                  (asyncWaitHolder evt))]
        (.timeoutMillis w (:waitMillis cfg))
        (doto ^Emitter src
          (.hold  w)
          (.dispatch  evt options))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private  assetHandler!
  (reify WHandler
    (run [_  j _]
      (let [evt (.event ^Job j)]
        (handleStatic (.emitter evt)
                      evt
                      (.getv ^Job j EV_OPTS))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn assetHandler ""

  ^WHandler
  []

  assetHandler!)

;;(ns-unmap *ns* '->AssetHandler)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

