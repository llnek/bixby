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
    [czlab.xlib.str :refer [hgl? strim]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.core
     :refer [when-some+
             do->nil
             cast?
             try!!
             try!
             fpath
             convLong]]
    [clojure.java.io :as io]
    [clojure.string :as cs]
    [czlab.skaro.mvc.assets
     :refer [webAsset<>
             webContent<>
             getLocalContent]])

  (:use [czlab.skaro.io.http]
        [czlab.skaro.io.web]
        [czlab.xlib.consts]
        [czlab.wflow.core]
        [czlab.netty.core]
        [czlab.skaro.io.core]
        [czlab.skaro.sys.core])

  (:import
    [czlab.skaro.io IoService IoEvent HttpEvent HttpResult]
    [czlab.xlib XData Muble Hierarchial Identifiable]
    [io.netty.handler.codec.http HttpResponseStatus]
    [czlab.net RouteInfo RouteCracker]
    [czlab.skaro.server Container]
    [czlab.skaro.net
     MvcUtils
     WebAsset
     WebContent]
    [czlab.wflow WorkStream Job]
    [czlab.skaro.etc AuthError]
    [java.util Date]
    [java.io File]
    [io.netty.buffer Unpooled]
    [io.netty.channel Channel]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isMod?

  ""
  [^String eTag lastTm gist]

  (let
    [unmod (gistHeader gist "if-unmodified-since")
     none (gistHeader gist "if-none-match")]
    (cond
      (hgl? unmod)
      (let [t (try!! -1 (-> (MvcUtils/getSDF)
                            (.parse unmod)
                            (.getTime)))]
        (> lastTm t))

      (hgl? none)
      (not= eTag none)

      :else true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addETag

  "Add a ETag"
  [^IoService src gist ^File f ^HttpResult res]

  (let [lastTm (.lastModified f)
        cfg (.config src)
        maxAge (convLong (:maxAgeSecs cfg) 0)
        eTag  (format "\"%s-%s\""
                      lastTm (.hashCode f))]
    (if (isMod? eTag lastTm gist)
      (->> (Date. lastTm)
           (.format (MvcUtils/getSDF))
           (.setHeader res "last-modified" ))
      (if (= (:method gist) "GET")
        (->> HttpResponseStatus/NOT_MODIFIED
             (.code )
             (.setStatus res ))))
    (->> (if (== maxAge 0)
           "no-cache"
           (str "max-age=" maxAge))
         (.setHeader res "cache-control" ))
    (when
      (true? (:useETag cfg))
      (.setHeader res "etag" eTag))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeStripUrlCrap

  "Want to handle case where the url has stuff after the file name.
   For example:  /public/blab&hhh or /public/blah?ggg"
  ^String
  [^String path]

  (let [pos (.lastIndexOf path (int \/))]
    (if (> pos 0)
      (let [p1 (.indexOf path (int \?) pos)
            p2 (.indexOf path (int \&) pos)
            p3 (cond
                 (and (> p1 0)
                      (> p2 0))
                 (Math/min p1 p2)
                 (> p1 0) p1
                 (> p2 0) p2
                 :else -1)]
        (if (> p3 0)
          (.substring path 0 p3)
          path))
      path)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getStatic

  ""
  [gist ^HttpEvent evt ^File f]

  (log/debug "serving file: %s" (fpath f))
  (let [^HttpResult
        res (.resultObj evt)]
    (try
      (if (or (nil? f)
              (not (.exists f)))
        (do
          (.setStatus res 404)
          (.replyResult evt))
        (do
          (.setContent res f)
          (.setStatus res 200)
          (addETag gist f res)
          (.replyResult evt)))
      (catch Throwable e#
        (log/error "get: %s"
                   (:uri2 gist) e#)
        (try!
          (.setContent res nil)
          (.setStatus res 500)
          (.replyResult evt))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleStatic

  "Handle static resource"
  [^IoService src ^HttpEvent evt args]

  (let
    [appDir (-> ^Container
                (.server src) (.appDir))
     ps (fpath (io/file appDir DN_PUB))
     ^HttpResult res (.resultObj evt)
     cfg (.config src)
     check? (:fileAccessCheck cfg)
     fpath (str (:path args))
     gist (:gist args)]
    (log/debug "request for file: %s" fpath)
    (if (or (.startsWith fpath ps)
            (false? check?))
      (->> (io/file (maybeStripUrlCrap fpath))
           (getStatic gist evt))
      (do
        (log/warn "illegal access: %s" fpath)
        (.setStatus res 403)
        (.replyResult evt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- replyError

  ""
  [^IoService src code]

  (let [appDir (-> ^Container
                   (.server src) (.appDir))]
    (->> (str DN_PAGES "errors/" code ".html")
         (getLocalContent appDir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn serveError

  "Reply back an error"
  [^IoService src ^Channel ch code]

  (try
    (let
      [rsp (httpReply<> code)
       rts (-> ^Container
               (.server src)
               (.cljrt))
       ctype "text/plain"
       bits nil
       wf nil
       cfg (.config src)
       h (:errorHandler cfg)
       rc (if (hgl? h)
            (cast? WebContent
                   (.callEx rts h code)))
       ^WebContent
       rc (or rc
              (replyError src code))]
      (->> (.contentType rc)
           (setHeader rsp "content-type"))
      (let [bits (.body rc)]
        (->> (if (some? bits)
               (alength bits) 0)
             (contentLength! rsp))
        (let [w1 (.writeAndFlush ch rsp)
              w2
              (if (some? bits)
                (->> (Unpooled/wrappedBuffer bits)
                     (.writeAndFlush ch ))
                w1)]
          (closeCF w2 false))))
      (catch Throwable e# (.close ch))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- serveStatic2

  "Reply back with a static file content"
  [^IoService src ^Channel ch gist ^HttpEvent evt]

  (let
    [^RouteInfo ri (.getv (.getx evt) :ri)
     parts (.getv (.getx evt) :riParts)
     mpt (.getv (.getx ri) :mountPoint)
     cfg (.config src)
     appDir (-> ^Container
                (.server src) (.appDir))
     ps (fpath (io/file appDir DN_PUB))
     mpt (.replace (str mpt)
                   "${app.dir}"
                   ^String (fpath appDir))
     mpt
     (-> #(cs/replace-first %1 "{}" %2)
         (reduce mpt parts))
     mpt (fpath (io/file mpt))]
    (.hold src evt (:waitMillis cfg))
    (.dispatchEx
      src
      evt
      {:router "czlab.skaro.mvc.comms/assetHandler<>"
       :gist gist
       :path mpt})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn serveStatic

  "Reply back with a static file content"
  [^IoService src ^Channel ch gist ^HttpEvent evt]

  (let
    [exp
     (try
       (do->nil
         (upstream gist
                   (.appKeyBits (.server src))
                   (:maxIdleSecs (.config src))))
       (catch AuthError e# e#))]
    (if (some? exp)
      (serveError src ch 403)
      (serveStatic2 src ch gist evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- serveRoute2

  "Handle a matched route"
  [^IoService src ^Channel ch gist ^HttpEvent evt]

  (let
    [^RouteInfo ri (.getv (.getx evt) :ri)
     pms (.getv (.getx evt) :riParams)
     cfg (.config src)
     options {:router (.handler ri)
              :params (or pms {})
              :template (.template ri)}]
    (.hold src evt (:waitMillis cfg))
    (.dispatchEx src evt options)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn serveRoute

  "Handle a matched route"
  [^IoService src ^Channel ch gist ^HttpEvent evt]

  (let
    [exp
     (try
       (do->nil
         (upstream gist
                   (.appKeyBits (.server src))
                   (:maxIdleSecs (.config src))))
       (catch AuthError e# e#))]
    (if (some? exp)
      (serveError src ch 403)
      (serveRoute2 src ch gist evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ASSET_HANDLER
  (workStream<>
    (script<>
      #(let [^IoEvent evt (.event ^Job %2)]
         (handleStatic (.source evt)
                       evt
                       (.getv ^Job %2 EV_OPTS))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn assetHandler<> "" ^WorkStream [] ASSET_HANDLER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

