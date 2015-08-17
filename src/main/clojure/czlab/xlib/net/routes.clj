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
    [czlab.xlib.util.str
    :refer [ToKW SplitTokens strim lcase ucase hgl?]]
    [czlab.xlib.util.core
    :refer [MubleObj! test-cond test-nestr]]
    [czlab.xlib.util.files :refer [ReadOneFile]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.util.format :refer [ReadEdn]])

  (:import
    [com.zotohlab.skaro.runtime RouteCracker RouteInfo]
    [org.apache.commons.lang3 StringUtils]
    [com.zotohlab.skaro.core Muble]
    [java.io File]
    [jregex Matcher Pattern]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- routeInfo ""

  ^RouteInfo
  [route verbs handler]

  (let [impl (MubleObj!) ]
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

        (isStatic [_] (true? (.getv impl :static)))
        (isSecure [_] (true? (.getv impl :secure)))
        (getTemplate [_] (.getv impl :template))
        (getHandler [_] handler)
        (getPath [_] route)
        (getVerbs [_] verbs)

        (resemble [_ mtd path]
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
                                 (str (.group mmc ^String @r2)))))
              (persistent! @rc)))) )

      {:typeid (ToKW "czc.net" "RouteInfo") })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- initRoute ""

  ^Muble
  [^Muble rc ^String path]

  (let [buff (StringBuilder.)
        phs (atom [])
        cg (atom 0) ]
    (doseq [^String ts
            (SplitTokens path "/" true)]
      (->>
        (if
          (.startsWith ts ":")
          (let [gn (.substring ts 1)]
            (swap! cg inc)
            (swap! phs conj [ @cg gn ])
            (str "({" gn "}[^/]+)"))
          ;else
          (let [c (StringUtils/countMatches ts "(") ]
            (when (> c 0) (swap! cg + c))
            ts))
        (.append buff)))
    (let [pp (.toString buff) ]
      (log/info "route added: %s\ncanonicalized to: %s" path pp)
      (doto rc
        (.setv :regex (Pattern. pp))
        (.setv :path pp)
        (.setv :placeHolders @phs))
      rc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkRoute ""

  ^RouteInfo
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
     rc (routeInfo
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
    rc))

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
      (persistent! @rc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- seekRoute

  "[routeinfo, matcher]"

  [mtd uri rts]

  (when (some? rts)
    (some #(let [m (-> ^RouteInfo
                       %1
                       (.resemble mtd uri)) ]
             (when (some? m) [%1 m]))
          (seq rts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RouteCracker*

  "Create a url route cracker,
   returns [true? RouteInfo? Matcher? Redirect?]"

  ^RouteCracker
  [routes]

  (reify

    RouteCracker

    (isRoutable [this msgInfo] (first (.crack this msgInfo)))
    (hasRoutes [_] (> (count routes) 0))

    (crack [_ msgInfo]
      (let [^String mtd (:method msgInfo)
            ^String uri (:uri msgInfo)
            rc (seekRoute mtd uri routes)
            rt (if (nil? rc)
                  [false nil nil ""]
                  [true (first rc)(last rc) ""] ) ]
        (if (and (false? (first rt))
                 (not (.endsWith uri "/"))
                 (seekRoute mtd (str uri "/") routes))
          [true nil nil (str uri "/")]
          rt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

