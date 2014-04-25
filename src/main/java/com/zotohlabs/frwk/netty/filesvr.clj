;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2014 Cherimoia, LLC. All rights reserved.


(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.netty.filesvr

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:import (java.io IOException File))
  (:import (io.netty.channel ChannelHandlerContext Channel ChannelPipeline
                             ChannelFuture ChannelHandler ))
  (:import (io.netty.handler.codec.http HttpHeaders HttpMessage HttpResponse ))
  (:import (io.netty.handler.stream ChunkedStream))
  (:import (com.zotohlabs.frwk.io XData))
  (:use [comzotohlabscljc.util.files :only [SaveFile GetFile] ])
  (:use [comzotohlabscljc.util.core :only [notnil? Try! TryC] ])
  (:use [comzotohlabscljc.netty.comms])
  (:use [comzotohlabscljc.netty.server])
  (:use [comzotohlabscljc.netty.ssl])
  (:use [comzotohlabscljc.netty.expect100])
  (:use [comzotohlabscljc.netty.auxdecode])
  (:use [comzotohlabscljc.netty.exception])
  (:use [comzotohlabscljc.util.str :only [strim nsb hgl?] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; file handlers
(defn- replyGetVFile ""

  [^Channel ch info ^XData xdata]

  (let [ kalive (and (notnil? info) (:keep-alive info))
         res (MakeHttpReply 200)
         clen (.size xdata) ]
    (HttpHeaders/setHeader res "content-type" "application/octet-stream")
    (HttpHeaders/setContentLength res clen)
    (HttpHeaders/setTransferEncodingChunked res)
    (WWrite ch res)
    (CloseCF (WFlush ch (ChunkedStream. (.stream xdata))) kalive)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- filePutter ""

  [^File vdir ^Channel ch info ^String fname ^XData xdata]

  (try
    (SaveFile vdir fname xdata)
    (ReplyXXX ch 200)
    (catch Throwable e#
      (ReplyXXX ch 500))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileGetter ""

  [^File vdir ^Channel ch info ^String fname]

  (let [ xdata (GetFile vdir fname) ]
    (if (.hasContent xdata)
      (replyGetVFile ch info xdata)
      (ReplyXXX ch 204))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fileHandler ""

  [^ChannelHandlerContext ctx msg options]

  (let [ ^XData xs (:payload msg)
         ch (.channel ctx)
         vdir (:vdir options)
         info (:info msg)
         ^String uri (:uri info)
         mtd (:method info)
         pos (.lastIndexOf uri (int \/))
         p (if (< pos 0) uri (.substring uri (inc pos))) ]
    (log/debug "Method = " mtd ", Uri = " uri ", File = " p)
    (cond
      (or (= mtd "POST")(= mtd "PUT")) (filePutter vdir ch info p xs)
      (or (= mtd "GET")(= mtd "HEAD")) (fileGetter vdir ch info p)
      :else (ReplyXXX ch 405))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- filerInitor ""

  [^ChannelPipeline pipe options]

  (-> pipe (AddEnableSvrSSL options)
           (AddExpect100 options)
           (AddServerCodec options)
           (AddAuxDecoder options)
           (AddWriteChunker options))
   (.addLast pipe "filer" (NettyInboundHandler fileHandler options))
   (AddExceptionCatcher pipe options)
  pipe)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; make a In memory File Server
;;
(defn MakeMemFileServer "A file server which can get/put files."

  ;; returns netty objects if you want to do clean up
  [^String host port options]

  (let []
    (StartNetty host port (BootstrapNetty filerInitor options))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private filesvr-eof nil)

