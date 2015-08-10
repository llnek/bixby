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

  czlab.xlib.net.routes

  (:require
    [czlab.xlib.util.core
        :refer [MubleObj test-cond test-nestr]]
    [czlab.xlib.util.str
        :refer [ToKW strim lcase ucase nsb nichts? hgl?]]
    [czlab.xlib.util.files :refer [ReadOneFile]]
    [czlab.xlib.util.format :refer [ReadEdn]])

  (:require [czlab.xlib.util.logging :as log])

  (:import
    [org.apache.commons.lang3 StringUtils]
    [com.zotohlab.skaro.core Muble]
    [com.google.gson JsonObject]
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

  [route verbs handler]

  (let [impl (MubleObj) ]
    (with-meta
      (reify

        Muble

        (setv [_ k v] (.setv impl k v) )
        (seq [_] (.seq impl))
        (getv [_ k] (.getv impl k) )
        (unsetv [_ k] (.unsetv impl k) )
        (toEDN [_] (.toEDN impl))
        (clear [_] (.clear impl))

        RouteInfo

        (getTemplate [_] (.getv impl :template))
        (isStatic? [_] (.getv impl :static))
        (getHandler [_] handler)
        (getPath [_] route)
        (getVerbs [_] verbs)
        (isSecure? [_] (.getv impl :secure))

        (resemble? [_ mtd path]
          (let [^Pattern rg (.getv impl :regex)
                um (keyword (lcase mtd))
                m (.matcher rg path) ]
            (if (and (.matches m)
                     (or (contains? verbs :all)
                         (contains? verbs um)))
              m
              nil)))

        (collect [_ mc]
          (let [ph (.getv impl :placeHolders)
                ^Matcher mmc mc
                gc (.groupCount mmc) ]
            (with-local-vars
              [rc (transient {})
               r2 "" ]
              (doseq [h (seq ph) ]
                (var-set r2 (last h))
                (var-set rc
                         (assoc! rc
                                 @r2
                                 (nsb (.group mmc ^String @r2)))))
              (persistent! @rc)))) )

      { :typeid (ToKW "czc.net" "RouteInfo") })
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initRoute ""

  [^Muble rc ^String path]

  (let [tknz (StringTokenizer. path "/" true)
        buff (StringBuilder.) ]
    (with-local-vars
      [cg 0 gn ""
       ts ""
       phs (transient []) ]
      (while (.hasMoreTokens tknz)
        (var-set ts (.nextToken tknz))
        (if (= @ts "/")
          (.append buff "/")
          ;else
          (do
            (if (.startsWith ^String @ts ":")
              (do
                (var-set gn (.substring ^String @ts 1))
                (var-set cg (inc @cg))
                (var-set phs (conj! @phs [ @cg @gn ] ))
                (var-set ts  (str "({" @gn "}[^/]+)")))
              ;else
              (let [c (StringUtils/countMatches ^String @ts "(") ]
                (if (> c 0)
                  (var-set cg (+ @cg c)))))
            (.append buff @ts))))
      (let [pp (.toString buff) ]
        (log/info "Route added: %s\nCanonicalized to: %s" path pp)
        (.setv rc :regex (Pattern. pp))
        (.setv rc :path pp))
      (.setv rc :placeHolders (persistent! @phs))
      rc
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkRoute ""

  [stat rt]

  {:pre [(map? rt)]}

  (let
    [uri (strim (get rt :uri ""))
     secure (get rt :secure false)
     tpl (get rt :template "")
     verb (get rt :verb #{})
     mpt (get rt :mount "")
     pipe (get rt :pipe "")
     ^Muble
     rc (make-route-info
          uri
          (if (and stat (empty? verb))
              #{:get}
              verb)
          pipe) ]
    (.setv rc :secure secure)
    (if stat
      (do
        (.setv rc :mountPoint mpt)
        (.setv rc :static true)
        (test-nestr "static-route mount point" mpt))
      ;else
      (do
        (test-cond "http method for route" (not-empty verb))
        (test-nestr "pipeline for route" pipe)))
    (when (hgl? tpl)
      (.setv rc :template tpl))
    (initRoute rc uri)
    rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn LoadRoutes

  "Path can be /host.com/abc/:id1/gg/:id2"

  [^File file]

  (let [stat (-> file
                 (.getName)
                 (.startsWith "static-"))
        s (str "[ " (ReadOneFile file) " ]")
        rs (ReadEdn s) ]
    (with-local-vars
      [rc (transient []) ]
      (doseq [s rs]
        (log/debug "route def === %s" s)
        (var-set rc (conj! @rc (mkRoute stat s))))
      (persistent! @rc))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- seek-route


  "[routeinfo, matcher]"

  [mtd uri rts]

  (when (some? rts)
    (some #(let [m (-> ^czlab.xlib.net.routes.RouteInfo
                       %1
                       (.resemble? mtd uri)) ]
             (when (some? m) [%1 m]))
          (seq rts))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeRouteCracker

  "Create a url route cracker,
   returns [true? RouteInfo? Matcher? Redirect?]"

  ^czlab.xlib.net.routes.RouteCracker
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
        (if (and (false? (first rt))
                 (not (.endsWith uri "/"))
                 (seek-route mtd (str uri "/") routes))
          [true nil nil (str uri "/")]
          rt)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

