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
      :author "Kenneth Leung" }

  czlab.skaro.impl.dfts

  (:require
    [czlab.xlib.core
     :refer [test-cond
             seqint2
             trap!
             muble<>
             test-nestr]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.str :refer [hgl?]]
    [czlab.xlib.files
     :refer [fileRead? dirReadWrite?]])

  (:use [czlab.skaro.core.consts]
        [czlab.skaro.core.sys])

  (:import
    [czlab.xlib
     Versioned
     Muble
     I18N
     CU
     Hierarchial
     Identifiable]
    [czlab.skaro.server
     Component
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

  "Assert folder(s) are read-writeable?"
  [f & dirs]

  (doseq [d (cons f dirs)]
    (test-cond (rstr (I18N/getBase)
                     "dir.no.rw" d)
               (dirReadWrite? d))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the file is readable
(defn ^:no-doc precondFile

  "Assert file(s) are readable?"

  [ff & files]

  (doseq [f (cons ff files)]
    (test-cond (rstr (I18N/getBase)
                     "file.no.r" f)
               (fileRead? f))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc maybeDir

  "true if the key maps to a File"
  ^File
  [^Muble m kn]

  (let [v (.getv m kn)]
    (condp inst? v
      String (io/file v)
      File v
      (trap! ConfigError (rstr (I18N/getBase)
                               "skaro.no.dir" kn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;a registry is basically a container holding a bunch of components
;;a component itself can be a registry which implies that registeries can
;;be nested
(defn registry<>

  "Create a component registry"
  ^Registry
  [regoType regoId ver parObj]
  {:pre [(keyword? regoType) (keyword? regoId)]}

  (let [impl (muble<> {:_pptr_ parObj
                       :cache {}})]
    (with-meta
      (reify

        Hierarchial

        (setParent [_ p] (.setv impl :_pptr p))
        (parent [_] (.getv impl :_pptr_))

        Context

        (getx [_] impl)

        Component

        (version [_] (str ver))
        (id [_] regoId)

        Registry

        (has [_ cid]
          (-> (.getv impl :cache)
              (get cid)
              (some? )))

        (lookup [_ cid]
          (let [c (-> (.getv impl :cache)
                      (get cid))]
            (if (and (nil? c)
                     (inst? Registry parObj))
              (.lookup ^Registry parObj cid)
              c)))

        (dereg [this c]
          (let [cid (if (nil? c)
                      nil
                      (.id  ^Identifiable c))
                cc (.getv impl :cache)]
            (when (.has this cid)
              (when (inst? Hierarchial c)
                (.setParent ^Hierarchial c nil))
              (.setv impl :cache (dissoc cc cid)))))

        (reg [this c]
          (let [cid (if (nil? c)
                      nil
                      (.id  ^Identifiable c))
                cc (.getv impl :cache)]
            (when (.has this cid)
              (trap! RegistryError
                     (rstr (I18N/getBase)
                           "skaro.dup.cmp" cid)))
            (when (inst? Hierarchial c)
              (.setParent ^Hierarchial c this))
            (.setv impl :cache (assoc cc cid c))))

        (iter [_]
          (let [cc (.getv impl :cache)]
            (seq cc))))

      {:typeid regoType})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn podMeta

  "Create metadata for an application bundle"
  [app conf urlToApp]
  {:pre [(map? conf)]}

  (let [pid (juid)
        impl
        (-> (merge {:version "1.0"
                    :path urlToApp
                    :name app
                    :main "noname"} (:info conf))
            (muble<> ))]
    (log/info "pod-meta:\n%s" (.impl impl))
    (with-meta
      (reify
        Component
        (id [_] (format "%s{%s}" pid (.getv impl :name)))
        (version [_] (.getv impl :version))
        (getx [_] impl))
      {:typeid  ::AppGist})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


