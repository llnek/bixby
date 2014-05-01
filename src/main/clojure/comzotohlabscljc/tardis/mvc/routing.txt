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

  comzotohlabscljc.tardis.mvc.routing

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.core :only [MubleAPI Try! NiceFPath] ])
  (:use [comzotohlabscljc.tardis.io.http :only [HttpBasicConfig] ])
  (:use [comzotohlabscljc.tardis.io.triggers])
  (:use [comzotohlabscljc.tardis.io.netty])
  (:use [comzotohlabscljc.tardis.io.core])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [comzotohlabscljc.tardis.core.constants])
  (:use [comzotohlabscljc.tardis.mvc.tpls :only [GetLocalFile ReplyFileAsset] ])
  (:use '[comzotohlabscljc.util.str :only [hgl? nsb strim] ])
  (:use '[comzotohlabscljc.util.meta :only [MakeObj] ])
  (:import (com.zotohlabs.gallifrey.mvc HTTPErrorHandler MVCUtils WebAsset WebContent))
  (:import (com.zotohlabs.frwk.core Hierarchial Identifiable))
  (:import [com.zotohlabs.frwk.netty NettyFW])
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (java.util Date))
  (:import (java.io File))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (com.zotohlabs.gallifrey.io HTTPEvent Emitter))
  (:import (org.jboss.netty.buffer ChannelBuffers ChannelBuffer))
  (:import (org.jboss.netty.channel Channel))
  (:import (org.jboss.netty.handler.codec.http HttpHeaders$Values HttpHeaders$Names
                                               DefaultHttpRequest HttpVersion
                                               HttpMessage HttpRequest HttpResponse 
                                               HttpContentCompressor HttpHeaders 
                                               HttpResponseStatus
                                               DefaultHttpResponse HttpMethod))
  (:import (com.zotohlabs.frwk.net NetUtils))
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
      (let [ s (HttpHeaders/getHeader req "if-unmodified-since") ]
        (when (hgl? s)
          (Try! (when (>= (.getTime (.parse (MVCUtils/getSDF) s)) lastTm)
                     (var-set modd false)))))
      :else nil)
    @modd
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- addETag ""

  [^comzotohlabscljc.tardis.core.sys.Element src
   ^HTTPEvent evt ^HttpRequest req ^HttpResponse rsp
   ^File file ]

  (let [ maxAge (.getAttr src :cacheMaxAgeSecs)
         lastTm (.lastModified file)
         eTag  (str "\""  lastTm  "-"  (.hashCode file)  "\"") ]
    (if (isModified eTag lastTm req)
        (HttpHeaders/setHeader rsp "last-modified"
                  (.format (MVCUtils/getSDF) (Date. lastTm)))
        (if (= (.getMethod req) HttpMethod/GET)
          (.setStatus rsp HttpResponseStatus/NOT_MODIFIED)))
    (HttpHeaders/setHeader rsp "cache-control"
                (if (= maxAge 0) "no-cache" (str "max-age=" maxAge)))
    (when (.getAttr src :useETag)
      (HttpHeaders/setHeader rsp "etag" eTag)) 
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reply-error ""
  
  [^Emitter src code]

  (let [ ctr (.container src)
         appDir (.getAppDir ctr) ]
    (GetLocalFile appDir (str "pages/errors/" code ".html"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- serve-error ""

  [^comzotohlabscljc.tardis.core.sys.Element src
   ^Channel ch
   code]

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
          (var-set wf (.write ch (ChannelBuffers/wrappedBuffer ^bytes @bits))))
        (NettyFW/closeCF false @wf))
      (catch Throwable e#
        (NettyFW/closeChannel ch)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleStatic ""
  
  [src ^Channel ch req ^HTTPEvent evt ^File file]

  (let [ rsp (NettyFW/makeHttpReply ) ]
    (try
      (if (or (nil? file)
              (not (.exists file)))
        (serve-error src ch 404)
        (do
          (log/debug "serving static file: " (NiceFPath file))
          (addETag src evt req rsp file)
          ;; 304 not-modified
          (if (= (-> rsp (.getStatus)(.getCode)) 304)
            (do
              (HttpHeaders/setContentLength rsp 0)
              (NettyFW/closeCF (.write ch rsp) (.isKeepAlive evt) ))
            (ReplyFileAsset src ch req rsp file))))
      (catch Throwable e#
        (log/error "failed to get static resource " (.getUri evt) e#)
        (Try!  (serve-error src ch 500)))) 
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- serveWelcomeFile ""
  
  [^HTTPEvent evt]

  (if (not (.matches (.getUri evt) "/?"))
    nil
    (let [ ^Emitter src (.emitter evt)
           ctr (.container src)
           appDir (.getAppDir ctr)
           fs (.getAttr ^comzotohlabscljc.tardis.core.sys.Element src :welcomeFiles) ]
      (some (fn [^String f]
              (let [ file (File. appDir (str DN_PUBLIC "/" f)) ]
                (if (and (.exists file)
                         (.canRead file)) file nil)))
            (seq fs)) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- serveStatic ""

  [^Emitter src
   ^comzotohlabscljc.net.rts.RouteInfo ri
   ^Matcher mc ^Channel ch req ^HTTPEvent evt]

  (let [ ^File appDir (-> src (.container)(.getAppDir))
         mpt (nsb (.getf ^comzotohlabscljc.util.core.MubleAPI ri :mountPoint))
         ps (NiceFPath (File. appDir ^String DN_PUBLIC))
         uri (.getUri evt)
         gc (.groupCount mc) ]
    (with-local-vars [ mp (StringUtils/replace mpt
                                               "${app.dir}"
                                               (NiceFPath appDir)) ]
      (if (> gc 1)
        (doseq [ i (range 1 gc) ]
          (var-set mp (StringUtils/replace ^String @mp "{}" (.group mc (int i)) 1))) )

      ;; ONLY serve static assets from *public folder*
      (var-set mp (NiceFPath (File. ^String @mp)))
      (log/debug "request to serve static file: " @mp)
      (if (.startsWith ^String @mp ps)
        (handleStatic src ch req evt (File. ^String @mp))
        (do
          (log/warn "attempt to access non public file-system: " @mp)
          (serve-error src ch 403)
          )))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- serveRoute ""

  [^comzotohlabscljc.tardis.core.sys.Element src
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
(defn- make-service-io [^comzotohlabscljc.tardis.io.core.EmitterAPI co]
  (reify comzotohlabscljc.netty.comms.NettyServiceIO
    (onReply [_ ch rsp msginfo rdata] nil)
    (onError [_ ch msginfo exp]  nil)
    (preSend [_ ch msg] nil)
    (onRequest [_ ch req msginfo rdata]
      (let [ ^HTTPEvent evt (ioes-reify-event co ch req rdata)
             ^comzotohlabscljc.tardis.core.sys.Element
             ctr (.container ^com.zotohlabs.gallifrey.io.Emitter co)
             ^comzotohlabscljc.netty.comms.RouteCracker
             rcc (.getAttr ^comzotohlabscljc.tardis.core.sys.Element co :rtcObj)
             [r1 ^comzotohlabscljc.net.rts.RouteInfo r2 r3 r4]
             (.crack rcc msginfo) ]
        (cond
          (and r1 (hgl? r4))
          (sendRedirect ch false r4)

          (= r1 true)
          (do
            (debug "matched one route: " (.getPath r2) " , and static = " (.isStatic? r2))
            (if (.isStatic? r2)
              (serveStatic co r2 r3 ch req evt)
              (serveRoute co r2 r3 ch evt)))

          :else
          (do
            (debug "failed to match uri: " (.getUri evt))
            (serve-error co ch 404)) )))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RouteFilter ""

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^comzotohlabscljc.netty.comms.RouteCracker rcc (.getAttr co :rtcObj) ]
    (proxy [SimpleChannelInboundHandler][]
      (channelRead0 [c msg]
        (if (instance? HttpRequest msg)
          (let [ ^ChannelHandlerContext ctx c
                 ch (.channel ctx)
                 ^HttpRequest req msg
                 [r1 ^comzotohlabscljc.net.rts.RouteInfo r2 r3 r4]
                 (.crack rcc msginfo) ]
            (cond
              (and r1 (hgl? r4))
              (NettyFW/sendRedirect ch false r4)

              (= r1 true)
              (.fireChannelRead ctx msg)

              :else
              (do
                (log/debug "failed to match uri: " (.getUri msg))
                (serve-error co ch 404)) ))
          (.fireChannelRead ctx msg))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private routing-eof nil)

