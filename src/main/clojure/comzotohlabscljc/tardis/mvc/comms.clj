;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.tardis.mvc.comms

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.core :only [MubleAPI Try! NiceFPath] ])
  (:use [comzotohlabscljc.tardis.io.triggers])
  (:use [comzotohlabscljc.tardis.io.http :only [HttpBasicConfig] ])
  (:use [comzotohlabscljc.tardis.io.netty])
  (:use [comzotohlabscljc.tardis.io.core])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [comzotohlabscljc.tardis.core.constants])
  (:use [comzotohlabscljc.tardis.mvc.templates
         :only [GetLocalFile ReplyFileAsset] ])
  (:use [comzotohlabscljc.util.str :only [hgl? nsb strim] ])
  (:use [comzotohlabscljc.util.meta :only [MakeObj] ])
  (:import (com.zotohlabs.gallifrey.mvc HTTPErrorHandler
                                        MVCUtils WebAsset WebContent))
  (:import (com.zotohlabs.frwk.core Hierarchial Identifiable))
  (:import (com.zotohlabs.gallifrey.io HTTPEvent Emitter))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import [com.zotohlabs.frwk.netty NettyFW])
  (:import (java.util Date))
  (:import (java.io File))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (io.netty.handler.codec.http HttpRequest HttpResponse
                                        CookieDecoder ServerCookieEncoder
                                        DefaultHttpResponse HttpVersion
                                        HttpServerCodec
                                        HttpHeaders LastHttpContent
                                        HttpHeaders Cookie QueryStringDecoder))
  (:import (io.netty.buffer Unpooled))
  (:import (io.netty.channel Channel ChannelHandler
                             ChannelPipeline ChannelHandlerContext))
  (:import (com.zotohlabs.frwk.netty NettyFW))
  (:import (jregex Matcher Pattern)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isModified ""

  [^String eTag lastTm ^HttpRequest req]

  (with-local-vars [ modd true ]
    (cond
      (.containsHeader req "if-none-match")
      (var-set modd (not= eTag (HttpHeaders/getHeader req "if-none-match")))

      (.containsHeader req "if-unmodified-since")
      (if-let [ s (HttpHeaders/getHeader req "if-unmodified-since") ]
          (Try! (when (>= (.getTime (.parse (MVCUtils/getSDF) s)) lastTm)
                      (var-set modd false))))
      :else nil)
    @modd
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addETag ""

  [ ^comzotohlabscljc.tardis.core.sys.Element src
    ^HTTPEvent evt
    ^JsonObject info
    ^HttpResponse rsp
    ^File file ]

  (let [ maxAge (.getAttr src :cacheMaxAgeSecs)
         lastTm (.lastModified file)
         eTag  (str "\""  lastTm  "-"
                    (.hashCode file)  "\"") ]
    (if (isModified eTag lastTm req)
        (HttpHeaders/setHeader rsp "last-modified"
                  (.format (MVCUtils/getSDF) (Date. lastTm)))
        (if (= (-> (.get info "method")(.getAsString)) "GET")
            (.setStatus rsp HttpResponseStatus/NOT_MODIFIED)))
    (HttpHeaders/setHeader rsp "cache-control"
                (if (= maxAge 0) "no-cache" (str "max-age=" maxAge)))
    (when (.getAttr src :useETag) (HttpHeaders/setHeader rsp "etag" eTag))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reply-error ""

  [^Emitter src code]

  (let [ ctr (.container src)
         appDir (.getAppDir ctr) ]
    (GetLocalFile appDir (str "pages/errors/" code ".html"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeError ""

  [ ^comzotohlabscljc.tardis.core.sys.Element src
    ^Channel ch
    code ]

  (with-local-vars [ rsp (NettyFW/makeHttpReply code) bits nil wf nil]
    (try
      (let [ h (.getAttr src :errorHandler)
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
                                      (if (nil? @bits) 0 (alength ^bytes @bits)))
        (var-set wf (.write ch @rsp))
        (when-not (nil? @bits)
          (var-set wf (.write ch (Unpooled/wrappedBuffer ^bytes @bits))))
        (NettyFW/closeCF @wf false))
      (catch Throwable e#
        (NettyFW/closeChannel ch)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleStatic ""

  [src ^Channel ch info ^HTTPEvent evt ^File file]

  (let [ rsp (NettyFW/makeHttpReply ) ]
    (try
      (if (or (nil? file)
              (not (.exists file)))
        (ServeError src ch 404)
        (do
          (log/debug "serving static file: " (NiceFPath file))
          (addETag src evt info rsp file)
          ;; 304 not-modified
          (if (= (-> rsp (.getStatus)(.getCode)) 304)
            (do
              (HttpHeaders/setContentLength rsp 0)
              (NettyFW/closeCF (.write ch rsp) (.isKeepAlive evt) ))
            (ReplyFileAsset src ch info rsp file))))
      (catch Throwable e#
        (log/error "failed to get static resource " (.getUri evt) e#)
        (Try!  (ServeError src ch 500))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeStatic ""

  [ ^Emitter src
    ^comzotohlabscljc.net.rts.RouteInfo ri
    ^Matcher mc ^Channel ch info ^HTTPEvent evt]

  (let [ ^File appDir (-> src (.container)(.getAppDir))
         mpt (nsb (.getf ^comzotohlabscljc.util.core.MubleAPI ri :mountPoint))
         ps (NiceFPath (File. appDir ^String DN_PUBLIC))
         uri (.getUri evt)
         gc (.groupCount mc) ]
    (with-local-vars [ mp (StringUtils/replace mpt "${app.dir}" (NiceFPath appDir)) ]
      (if (> gc 1)
        (doseq [ i (range 1 gc) ]
          (var-set mp (StringUtils/replace ^String @mp "{}" (.group mc (int i)) 1))) )

      ;; ONLY serve static assets from *public folder*
      (var-set mp (NiceFPath (File. ^String @mp)))
      (log/debug "request to serve static file: " @mp)

      (if (.startsWith ^String @mp ps)
        (handleStatic src ch info evt (File. ^String @mp))
        (do
          (log/warn "attempt to access non public file-system: " @mp)
          (ServeError src ch 403))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeRoute ""

  [ ^comzotohlabscljc.tardis.core.sys.Element src
    ^comzotohlabscljc.net.rts.RouteInfo ri
    ^Matcher mc
    ^Channel ch
    ^comzotohlabscljc.util.core.MubleAPI evt]

  (let [ wms (.getAttr src :waitMillis)
         pms (.collect ri mc)
         options { :router (.getHandler ri)
                   :params (merge {} pms)
                   :template (.getTemplate ri) } ]
    (let [ ^comzotohlabscljc.tardis.io.core.EmitterAPI co src
           ^comzotohlabscljc.tardis.io.core.WaitEventHolder
           w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
      (.timeoutMillis w wms)
      (.hold co w)
      (.dispatch co evt options))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private comms-eof nil)

