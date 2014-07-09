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

  cmzlabclj.tardis.impl.defaults

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core :only [notnil? MubleAPI] ]
        [cmzlabclj.tardis.core.constants]
        [cmzlabclj.tardis.core.sys]
        [cmzlabclj.nucleus.util.files
         :only [FileRead? DirReadWrite? ] ]
        [cmzlabclj.nucleus.util.core
         :only [test-cond MakeMMap test-nestr] ] )

  (:import  [com.zotohlab.frwk.core Versioned Identifiable Hierarchial]
            [com.zotohlab.gallifrey.loaders AppClassLoader]
            [com.zotohlab.frwk.util CoreUtils]
            [com.zotohlab.frwk.server Component ComponentRegistry
                                                RegistryError ServiceError]
            [com.zotohlab.gallifrey.core ConfigError]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;  Asserts that the directory is readable & writable.
;;
(defn PrecondDir ""

  [d]

  (test-cond (str "Directory " d " must be read-writable.")
                  (DirReadWrite? d)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Asserts that the file is readable.
;;
(defn PrecondFile ""

  [f]

  (test-cond (str "File " f " must be readable.")
                  (FileRead? f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeDir ""

  ^File
  [^cmzlabclj.nucleus.util.core.MubleAPI m kn]

  (let [v (.getf m kn) ]
    (condp instance? v
      String (File. ^String v)
      File v
      (throw (ConfigError. (str "No such folder for key: " kn))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Deployer

  ""

  (undeploy [_ app] )
  (deploy [_ src] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol BlockMeta

  ""

  (enabled? [_] )
  (metaUrl [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol PODMeta

  ""

  (typeof [_ ] )
  (moniker [_] )
  (appKey [_ ] )
  (srcUrl [_ ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Kernel "")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; A registry is basically a container holding a bunch of components.
;; A component itself can be a registry which implies that registeries can
;; ne nested.
;;
(defn MakeComponentRegistry ""

  [regoType regoId ver parObj]

  (let [impl (MakeMMap) ]
    (test-cond "registry type" (keyword? regoType))
    (test-cond "registry id" (keyword? regoId))
    (test-nestr "registry version" ver)
    (.setf! impl :cache {} )
    (with-meta
      (reify

        Element

        (setCtx! [_ x] (.setf! impl :ctx x))
        (getCtx [_] (.getf impl :ctx))
        (setAttr! [_ a v] (.setf! impl a v) )
        (clrAttr! [_ a] (.clrf! impl a) )
        (getAttr [_ a] (.getf impl a) )
        (toJson [_] (.toJson impl))

        Hierarchial

        (parent [_] parObj)

        Component

        (version [_] ver)
        (id [_] regoId)

        ComponentRegistry

        (has [this cid]
          (let [cache (.getf impl :cache)
                c (get cache cid) ]
            (notnil? c)))

        (lookup [this cid]
          (let [cache (.getf impl :cache)
                c (get cache cid) ]
            (if (and (nil? c) (instance? ComponentRegistry parObj))
              (.lookup ^ComponentRegistry parObj cid)
              c)) )

        (dereg [this c]
          (let [cid (if (nil? c) nil (.id  ^Identifiable c))
                cache (.getf impl :cache) ]
            (when (.has this cid)
              (.setf! impl :cache (dissoc cache cid)))))

        (reg [this c]
          (let [cid (if (nil? c) nil (.id  ^Identifiable c))
                cache (.getf impl :cache) ]
            (when (.has this cid)
              (throw (RegistryError.  (str "Component \""
                                           cid
                                           "\" already exists" ))))
            (.setf! impl :cache (assoc cache cid c))))

        Registry
          (seq* [_]
            (let [cache (.getf impl :cache) ]
              (seq cache))) )

      { :typeid (keyword (str "czc.tardis.impl/" (name regoType))) }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private defaults-eof nil)

