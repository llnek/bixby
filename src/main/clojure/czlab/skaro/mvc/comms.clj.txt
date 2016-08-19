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
    [czlab.xlib.core :refer [when-some+ try! fpath]]
    [czlab.xlib.str :refer [hgl? strim]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.skaro.mvc.assets
     :refer [webAsset
             getLocalFile]]
    [czlab.xlib.meta :refer [new<>]])

  (:use [czlab.xlib.consts]
        [czlab.netty.core]
        [czlab.skaro.io.http]
        [czlab.skaro.io.netty]
        [czlab.skaro.io.core]
        [czlab.skaro.sys.core])

  (:import
    [czlab.skaro.server Container Service EventTrigger]
    [czlab.server Emitter EventHolder]
    [czlab.skaro.io HttpEvent HttpResult]
    [czlab.net RouteInfo RouteCracker]
    [czlab.skaro.mvc
     HttpErrorHandler
     MVCUtils
     WebAsset
     WebContent]
    [czlab.xlib XData Muble Hierarchial Identifiable]
    [czlab.wflow  TaskDef Job]
    [czlab.skaro.rt AuthError]
    [org.apache.commons.lang3 StringUtils]
    [java.util Date]
    [java.io File]
    [io.netty.handler.codec.http.cookie
     ServerCookieEncoder
     CookieDecoder ]
    [io.netty.handler.codec.http
     DefaultHttpResponse
     HttpRequest
     HttpResponseStatus
     HttpResponse
     HttpVersion
     HttpMessage
     HttpHeaders
     LastHttpContent
     HttpHeaders
     Cookie
     QueryStringDecoder]
    [io.netty.buffer Unpooled]
    [io.netty.channel
     Channel
     ChannelHandler
     ChannelFuture
     ChannelPipeline
     ChannelHandlerContext]
    [jregex Matcher Pattern]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isModified

  ""
  [^String eTag lastTm gist]

  (with-local-vars
    [unmod "if-unmodified-since"
     none "if-none-match"
     modd true ]
    (cond
      (gistHeader? gist @unmod)
      (when-some+ [s (gistHeader gist @unmod)]
        (try!
          (when (>= (.getTime (.parse (MVCUtils/getSDF) s))
                    lastTm)
            (var-set modd false))))

      (gistHeader? gist @none)
      (var-set modd (not= eTag
                          (gistHeader gist @none)))

      :else nil)
    @modd))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addETag

  "Add a ETag"
  [^Service src gist ^File file ^HttpResult res]

  (let [lastTm (.lastModified file)
        cfg (.config src)
        maxAge (:maxAgeSecs cfg)
        eTag  (str "\""  lastTm  "-"
                   (.hashCode file)  "\"")]
    (if (isModified eTag lastTm gist)
      (->> (Date. lastTm)
           (.format (MVCUtils/getSDF))
           (.setHeader res "last-modified" ))
      (when (= (:method gist) "GET")
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
(defn- handleStatic2

  ""
  [gist ^HttpEvent evt ^File file]

  (log/debug "serving static file: %s" (fpath file))
  (with-local-vars [crap false]
    (let [^HttpResult
          res (.resultObj evt)]
      (try
        (if (or (nil? file)
                (not (.exists file)))
          (do
            (.setStatus res 404)
            (.replyResult evt))
          (do
            (.setContent res (webAsset file))
            (.setStatus res 200)
            (addETag gist file res)
            (var-set crap true)
            (.replyResult evt)))
        (catch Throwable e#
          (log/error "failed to get static resource %s"
                     (:uri2 gist)
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
  [^Service src ^HttpEvent evt options]

  (let [appDir (-> ^Container
                   (.server src) (.appDir))
        ps (fpath (io/file appDir DN_PUBLIC))
        ^HttpResult res (.resultObj evt)
        cfg (.config src)
        ckAccess (:fileAccessCheck cfg)
        fpath (str (:path options))
        gist (:gist options) ]
    (log/debug "request to serve static file: %s" fpath)
    (if (or (.startsWith fpath ps)
            (false? ckAccess))
      (handleStatic2 gist
                     evt
                     (io/file (maybeStripUrlCrap fpath)))
      (do
        (log/warn "attempt to access non public file-system: %s" fpath)
        (.setStatus res 403)
        (.replyResult evt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- replyError

  ""
  [^Service src code]

  (-> ^Container
      (.server src)
      (.appDir )
      (getLocalFile (str "pages/errors/" code ".html"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn serveError

  "Reply back an error"
  [^Service src ^Channel ch code]

  (with-local-vars
    [rsp (httpReply<> code)
     bits nil wf nil
     ctype "text/plain"]
    (try
      (let
        [cfg (.config src)
         h (:errorHandler cfg)
         ^HttpErrorHandler
         cb (if (hgl? h) (new<> h) nil)
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
             (HttpUtil/setContentLength @rsp ))
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
  [^RouteInfo ri ^Service src ^Matcher mc
   ^Channel ch
   gist ^HttpEvent evt]

  (with-local-vars
    [ok true mp nil]
    (try
      (-> evt (.session)(.handleEvent evt))
      (catch AuthError e#
        (var-set ok false)
        (serveError src ch 403)))
    (when @ok
      (let [appDir (-> ^Container
                       (.server src) (.appDir))
            ps (fpath (io/file appDir DN_PUBLIC))
            mpt (str (.getv (.getx ri) :mountPoint))
            gc (.groupCount mc)]
        (var-set mp (.replace mpt "${app.dir}" (fpath appDir)))
        (when (> gc 1)
          (doseq [i (range 1 gc)]
            (var-set mp (StringUtils/replace ^String @mp
                                             "{}"
                                             (.group mc (int i)) 1))))
        (var-set mp (fpath (File. ^String @mp)))
        (let [cfg (.config src)
              w (-> (nettyTrigger<> ch evt src)
                    (asyncWaitHolder<> evt))]
          (.timeoutMillis w (:waitMillis cfg))
          (doto src
            (.hold w)
            (.dispatchEx
              evt
              {:router "czlab.skaro.mvc.comms/AssetHandler"
               :gist gist
               :path @mp})))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn serveRoute

  "Handle a match route"
  [^RouteInfo ri
   ^Service src
   ^Matcher mc
   ^Channel ch
   ^HttpEvent evt]

  (with-local-vars [ok true]
    (try
      (-> evt (.session)(.handleEvent evt))
      (catch AuthError e#
        (var-set ok false)
        (serveError src ch 403)))
    (when @ok
      (let [cfg (.config src)
            pms (.collect ri mc)
            options {:router (.getHandler ri)
                     :params (merge {} pms)
                     :template (.getTemplate ri)}
            w (-> (nettyTrigger<> ch evt src)
                  (asyncWaitHolder<> evt))]
        (.timeoutMillis w (:waitMillis cfg))
        (doto src
          (.hold  w)
          (.dispatchEx  evt options))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private  assetHandler!
  (script<>
    #(let [evt (.event ^Job %2)]
       (handleStatic (.emitter evt)
                     evt
                     (.getv ^Job %2 EV_OPTS)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn assetHandler ""

  ^TaskDef
  []

  assetHandler!)

;;(ns-unmap *ns* '->AssetHandler)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

