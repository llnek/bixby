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

  czlab.skaro.mvc.assets

  (:require
    [czlab.xlib.core :refer [do->nil try! fpath]]
    [czlab.xlib.mime :refer [guessContentType]]
    [czlab.xlib.str :refer [lcase ewicAny?]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.files
     :refer [readFileBytes writeOneFile]]
    [czlab.xlib.io :refer [streamify]])

  (:use [czlab.skaro.io.http]
        [czlab.netty.io])

  (:import
    [io.netty.handler.codec.http HttpRequest HttpResponse
     HttpResponseStatus
     CookieDecoder ServerCookieEncoder
     DefaultHttpResponse HttpVersion
     HttpMethod
     LastHttpContent
     HttpHeaders Cookie QueryStringDecoder]
    [io.netty.handler.stream ChunkedStream ChunkedFile]
    [java.io Closeable RandomAccessFile File]
    [io.netty.channel Channel
     ChannelHandler
     ChannelFutureListener ChannelFuture
     ChannelPipeline ChannelHandlerContext]
    [org.apache.commons.io FileUtils]
    [czlab.skaro.mvc WebContent
     WebAsset
     HTTPRangeInput ]
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
(defn getLocalFile ""

  ^WebContent
  [appDir fname]

  (let [f (io/file appDir fname) ]
    (when (.canRead f)
      (reify
          WebContent
          (contentType [_]
            (guessContentType f "utf-8"))
          (body [_]
            (writeOneFile f))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeCache

  "cache certain files"

  [^File fp]

  (if @cache-assets-flag
    (-> (fpath fp)
        (ewicAny? [ ".css" ".gif" ".jpg" ".jpeg" ".png" ".js"]))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn webAsset ""

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
      (getBytes [_] (readFileBytes file)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fetchAsset ""

  ^WebAsset
  [^File file]

  (when (and (.exists file)
             (.canRead file))
    (webAsset file)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fetchAndSetAsset ""

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
(defn- getAsset ""

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
(defn- getFileInput ""

  [^RandomAccessFile raf
   ^String ct
   info
   ^HttpResponse rsp ]

  (let [s (getInHeader info "range")]
    (if (HTTPRangeInput/isAcceptable s)
      (doto (HTTPRangeInput. raf ct s)
        (.process rsp))
      (ChunkedFile. raf))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn replyFileAsset ""

  [src ^Channel ch info ^HttpResponse rsp ^File file]

  (let [^WebAsset
        asset (if-not (maybeCache file)
                nil
                (getAsset file))
        fname (.getName file) ]
    (with-local-vars
      [raf nil clen 0
       inp nil ct "" wf nil]
      (if (nil? asset)
        (do
          (var-set ct (guessContentType file "utf-8" "text/plain"))
          (var-set raf (RandomAccessFile. file "r"))
          (var-set clen (.length ^RandomAccessFile @raf))
          (var-set inp (getFileInput @raf @ct info rsp)))
        (do
          (var-set ct (.contentType asset))
          (var-set clen (.size asset))
          (var-set inp (ChunkedStream. (streamify (.getBytes asset))))) )
      (log/debug (str "serving file: %s with "
                      "clen= %s, ctype= %s")
                 fname @clen @ct)
      (try
        (when (= HttpResponseStatus/NOT_MODIFIED
                 (.getStatus rsp))
              (var-set clen 0))
        (addHeader rsp "Accept-Ranges" "bytes")
        (setHeader rsp "Content-Type" @ct)
        (HttpHeaders/setContentLength rsp @clen)
        (var-set wf (.writeAndFlush ch rsp))
        (when-not (or (= (:method info) "HEAD")
                      (== 0 @clen))
                  (var-set wf (.writeAndFlush ch @inp)))
        (futureCB @wf #(do
                        (log/debug "channel-future-op-cmp: %s, file = %s" %1 fname)
                        (try! (when (some? @raf) (.close ^Closeable @raf)))
                        (when-not (:keepAlive info)
                          (.close ch))))
        (catch Throwable e#
          (try! (when (some? @raf)(.close ^Closeable @raf)))
          (log/error e# "")
          (try! (.close ch))) ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


