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

  comzotohlabscljc.tardis.mvc.handler)

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

(defn RouteDispatcher ""

  ^ChannelHandler
  [ ^com.zotohlabs.gallifrey.io.Emitter co ]

  (proxy [ChannelInboundHandlerAdapter] []
    (channelRead [c msg]
      (let [ ^ChannelHandlerContext ctx c
             ^XData xs (:payload msg) 
             ch (.channel ctx)
             info (:info msg)
             ^HTTPEvent evt (ioes-reify-event co ch info xs)
             ^comzotohlabscljc.tardis.core.sys.Element
             ctr (.container co)
             ^comzotohlabscljc.netty.comms.RouteCracker
             rcc (.getAttr ^comzotohlabscljc.tardis.core.sys.Element co :rtcObj)
             [r1 ^comzotohlabscljc.net.rts.RouteInfo r2 r3 r4]
             (.crack rcc msginfo) ]
        (cond
          (and r1 (hgl? r4))
          (SendRedirect ch false r4)

          (= r1 true)
          (do
            (debug "matched one route: " (.getPath r2) " , and static = " (.isStatic? r2))
            (if (.isStatic? r2)
              (ServeStatic co r2 r3 ch info evt)
              (ServeRoute co r2 r3 ch evt)))

          :else
          (do
            (debug "failed to match uri: " (.getUri evt))
            (ServeError co ch 404)) )))
  ))

(defn- makeRouteDispatcher [ co ]
  (fn [^ChannelPipeline pipe options]
    (.addLast pipe "" (RouteDispatcher. co))))

(defn- mvcInitor ""

  [co]

  (let [ disp (makeRouteDispatcher co) ]
    (fn [^ChannelPipeline pipe options]

      (-> pipe
        (AddEnableSSL options)
        (AddRouteFilter options)
        (AddExpect100 options)
        (AddServerCodec options)
        (AddAuxDecoder options)
        (AddWriteChunker options)
        (disp options)
        (AddExceptionCatcher options)

      ))
  ))

(defn- init-netty
  [^comzotohlabscljc.tardis.core.sys.Element co]
  (let [ ^comzotohlabscljc.tardis.core.sys.Element
         ctr (.parent ^Hierarchial co)
         rtc (makeRouteCracker (.getAttr ctr :routes))
         options { :serverkey (.getAttr co :serverKey)
                   :passwd (.getAttr co :pwd)
                   :rtcObj rtc }
         nes (BootstrapNetty (mvcInitor co) options) ]
    (debug "server-netty - made - success.")
    (.setAttr! co :rtcObj rtc)
    (.setAttr! co :netty nes)
    co))

(defmethod comp-configure :czc.tardis.io/NettyMVC
  [^comzotohlabscljc.tardis.core.sys.Element co cfg]
  (let [ c (nsb (:context cfg)) ]
    (.setAttr! co :contextPath (strim c))
    (.setAttr! co :cacheMaxAgeSecs (:cacheMaxAgeSecs cfg))
    (.setAttr! co :useETags (:useETags cfg))
    (.setAttr! co :welcomeFiles (:welcomeFiles cfg))
    (.setAttr! co :router (strim (:handler cfg)))
    (.setAttr! co :errorRouter (strim (:errorHandler cfg)))
    (http-basic-config co cfg) ))

(defmethod comp-initialize :czc.tardis.io/NettyMVC
  [^comzotohlabscljc.tardis.core.sys.Element co]
  (init-netty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private handler-eof nil)

