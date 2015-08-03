;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.mvc.assets

  (:require
    [czlab.xlib.util.core :refer [try! notnil? FPath]]
    [czlab.xlib.util.mime :refer [GuessContentType]]
    [czlab.xlib.util.str :refer [lcase nsb]]
    [czlab.xlib.util.files
    :refer [ReadFileBytes WriteOneFile]]
    [czlab.xlib.util.io :refer [Streamify]])

  (:require [czlab.xlib.util.logging :as log])

  (:use [czlab.skaro.io.http]
        [czlab.xlib.netty.io])

  (:import
    [io.netty.handler.codec.http HttpRequest HttpResponse
    HttpResponseStatus
    CookieDecoder ServerCookieEncoder
    DefaultHttpResponse HttpVersion
    HttpMethod
    HttpHeaders LastHttpContent
    HttpHeaders Cookie QueryStringDecoder]
    [io.netty.channel Channel ChannelHandler
    ChannelFutureListener ChannelFuture
    ChannelPipeline ChannelHandlerContext]
    [io.netty.handler.stream ChunkedStream ChunkedFile]
    [com.google.gson JsonObject JsonArray]
    [org.apache.commons.io FileUtils]
    [com.zotohlab.skaro.mvc WebContent
    WebAsset
    HTTPRangeInput AssetCache]
    [java.io Closeable RandomAccessFile File]
    [java.util Map HashMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private cache-assets-flag (atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetCacheAssetsFlag

  "Toggle caching of assers"

  [cacheFlag]

  (reset! cache-assets-flag (if cacheFlag true false))
  (log/info "web assets caching is set to %s" @cache-assets-flag))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeWebContent ""

  ^WebAsset
  [^String cType bits]

  (reify
    WebContent
    (contentType [_] cType)
    (body [_] bits)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetLocalFile ""

  ^WebAsset
  [^File appDir ^String fname]

  (let [f (File. appDir fname) ]
    (if (.canRead f)
      (makeWebContent
        (GuessContentType f "utf-8")
        (WriteOneFile f))
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeCache

  "cache certain files..."

  [^File fp]

  (if @cache-assets-flag
    (let [^String fpath (lcase (FPath fp)) ]
      (or (.endsWith fpath ".css")
          (.endsWith fpath ".gif")
          (.endsWith fpath ".jpg")
          (.endsWith fpath ".jpeg")
          (.endsWith fpath ".png")
          (.endsWith fpath ".js")))
    false
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeWebAsset ""

  ^WebAsset
  [^File file]

  (let [ct (GuessContentType file "utf-8" "text/plain")
        ts (.lastModified file) ]
    (reify
      WebAsset

      (contentType [_] ct)
      (getFile [_] file)
      (getTS [_] ts)
      (size [_] (.length file))
      (getBytes [_] (ReadFileBytes file)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fetchAsset ""

  ^WebAsset
  [^File file]

  (if (and (.exists file)
           (.canRead file))
    (MakeWebAsset file)
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fetchAndSetAsset ""

  ^WebAsset
  [^Map cache fp ^File file]

  (if-let [wa (fetchAsset file) ]
    (do
      (log/debug "asset-cache: cached new file: %s" fp)
      (.put cache fp wa)
      wa)
    (do
      (log/warn "asset-cache: failed to read/find file: %s" fp)
      nil)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getAsset ""

  ^WebAsset
  [^File file]

  (if @cache-assets-flag
    (let [cache (AssetCache/get)
          fp (FPath file)
          ^WebAsset wa (.get cache fp)
          ^File cf (if (nil? wa) nil (.getFile wa)) ]
      (if (or (nil? cf)
              (> (.lastModified file)
                 (.getTS wa)))
        (fetchAndSetAsset cache fp file)
        wa)
    )
    (fetchAsset file)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getFileInput ""

  [^RandomAccessFile raf
   ^String ct
   info
   ^HttpResponse rsp ]

  (let [s (nsb (GetInHeader info "range"))]
    (if (HTTPRangeInput/isAcceptable s)
      (doto (HTTPRangeInput. raf ct s)
        (.process rsp))
      (ChunkedFile. raf))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReplyFileAsset ""

  [src ^Channel ch info ^HttpResponse rsp ^File file]

  (let [^WebAsset asset (if-not (maybeCache file)
                          nil
                          (getAsset file))
        fname (.getName file) ]
    (with-local-vars [raf nil clen 0 inp nil ct "" wf nil]
      (if (nil? asset)
        (do
          (var-set ct (GuessContentType file "utf-8" "text/plain"))
          (var-set raf (RandomAccessFile. file "r"))
          (var-set clen (.length ^RandomAccessFile @raf))
          (var-set inp (getFileInput @raf @ct info rsp)))
        (do
          (var-set ct (.contentType asset))
          (var-set clen (.size asset))
          (var-set inp (ChunkedStream. (Streamify (.getBytes asset))))) )
      (log/debug (str "serving file: %s with "
                      "clen= %s, ctype= %s")
                 fname @clen @ct)
      (try
        (when (= HttpResponseStatus/NOT_MODIFIED
                 (.getStatus rsp))
              (var-set clen 0))
        (AddHeader rsp "Accept-Ranges" "bytes")
        (SetHeader rsp "Content-Type" @ct)
        (HttpHeaders/setContentLength rsp @clen)
        (var-set wf (.writeAndFlush ch rsp))
        (when-not (or (= (:method info) "HEAD")
                      (== 0 @clen))
                  (var-set wf (.writeAndFlush ch @inp)))
        (FutureCB @wf #(do
                        (log/debug "channel-future-op-cmp: %s, file = %s" %1 fname)
                        (try! (when (some? @raf) (.close ^Closeable @raf)))
                        (when-not (:keepAlive info)
                          (.close ch))))
        (catch Throwable e#
          (try! (when (some? @raf)(.close ^Closeable @raf)))
          (log/error e# "")
          (try! (.close ch))) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

