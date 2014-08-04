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

  cmzlabclj.nucleus.net.routes

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])
  (:use [cmzlabclj.nucleus.util.core :only [MubleAPI MakeMMap test-nestr] ]
        [cmzlabclj.nucleus.util.str :only [nsb nichts? hgl?] ]
        [cmzlabclj.nucleus.util.ini :only [ParseInifile] ])
  (:import  [org.apache.commons.lang3 StringUtils]
            [com.google.gson JsonObject]
            [com.zotohlab.frwk.util IWin32Conf]
            [java.io File]
            [jregex Matcher Pattern]
            [java.util StringTokenizer]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol RouteCracker

  ""

  (routable? [_ msgInfo] )
  (hasRoutes? [_])
  (crack [_ msgInfo] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol RouteInfo

  ""

  (getTemplate [_] )
  (getHandler [_] )
  (getPath [_] )
  (isStatic? [_] )
  (isSecure? [_] )
  (getVerbs [_] )
  (resemble? [_ mtd path] )
  (collect [_ matcher] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- make-route-info ""

  [route ^String verb handler]

  (let [verbList (cstr/upper-case verb)
        impl (MakeMMap) ]
    (with-meta
      (reify

        MubleAPI

        (setf! [_ k v] (.setf! impl k v) )
        (seq* [_] (.seq* impl))
        (getf [_ k] (.getf impl k) )
        (clrf! [_ k] (.clrf! impl k) )
        (clear! [_] (.clear! impl))

        RouteInfo

        (getTemplate [_] (.getf impl :template))
        (isStatic? [_] (.getf impl :static))
        (getHandler [_] handler)
        (getPath [_] route)
        (getVerbs [_] verbList)
        (isSecure? [_] (.getf impl :secure))

        (resemble? [_ mtd path]
          (let [^Pattern rg (.getf impl :regex)
                um (cstr/upper-case mtd)
                m (.matcher rg path) ]
            (if (and (.matches m)
                     (or (= "*" verbList)
                         (>= (.indexOf verbList um) 0)))
              m
              nil)))

        (collect [_ mc]
          (let [ph (.getf impl :placeHolders)
                ^Matcher mmc mc
                gc (.groupCount mmc) ]
            (with-local-vars [rc (transient {}) r2 "" ]
              (doseq [h (seq ph) ]
                (var-set r2 (last h))
                (var-set rc
                         (assoc! rc
                                 @r2
                                 (nsb (.group mmc ^String @r2)))))
              (persistent! @rc)))) )

      { :typeid :czc.net/RouteInfo } )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initRoute ""

  [^cmzlabclj.nucleus.util.core.MubleAPI rc
   ^String path]

  (let [tknz (StringTokenizer. path "/" true)
        buff (StringBuilder.) ]
    (with-local-vars [ cg 0 gn "" ts "" phs (transient []) ]
      (while (.hasMoreTokens tknz)
        (var-set ts (.nextToken tknz))
        (if (= @ts "/")
          (.append buff "/")
          (do
            (if (.startsWith ^String @ts ":")
              (do
                (var-set gn (.substring ^String @ts 1))
                (var-set cg (inc @cg))
                (var-set phs (conj! @phs [ @cg @gn ] ))
                (var-set ts  (str "({" @gn "}[^/]+)")))
              (let [c (StringUtils/countMatches @ts "(") ]
                (if (> c 0)
                  (var-set cg (+ @cg c)))))
            (.append buff @ts))))
      (let [pp (.toString buff) ]
        (log/info "route added: " path " \ncanonicalized to: " pp)
        (.setf! rc :regex (Pattern. pp))
        (.setf! rc :path pp))
      (.setf! rc :placeHolders (persistent! @phs))
      rc
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkRoute ""

  [stat path ^IWin32Conf cfile]

  (let [secure (.optString cfile path :secure "")
        tpl (.optString cfile path :template "")
        verb (.optString cfile path :verb "")
        mpt (.optString cfile path :mount "")
        pipe (.optString cfile path :pipe "")
        ^cmzlabclj.nucleus.util.core.MubleAPI
        rc (make-route-info path
                            (if (and stat
                                     (nichts? verb))
                              "GET"
                              verb)
                            pipe) ]
    (.setf! rc :secure (= "true" (cstr/lower-case secure)))
    (if stat
      (do
        (.setf! rc :mountPoint mpt)
        (.setf! rc :static true)
        (test-nestr "static-route mount point" mpt))
      (do
        (test-nestr "http method for route" verb)
        (test-nestr "pipeline for route" pipe)))
    (when (hgl? tpl)
      (.setf! rc :template tpl))
    (initRoute rc path)
    rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; path can be /host.com/abc/:id1/gg/:id2
;;
(defn LoadRoutes ""

  [^File file]

  (let [stat (-> file (.getName)(.startsWith "static-"))
        cf (ParseInifile file) ]
    (with-local-vars [rc (transient []) ]
      (doseq [s (seq (.sectionKeys cf)) ]
        ;;(log/debug "route key === " s)
        (var-set rc (conj! @rc (mkRoute stat s cf))))
      (persistent! @rc)
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; [ri mc] routeinfo matcher
(defn- seek-route ""

  [mtd uri rts]

  (if-not (nil? rts)
    (some (fn [^cmzlabclj.nucleus.net.routes.RouteInfo ri]
            (let [ m (.resemble? ri mtd uri) ]
              (if (nil? m) nil [ri m])))
          (seq rts))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; router cracker
(defn MakeRouteCracker "Create a url route cracker."

  ^cmzlabclj.nucleus.net.routes.RouteCracker
  [routes]

  (reify RouteCracker
    (routable? [this msgInfo] (first (crack this msgInfo)))
    (hasRoutes? [_] (> (count routes) 0))

    (crack [_ msgInfo]
      (let [^String mtd (:method msgInfo)
            ^String uri (:uri msgInfo)
            rc (seek-route mtd uri routes)
            rt (if (nil? rc)
                  [false nil nil ""]
                  [true (first rc)(last rc) ""] ) ]
        (if (and (false? (nth rt 0))
                 (not (.endsWith uri "/"))
                 (seek-route mtd (str uri "/") routes))
          [true nil nil (str uri "/")]
          rt)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private routes-eof nil)

