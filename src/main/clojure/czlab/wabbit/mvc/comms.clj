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
      :author "Kenneth Leung"}

  czlab.wabbit.mvc.comms

  (:require [czlab.xlib.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as cs])

  (:use [czlab.convoy.netty.core]
        [czlab.wabbit.io.http]
        [czlab.wabbit.io.web]
        [czlab.xlib.consts]
        [czlab.flux.wflow.core]
        [czlab.wabbit.io.core]
        [czlab.wabbit.sys.core])

  (:import [czlab.convoy.net HttpResult RouteInfo RouteCracker]
           [czlab.xlib XData Muble Hierarchial Identifiable]
           [czlab.wabbit.io IoService IoEvent HttpEvent]
           [io.netty.handler.codec.http HttpResponseStatus]
           [czlab.wabbit.server Container]
           [czlab.flux.wflow WorkStream Job]
           [czlab.wabbit.etc AuthError]
           [java.util Date]
           [java.io File]
           [io.netty.buffer Unpooled]
           [io.netty.channel Channel]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeStripUrlCrap
  "Want to handle case where the url has stuff after the file name.
   For example:  /public/blab&hhh or /public/blah?ggg"
  ^String
  [^String path]

  (let [pos (.lastIndexOf path (int \/))]
    (if (spos? pos)
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
  [^HttpEvent evt file]
  (let [^Channel ch (.socket evt)
        res (httpResult<> )
        gist (.msgGist evt)
        fp (io/file file)]
    (log/debug "serving file: %s" (fpath fp))
    (try
      (if (or (nil? fp)
              (not (.exists fp)))
        (do
          (.setStatus res (.code HttpResponseStatus/NOT_FOUND))
          (replyResult ch res))
        (do
          (.setContent res fp)
          (replyResult ch res)))
      (catch Throwable e#
        (log/error "get: %s" (:uri gist) e#)
        (try!
          (.setStatus res
                      (.code HttpResponseStatus/INTERNAL_SERVER_ERROR))
          (.setContent res nil)
          (replyResult ch res))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleStatic
  "Handle static resource"
  [^HttpEvent evt args]
  (let
    [appDir (.. evt source server appDir)
     pubDir (io/file appDir DN_PUB)
     cfg (.config src)
     check? (:fileAccessCheck? cfg)
     fpath (str (:path args))
     gist (.msgGist evt)]
    (log/debug "request for file: %s" fpath)
    (if (or (.startsWith fpath (fpath pubDir))
            (false? check?))
      (->> (maybeStripUrlCrap fpath)
           (getStatic evt))
      (do
        (log/warn "illegal access: %s" fpath)
        (-> (httpResult<> HttpResponseStatus/FORBIDDEN)
            (replyResult evt res))))))

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
  [^HttpEvent evt]

  (let
    [appDir (.. evt source server appDir)
     pubDir (io/file appDir DN_PUB)
     gist (.msgGist evt)
     r (:route gist)
     ^RouteInfo ri (:info r)
     mpt (-> (.getx ri)
             (.getv :mountPoint))
     {:keys [waitMillis]}
     (.. evt source config)
     mpt (reduce
           #(cs/replace-first %1 "{}" %2)
           (.replace (str mpt)
                     "${app.dir}" (fpath appDir))
           (:groups r))
     mDir (io/file mpt)]
    (if (spos? waitMillis)
      (.hold (.source evt) evt waitMillis))
    (.dispatchEx (.source evt)
                 evt
                 {:router "czlab.wabbit.mvc.comms/assetHandler<>"
                  :path (fpath mDir)})))

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
      (serveError evt HttpResponseStatus/FORBIDDEN)
      (serveStatic2 evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- serveRoute2
  "Handle a matched route"
  [^HttpEvent evt]
  (let
    [gist (.msgGist evt)
     r (:route gist)
     ^RouteInfo ri (:info r)
     pms (:places r)
     {:keys [waitMillis]}
     (.. evt source config)
     options {:router (.handler ri)
              :params (or pms {})
              :template (.template ri)}]
    (if (spos? waitMillis)
      (.hold (.source evt) evt waitMillis))
    (.dispatchEx (.source evt) evt options)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn serveRoute
  "Handle a matched route"
  [^HttpEvent evt]
  (let
    [exp
     (try
       (do->nil
         (upstream evt
                   (.. evt source server appKeyBits)
                   (:maxIdleSecs (.config src))))
       (catch AuthError _ _))]
    (if (some? exp)
      (serveError src ch 403)
      (serveRoute2 src ch gist evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ASSET_HANDLER
  (workStream<>
    (script<>
      #(let [evt (.event ^Job %2)]
         (handleStatic evt
                       (.getv ^Job %2 EV_OPTS))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn assetHandler<> "" ^WorkStream [] ASSET_HANDLER)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

