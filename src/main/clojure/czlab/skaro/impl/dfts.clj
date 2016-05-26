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
    [czlab.xlib.core
     :refer [nextLong
             trap!
             test-cond
             mubleObj!
             test-nestr]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.str :refer [toKW]]
    [czlab.xlib.files
     :refer [fileRead? dirReadWrite?]])

  (:use [czlab.skaro.core.consts]
        [czlab.skaro.core.sys])

  (:import
    [czlab.skaro.loaders AppClassLoader]
    [czlab.xlib Muble
     Versioned
     I18N CU
     Identifiable Hierarchial]
    [czlab.skaro.runtime PODMeta]
    [czlab.skaro.server Component
     Context
     ConfigError
     Registry
     RegistryError
     ServiceError]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the directory is readable & writable.
(defn ^:no-doc precondDir

  "Is this folder read-writeable?"

  [f & dirs]

  (doseq [d (cons f dirs)]
    (test-cond (rstr (I18N/getBase) "dir.no.rw" d)
               (dirReadWrite? d))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the file is readable
(defn ^:no-doc precondFile

  "Is this file readable?"

  [ff & files]

  (doseq [f (cons ff files)]
    (test-cond (rstr (I18N/getBase) "file.no.r" f)
               (fileRead? f))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc maybeDir

  "true if the key maps to a File"

  ^File
  [^Muble m kn]

  (let [v (.getv m kn) ]
    (condp instance? v
      String (io/file v)
      File v
      (trap! ConfigError (rstr (I18N/getBase)
                                 "skaro.no.dir" kn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;a registry is basically a container holding a bunch of components
;;a component itself can be a registry which implies that registeries can
;;be nested
(defn reifyRegistry

  "Create a generic component registry"

  ^Registry
  [regoType regoId ver parObj]

  {:pre [(keyword? regoType) (keyword? regoId)]}

  (let [impl (mubleObj! {:cache {} })
        ctxt (atom (mubleObj!)) ]
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
              (trap! RegistryError (rstr (I18N/getBase)
                                           "skaro.dup.cmp" cid)))
            (.setv impl :cache (assoc cache cid c))))

        (iter [_]
          (let [cache (.getv impl :cache) ]
            (seq cache))) )

      {:typeid (toKW "czc.skaro.impl" (name regoType))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn podMeta

  "Create metadata for an application bundle"

  [app info pathToPOD]

  {:pre [(map? info)]}

  (let [{:keys [disposition version main]
         :or {main "noname"
              version "1.0"}}
        info
        pid (str main "#" (nextLong))
        impl (mubleObj!)
        ctxt (atom (mubleObj!)) ]

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

      {:typeid (toKW "czc.skaro.impl" "PODMeta") })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


