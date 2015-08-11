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

  czlab.skaro.impl.dfts

  (:require
    [czlab.xlib.util.core
    :refer [NextLong test-cond MubleObj test-nestr]]
    [czlab.xlib.i18n.resources :refer [RStr]]
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.util.str :refer [ToKW]]
    [czlab.xlib.util.files
    :refer [FileRead? DirReadWrite? ]])

  (:use [czlab.skaro.core.consts]
        [czlab.skaro.core.sys])

  (:import
    [com.zotohlab.skaro.core Muble Context ConfigError]
    [com.zotohlab.frwk.core Versioned
    Identifiable Hierarchial]
    [com.zotohlab.skaro.runtime PODMeta]
    [com.zotohlab.skaro.loaders AppClassLoader]
    [com.zotohlab.frwk.util CU]
    [com.zotohlab.frwk.i18n I18N]
    [com.zotohlab.frwk.server Component
    Registry RegistryError ServiceError]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the directory is readable & writable.
(defn ^:no-doc PrecondDir

  "Is this folder read-writeable?"

  [d]

  (test-cond (RStr (I18N/getBase) "dir.no.rw" d)
             (DirReadWrite? d)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the file is readable
(defn ^:no-doc PrecondFile

  "Is this file readable?"

  [f]

  (test-cond (RStr (I18N/getBase) "file.no.r" f)
             (FileRead? f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc MaybeDir

  "true if the key maps to a File"

  ^File
  [^Muble m kn]

  (let [v (.getv m kn) ]
    (condp instance? v
      String (io/file v)
      File v
      (throw (ConfigError. (RStr (I18N/getBase)
                                 "skaro.no.dir" kn))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;a registry is basically a container holding a bunch of components
;;a component itself can be a registry which implies that registeries can
;;be nested
(defn ReifyRegistry

  "Create a generic component registry"

  ^Registry
  [regoType regoId ver parObj]

  {:pre [(keyword? regoType) (keyword? regoId)]}

  (let [impl (MubleObj {:cache {} })
        ctxt (atom (MubleObj)) ]
    (with-meta
      (reify

        Context

        (setx [_ x] (reset! ctxt x))
        (getx [_] @ctxt)

        Muble

        (setv [_ a v] (.setv impl a v) )
        (unsetv [_ a] (.unsetv impl a) )
        (getv [_ a] (.getv impl a) )
        (seq [_] )
        (clear [_] (.clear impl))
        (toEDN [_] (.toEDN impl))

        Hierarchial

        (parent [_] parObj)

        Component

        (version [_] (str ver))
        (id [_] regoId)

        Registry

        (has [_ cid]
          (-> (.getv impl :cache)
              (get cid)
              (some? )))

        (lookup [_ cid]
          (let [cache (.getv impl :cache)
                c (get cache cid) ]
            (if (and (nil? c)
                     (instance? Registry parObj))
              (-> ^Registry parObj (.lookup cid))
              c)))

        (dereg [this c]
          (let [cid (if (nil? c)
                      nil
                      (.id  ^Identifiable c))
                cache (.getv impl :cache) ]
            (when (.has this cid)
              (.setv impl :cache (dissoc cache cid)))))

        (reg [this c]
          (let [cid (if (nil? c)
                      nil
                      (.id  ^Identifiable c))
                cache (.getv impl :cache) ]
            (when (.has this cid)
              (throw (RegistryError. (RStr (I18N/getBase)
                                           "skaro.dup.cmp" cid))))
            (.setv impl :cache (assoc cache cid c))))

        (iter [_]
          (let [cache (.getv impl :cache) ]
            (seq cache))) )

      {:typeid (ToKW "czc.skaro.impl" (name regoType))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PodMeta*

  "Create metadata for an application bundle"

  [app info pathToPOD]

  {:pre [(map? info)]}

  (let [{:keys [disposition version main]
         :or {main "noname"
              version "1.0"}}
        info
        pid (str main "#" (NextLong))
        impl (MubleObj)
        ctxt (atom (MubleObj)) ]

    (log/info (str "pod-meta: app=%s\n"
                   "ver=%s\ntype=%s\n"
                   "key=%s\npath=%s")
              app version
              main disposition pathToPOD )
    (with-meta
      (reify

        Context

        (setx [_ x] (reset! ctxt x))
        (getx [_] @ctxt)

        Muble

        (setv [_ a v] (.setv impl a v) )
        (unsetv [_ a] (.unsetv impl a) )
        (getv [_ a] (.getv impl a) )
        (seq [_])
        (clear [_] (.clear impl))
        (toEDN [_ ] (.toEDN impl))

        Component

        (version [_] (str version))
        (id [_] pid )

        Hierarchial

        (parent [_] nil)

        PODMeta

        (srcUrl [_] pathToPOD)
        (moniker [_] app)
        (appKey [_] disposition)
        (typeof [_] main))

      {:typeid (ToKW "czc.skaro.impl" "PODMeta") })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

