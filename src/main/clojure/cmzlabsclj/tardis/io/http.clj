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

  cmzlabsclj.tardis.io.http

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:use [cmzlabsclj.util.core :only [MubleAPI notnil? juid TryC spos?
                                           ToJavaInt
                                           MakeMMap test-cond Stringify] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.crypto.ssl])
  (:use [cmzlabsclj.util.str :only [hgl? nsb strim] ])
  (:use [cmzlabsclj.crypto.codec :only [Pwdify] ])
  (:use [cmzlabsclj.util.seqnum :only [NextLong] ])
  (:use [cmzlabsclj.tardis.core.constants])
  (:use [cmzlabsclj.tardis.core.sys])
  (:use [cmzlabsclj.tardis.io.core])
  (:use [cmzlabsclj.tardis.io.webss])
  (:use [cmzlabsclj.tardis.io.triggers])

  (:import (java.util.concurrent ConcurrentHashMap))
  (:import (java.net URL))
  (:import (java.util List Map HashMap ArrayList))
  (:import (java.io File))
  (:import (com.zotohlabs.frwk.util NCMap))
  (:import (javax.servlet.http Cookie HttpServletRequest))
  (:import (java.net HttpCookie))
  (:import (com.google.gson JsonObject))

  (:import (com.zotohlabs.frwk.server Component))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (com.zotohlabs.frwk.core Versioned Hierarchial
                                    Identifiable Disposable Startable))
  (:import (org.apache.commons.codec.binary Base64))

  (:import (com.zotohlabs.gallifrey.io IOSession ServletEmitter Emitter))

  (:import (javax.servlet.http HttpServletRequest HttpServletResponse))

  (:import (com.zotohlabs.gallifrey.io WebSockResult HTTPResult HTTPEvent JettyUtils))
  (:import (com.zotohlabs.gallifrey.core Container)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ScanBasicAuth ""

  [^HTTPEvent evt]

  (if (.hasHeader evt "authorization")
    (let [ s (Stringify (Base64/decodeBase64 (.getHeaderValue evt "authorization")))
           pos (.indexOf s ":") ]
      (if (pos > 0)
        [ (.substring s 0 pos) (.substring s (inc pos)) ]
        []))
    nil
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn HttpBasicConfig ""

  [^cmzlabsclj.tardis.core.sys.Element co cfg]

  (let [ ^String file (:server-key cfg)
         ^String fv (:flavor cfg)
         socto (:soctoutmillis cfg)
         kbs (:threshold-kb cfg)
         w (:wait-millis cfg)
         port (:port cfg)
         bio (:sync cfg)
         tds (:workers cfg)
         pkey (:hhh.pkey cfg)
         ssl (hgl? file)
         json (JsonObject.) ]

    (let [ xxx (if (spos? port) port (if ssl 443 80)) ]
      (.addProperty json "port" (ToJavaInt port))
      (.setAttr! co :port port))

    (let [ xxx (nsb (:host cfg)) ]
      (.addProperty json "host" xxx)
      (.setAttr! co :host xxx))

    (let [ ^String xxx (if (hgl? fv) fv "TLS") ]
      (.addProperty json "sslType" xxx)
      (.setAttr! co :sslType xxx))

    (when (hgl? file)
      (test-cond "server-key file url" (.startsWith file "file:"))
      (let [ xxx (URL. file) ]
        (.addProperty json "serverKey" (nsb xxx))
        (.setAttr! co :serverKey xxx))
      (let [ xxx (Pwdify ^String (:passwd cfg) pkey) ]
        (.addProperty json "pwd" (nsb xxx))
        (.setAttr! co :pwd xxx)))

    (let [ xxx (if (spos? socto) socto 0) ]
      (.addProperty json "sockTimeOut" (ToJavaInt xxx))
      (.setAttr! co :sockTimeOut xxx))

    (let [ xxx (if (true? bio) false true) ]
      (.addProperty json "async" (true? xxx))
      (.setAttr! co :async xxx))

    (let [ xxx (if (spos? tds) tds 6) ]
      (.addProperty json "workers" (ToJavaInt xxx))
      (.setAttr! co :workers xxx))

    (let [ xxx (if (spos? kbs) kbs (* 1024 1024 8)) ]
      (.addProperty json "limit" (ToJavaInt xxx))
      (.setAttr! co :limit xxx))

    ;; 5 mins
    (let [ xxx (if (spos? w) w 300000) ]
      (.addProperty json "waitMillis" (ToJavaInt xxx))
      (.setAttr! co :waitMillis xxx))

    (.setAttr! co :emcfg json)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/HTTP

  [co cfg]

  (HttpBasicConfig co cfg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeWSockResult ""

  ^WebSockResult
  [co]

  (let [ impl (MakeMMap) ]
    (.setf! impl :binary false)
    (.setf! impl :data nil)
    (reify

      MubleAPI

      (setf! [_ k v] (.setf! impl k v) )
      (seq* [_] (.seq* impl))
      (getf [_ k] (.getf impl k) )
      (clrf! [_ k] (.clrf! impl k) )
      (clear! [_] (.clear! impl))

      WebSockResult
      (isBinary [_] (true? (.getf impl :binary)))
      (isText [this] (not (.isBinary this)))
      (getData [_] (XData. (.getf impl :data)))
      (emitter [_] co)

  )))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeHttpResult ""

  ^HTTPResult
  [co]

  (let [ impl (MakeMMap) ]
    (.setf! impl :cookies (ArrayList.))
    (.setf! impl :code -1)
    (.setf! impl :hds (NCMap.))
    (.setf! impl :version "HTTP/1.1" )
    (reify

      MubleAPI

      (setf! [_ k v] (.setf! impl k v) )
      (seq* [_] (.seq* impl))
      (getf [_ k] (.getf impl k) )
      (clrf! [_ k] (.clrf! impl k) )
      (clear! [_] (.clear! impl))

      HTTPResult
      (setRedirect [_ url] (.setf! impl :redirect url))

      (setProtocolVersion [_ ver]  (.setf! impl :version ver))
      (setStatus [_ code] (.setf! impl :code code))
      (getStatus [_] (.getf impl :code))
      (emitter [_] co)
      (addCookie [_ c]
        (let [ a (.getf impl :cookies) ]
          (when-not (nil? c)
            (.add ^List a c))))

      (containsHeader [_ nm]
        (let [ m (.getf impl :hds) ]
          (.containsKey ^Map m nm)))

      (removeHeader [_ nm]
        (let [ m (.getf impl :hds) ]
          (.remove ^Map m nm)))

      (clearHeaders [_]
        (let [ m (.getf impl :hds) ]
          (.clear ^Map m)))

      (addHeader [_ nm v]
        (let [ ^Map m (.getf impl :hds)
               ^List a (.get m nm) ]
          (if (nil? a)
            (.put m nm (doto (ArrayList.) (.add v)))
            (.add a v))))

      (setHeader [_ nm v]
        (let [ ^Map m (.getf impl :hds)
               a (ArrayList.) ]
          (.add a v)
          (.put m nm a)))

      (setChunked [_ b] (.setf! impl :chunked b))

      (setContent [_ data]
        (if-not (nil? data)
          (.setf! impl :data data)) )

  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private http-eof nil)

