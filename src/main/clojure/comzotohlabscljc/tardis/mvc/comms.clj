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

  comzotohlabscljc.tardis.mvc.comms)

(import '(org.apache.commons.lang3 StringUtils))
(import '(java.util Date))
(import '(java.io File))
(import '(com.zotohlabs.frwk.io XData))
(import '(com.zotohlabs.frwk.core Hierarchial Identifiable))

(import '(com.zotohlabs.gallifrey.io HTTPEvent Emitter))
(import '(com.zotohlabs.gallifrey.mvc
  HTTPErrorHandler MVCUtils WebAsset WebContent))

(import '(org.jboss.netty.buffer ChannelBuffers ChannelBuffer))
(import '(org.jboss.netty.channel Channel))

(import '(org.jboss.netty.handler.codec.http
  HttpHeaders$Values HttpHeaders$Names
  DefaultHttpRequest
  HttpContentCompressor HttpHeaders HttpVersion
  HttpMessage HttpRequest HttpResponse HttpResponseStatus
  DefaultHttpResponse HttpMethod))

(import '(com.zotohlabs.frwk.net NetUtils))
(import '(jregex Matcher Pattern))

(use '[clojure.tools.logging :only [info warn error debug] ])
(use '[comzotohlabscljc.util.core :only [MuObj Try! nice-fpath] ])
(use '[comzotohlabscljc.tardis.io.triggers])
(use '[comzotohlabscljc.tardis.io.http :only [http-basic-config] ])
(use '[comzotohlabscljc.tardis.io.netty])
(use '[comzotohlabscljc.tardis.io.core])
(use '[comzotohlabscljc.tardis.core.sys])
(use '[comzotohlabscljc.tardis.core.constants])
(use '[comzotohlabscljc.tardis.mvc.tpls :only [getLocalFile replyFileAsset] ])
(use '[comzotohlabscljc.netty.comms :only [sendRedirect makeRouteCracker
                                       makeServerNetty finzNetty addListener
                                       makeHttpReply closeCF] ])
(use '[comzotohlabscljc.util.str :only [hgl? nsb strim] ])
(use '[comzotohlabscljc.util.meta :only [make-obj] ])

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
    ^HttpRequest req
    ^HttpResponse rsp
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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reply-error ""

  [^Emitter src code]

  (let [ ctr (.container src)
         appDir (.getAppDir ctr) ]
    (getLocalFile appDir (str "pages/errors/" code ".html"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeError ""

  [ ^comzotohlabscljc.tardis.core.sys.Element src
    ^Channel ch
    code ]

  (with-local-vars [ rsp (MakeHttpReply code) bits nil wf nil]
    (try
      (let [ h (.getAttr src :errorHandler)
             ^HTTPErrorHandler
             cb (if (hgl? h) (make-obj h) nil)
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
        (CloseCF @wf false))
      (catch Throwable e#
        (NetUtils/closeChannel ch)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleStatic ""

  [src ^Channel ch req ^HTTPEvent evt ^File file]

  (let [ rsp (MakeHttpReply ) ]
    (try
      (if (or (nil? file)
              (not (.exists file)))
        (ServeError src ch 404)
        (do
          (debug "serving static file: " (nice-fpath file))
          (addETag src evt req rsp file)
          ;; 304 not-modified
          (if (= (-> rsp (.getStatus)(.getCode)) 304)
            (do
              (HttpHeaders/setContentLength rsp 0)
              (CloseCF (.write ch rsp) (.isKeepAlive evt) ))
            (replyFileAsset src ch req rsp file))))
      (catch Throwable e#
        (error "failed to get static resource " (.getUri evt) e#)
        (Try!  (ServeError src ch 500))))
  ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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
            (seq fs)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeStatic ""

  [ ^Emitter src
    ^comzotohlabscljc.net.rts.RouteInfo ri
    ^Matcher mc ^Channel ch req ^HTTPEvent evt]

  (let [ ^File appDir (-> src (.container)(.getAppDir))
         mpt (nsb (.getf ^comzotohlabscljc.util.core.MuObj ri :mountPoint))
         ps (nice-fpath (File. appDir ^String DN_PUBLIC))
         uri (.getUri evt)
         gc (.groupCount mc) ]

    (with-local-vars [ mp (StringUtils/replace mpt
                                               "${app.dir}"
                                               (nice-fpath appDir)) ]
      (if (> gc 1)
        (doseq [ i (range 1 gc) ]
          (var-set mp (StringUtils/replace ^String @mp "{}" (.group mc (int i)) 1))) )

      ;; ONLY serve static assets from *public folder*
      (var-set mp (nice-fpath (File. ^String @mp)))
      (debug "request to serve static file: " @mp)
      (if (.startsWith ^String @mp ps)
        (handleStatic src ch req evt (File. ^String @mp))
        (do
          (warn "attempt to access non public file-system: " @mp)
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
    ^comzotohlabscljc.util.core.MuObj evt]

  (let [ wms (.getAttr src :waitMillis)
         pms (.collect ri mc)
         options { :router (.getHandler ri)
                   :params (merge {} pms)
                   :template (.getTemplate ri) } ]
    (let [ ^comzotohlabscljc.tardis.io.core.EmitterAPI co src
           ^comzotohlabscljc.tardis.io.core.WaitEventHolder
           w (make-async-wait-holder (make-netty-trigger ch evt co) evt) ]
      (.timeoutMillis w wms)
      (.hold co w)
      (.dispatch co evt options))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private comms-eof nil)

