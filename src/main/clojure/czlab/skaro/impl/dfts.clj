;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.


(ns ^{:doc ""
      :author "kenl" }

  czlab.skaro.impl.dfts

  (:require
    [czlab.xlib.util.core
    :refer [NextLong trap! test-cond MubleObj! test-nestr]]
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

  [f & dirs]

  (doseq [d (cons f dirs)]
    (test-cond (RStr (I18N/getBase) "dir.no.rw" d)
               (DirReadWrite? d))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the file is readable
(defn ^:no-doc PrecondFile

  "Is this file readable?"

  [ff & files]

  (doseq [f (cons ff files)]
    (test-cond (RStr (I18N/getBase) "file.no.r" f)
               (FileRead? f))))

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
      (trap! ConfigError (RStr (I18N/getBase)
                                 "skaro.no.dir" kn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;a registry is basically a container holding a bunch of components
;;a component itself can be a registry which implies that registeries can
;;be nested
(defn ReifyRegistry

  "Create a generic component registry"

  ^Registry
  [regoType regoId ver parObj]

  {:pre [(keyword? regoType) (keyword? regoId)]}

  (let [impl (MubleObj! {:cache {} })
        ctxt (atom (MubleObj!)) ]
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
              (trap! RegistryError (RStr (I18N/getBase)
                                           "skaro.dup.cmp" cid)))
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
        impl (MubleObj!)
        ctxt (atom (MubleObj!)) ]

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

