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

  comzotohlabscljc.tardis.impl.defaults

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [ comzotohlabscljc.util.core :only [MubleAPI] ] )
  (:use [comzotohlabscljc.tardis.core.constants])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [ comzotohlabscljc.util.files :only [FileRead? DirReadWrite? ] ] )
  (:use [ comzotohlabscljc.util.core :only [test-cond MakeMMap test-nestr] ] )

  (:import (com.zotohlabs.frwk.core Versioned Identifiable Hierarchial))
  (:import (com.zotohlabs.gallifrey.loaders AppClassLoader))
  (:import (com.zotohlabs.frwk.util CoreUtils))
  (:import (com.zotohlabs.frwk.server Component ComponentRegistry
                                      RegistryError ServiceError ))
  (:import (com.zotohlabs.gallifrey.core ConfigError))
  (:import (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PrecondDir ""

  [d]

  (test-cond (str "Directory " d " must be read-writable.")
                (DirReadWrite? d)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn PrecondFile ""

  [f]

  (test-cond (str "File " f " must be readable.")
                (FileRead? f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MaybeDir ""

  ^File
  [^comzotohlabscljc.util.core.MubleAPI m kn]

  (let [ v (.getf m kn) ]
    (cond
      (instance? String v)
      (File. ^String v)

      (instance? File v)
      v

      :else
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
;;
(defn MakeComponentRegistry "" 
  
  [regoType regoId ver parObj]

  (let [ impl (MakeMMap) ]
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

        Hierarchial
        (parent [_] parObj)

        Component
        (version [_] ver)
        (id [_] regoId)

        ComponentRegistry
        (has [this cid]
            (let [ cache (.getf impl :cache)
                   c (get cache cid) ]
              (if (nil? c) false true)) )
        (lookup [this cid]
            (let [ cache (.getf impl :cache)
                   c (get cache cid) ]
              (if (and (nil? c) (instance? ComponentRegistry parObj))
                (.lookup ^ComponentRegistry parObj cid)
                c)) )

        (dereg [this c]
          (let [ cid (if (nil? c) nil (.id  ^Identifiable c))
                 cache (.getf impl :cache) ]
            (when (.has this cid)
              (.setf! impl :cache (dissoc cache cid)))))

        (reg [this c]
          (let [ cid (if (nil? c) nil (.id  ^Identifiable c))
                 cache (.getf impl :cache) ]
            (when (.has this cid)
              (throw (RegistryError.
                       (str "Component \"" cid "\" already exists" ))))
            (.setf! impl :cache (assoc cache cid c))))

        Registry
          (seq* [_]
            (let [ cache (.getf impl :cache) ]
              (seq cache))) )

      { :typeid (keyword (str "czc.tardis.impl/" (name regoType))) } 

  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private defaults-eof nil)

