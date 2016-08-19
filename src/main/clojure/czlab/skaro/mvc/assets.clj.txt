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

  czlab.skaro.mvc.assets

  (:require
    [czlab.xlib.core :refer [do->nil try! fpath]]
    [czlab.xlib.mime :refer [guessContentType]]
    [czlab.xlib.str :refer [lcase ewicAny?]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.files
     :refer [slurpBytes writeFile]]
    [czlab.xlib.io :refer [streamify]])

  (:use [czlab.skaro.io.http]
        [czlab.netty.core])

  (:import
    [io.netty.handler.stream ChunkedStream ChunkedFile]
    [io.netty.handler.codec.http.cookie
     ServerCookieEncoder
     CookieDecoder]
    [io.netty.handler.codec.http
     DefaultHttpResponse
     HttpRequest
     HttpResponse
     HttpResponseStatus
     HttpVersion
     HttpMethod
     LastHttpContent
     HttpHeaders
     Cookie
     QueryStringDecoder]
    [java.io Closeable RandomAccessFile File]
    [io.netty.channel
     ChannelFutureListener
     Channel
     ChannelHandler
     ChannelFuture
     ChannelPipeline
     ChannelHandlerContext]
    [org.apache.commons.io FileUtils]
    [czlab.skaro.mvc
     WebContent
     WebAsset
     HttpRangeInput ]
    [czlab.xlib Muble]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private cache-assets-flag (atom false))
(def ^:private asset-cache (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn setCacheAssetsFlag

  "Toggle caching of assers"
  [cacheFlag]

  (reset! cache-assets-flag (true? cacheFlag))
  (log/info "web assets caching is set to %s" @cache-assets-flag))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getLocalFile

  ""
  ^WebContent
  [appDir fname]

  (let [f (io/file appDir fname) ]
    (when (.canRead f)
      (reify
          WebContent
          (contentType [_]
            (guessContentType f "utf-8"))
          (body [_]
            (writeFile f))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeCached?

  "cache certain files"
  [^File fp]

  (if @cache-assets-flag
    (-> (fpath fp)
        (ewicAny? [ ".css" ".gif" ".jpg" ".jpeg" ".png" ".js"]))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn webAsset

  ""
  ^WebAsset
  [^File file]

  (let [ct (guessContentType file "utf-8" "text/plain")
        ts (.lastModified file) ]
    (reify
      WebAsset

      (contentType [_] ct)
      (getFile [_] file)
      (getTS [_] ts)
      (size [_] (.length file))
      (getBytes [_] (slurpBytes file)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fetchAsset

  ""
  ^WebAsset
  [^File file]

  (when (and (.exists file)
             (.canRead file))
    (webAsset file)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fetchAndSetAsset

  ""
  ^WebAsset
  [^File file]

  (if-some [wa (fetchAsset file) ]
    (let [fp (fpath file)]
      (log/debug "asset-cache: cached new file: %s" fp)
      (swap! asset-cache assoc fp wa)
      wa)
    (do->nil
      (log/warn "asset-cache: failed to read/find file: %s" file))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getAsset

  ""
  ^WebAsset
  [^File file]

  (if @cache-assets-flag
    (let [^WebAsset wa (@asset-cache (fpath file))
          cf (if (some? wa) (.getFile wa)) ]
      (if (or (nil? cf)
              (> (.lastModified file)
                 (.getTS wa)))
        (fetchAndSetAsset file)
        wa))
    (fetchAsset file)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getFileInput

  ""
  ^ChunkedInput
  [^File file gist ^HttpResponse rsp ]

  (HttpRangeInput/fileRange (getInHeader gist "range") file rsp))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn replyFileAsset

  ""
  [^Channel ch gist ^HttpResponse rsp ^File file]

  (let [^WebAsset
        asset (if-not (maybeCached? file)
                nil
                (getAsset file))
        fname (.getName file) ]
    (with-local-vars
      [inp nil clen (.length file)]
      (if (nil? asset)
        (do
          (->> (guessContentType file "utf-8" "text/plain")
               (setHeader rsp "content-type" ))
          (var-set inp (getFileInput file gist rsp)))
        (do
          (->> (.contentType asset)
               (setHeader rsp "content-type" ))
          (var-set inp (ChunkedStream. (streamify (.getBytes asset))))))
      (log/debug (str "serving file: %s with "
                      "clen= %s, ctype= %s")
                 fname clen (getHeader rsp "content-type"))
      (try
        (when (= HttpResponseStatus/NOT_MODIFIED
                 (.getStatus rsp))
          (var-set clen 0))
        (addHeader rsp "Accept-Ranges" "bytes")
        (HttpHeaders/setContentLength rsp @clen)
        (let [wf1 (.writeAndFlush ch rsp)
              wf2
              (if-not (or (= (:method gist) "HEAD")
                          (== 0 @clen))
                (.writeAndFlush ch @inp)
                (do->nil (.close ^ChunkedInput @inp)))]
          (closeCF (or wf2 wf1) (:keepAlive? gist)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


