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

  czlab.tardis.impl.dfts

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.tardis.core.consts]
        [czlab.tardis.core.sys])

  (:require [czlab.xlib.util.core :refer [notnil? Muble]]
            [czlab.xlib.util.str :refer [ToKW]]
            [czlab.xlib.i18n.resources :refer [RStr]]
            [czlab.xlib.util.files
             :refer
             [FileRead?
              DirReadWrite? ]]
            [czlab.xlib.util.core
             :refer
             [NextLong
              test-cond
              MakeMMap
              test-nestr]])

  (:import  [com.zotohlab.frwk.core Versioned
             Identifiable Hierarchial]
            [com.zotohlab.skaro.loaders AppClassLoader]
            [com.zotohlab.frwk.util CU]
            [com.zotohlab.frwk.i18n I18N]
            [com.zotohlab.frwk.server Component
             ComponentRegistry RegistryError ServiceError]
            [com.zotohlab.skaro.core ConfigError]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;  Asserts that the directory is readable & writable.
;;
(defn ^:no-doc PrecondDir "Is this folder read-writeable?"

  [d]

  (test-cond (RStr (I18N/getBase) "dir.no.rw" [d])
             (DirReadWrite? d)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Asserts that the file is readable.
;;
(defn ^:no-doc PrecondFile "Is this file readable?"

  [f]

  (test-cond (RStr (I18N/getBase) "file.no.r" [f])
             (FileRead? f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc MaybeDir "Tests if the key maps to a File."

  ^File
  [^czlab.xlib.util.core.Muble m kn]

  (let [v (.getf m kn) ]
    (condp instance? v
      String (io/file v)
      File v
      (throw (ConfigError. (RStr (I18N/getBase)
                                 "skaro.no.dir" [kn]))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol EmitMeta

  "Metadata for a Emitter."

  (enabled? [_] )
  (getName [_])
  (metaUrl [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol PODMeta

  "Metadata for an application bundle."

  (typeof [_ ] )
  (moniker [_] )
  (appKey [_ ] )
  (srcUrl [_ ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; A registry is basically a container holding a bunch of components.
;; A component itself can be a registry which implies that registeries can
;; ne nested.
;;
(defn MakeRegistry "Create a generic component registry."

  [regoType regoId ver parObj]

  (let [impl (MakeMMap {:cache {} })]
    (test-cond "registry type" (keyword? regoType))
    (test-cond "registry id" (keyword? regoId))
    (test-nestr "registry version" ver)
    (with-meta
      (reify

        Elmt

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (toEDN [_] (.toEDN impl))

        Hierarchial

        (parent [_] parObj)

        Component

        (version [_] ver)
        (id [_] regoId)

        ComponentRegistry

        (has [this cid]
          (let [cache (.getf impl :cache)
                c (get cache cid) ]
            (some? c)))

        (lookup [this cid]
          (let [cache (.getf impl :cache)
                c (get cache cid) ]
            (if (and (nil? c)
                     (instance? ComponentRegistry parObj))
              (.lookup ^ComponentRegistry parObj cid)
              c)) )

        (dereg [this c]
          (let [cid (if (nil? c)
                      nil
                      (.id  ^Identifiable c))
                cache (.getf impl :cache) ]
            (when (.has this cid)
              (.setf! impl :cache (dissoc cache cid)))))

        (reg [this c]
          (let [cid (if (nil? c)
                      nil
                      (.id  ^Identifiable c))
                cache (.getf impl :cache) ]
            (when (.has this cid)
              (throw (RegistryError. (RStr (I18N/getBase)
                                           "skaro.dup.cmp" [cid]))))
            (.setf! impl :cache (assoc cache cid c))))

        Rego
          (seq* [_]
            (let [cache (.getf impl :cache) ]
              (seq cache))) )

      { :typeid (ToKW "czc.tardis.impl" (name regoType)) }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakePodMeta "Create metadata for an application bundle."

  [app ver podType appid pathToPOD]

  (let [pid (str podType "#" (NextLong))
        impl (MakeMMap) ]
    (log/info "PODMeta: " app ", " ver ", "
              podType ", " appid ", " pathToPOD )
    (with-meta
      (reify

        Elmt

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (toEDN [_ ] (.toEDN impl))

        Component

        (version [_] ver)
        (id [_] pid )

        Hierarchial

        (parent [_] nil)

        PODMeta

        (srcUrl [_] pathToPOD)
        (moniker [_] app)
        (appKey [_] appid)
        (typeof [_] podType))

      { :typeid (ToKW "czc.tardis.impl" "PODMeta") }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

