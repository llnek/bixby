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

  czlab.skaro.sys.dfts

  (:require
    [czlab.xlib.core
     :refer [test-cond
             seqint2
             juid
             trap!
             muble<>
             test-nestr]]
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.str :refer [hgl?]]
    [czlab.xlib.files
     :refer [fileRead? dirReadWrite?]])

  (:use [czlab.skaro.sys.core])

  (:import
    [czlab.skaro.rt AppGist]
    [czlab.xlib
     Versioned
     Muble
     I18N
     CU
     Hierarchial
     Identifiable]
    [czlab.skaro.server
     ConfigError
     Component
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
    (test-cond (rstr (I18N/base)
                     "dir.no.rw" d)
               (dirReadWrite? d))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;asserts that the file is readable
(defn ^:no-doc precondFile

  "Assert file(s) are readable?"

  [ff & files]

  (doseq [f (cons ff files)]
    (test-cond (rstr (I18N/base)
                     "file.no.r" f)
               (fileRead? f))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ^:no-doc maybeDir

  "true if the key maps to a File"
  ^File
  [^Muble m kn]

  (let [v (.getv m kn)]
    (condp instance? v
      String (io/file v)
      File v
      (trap! ConfigError (rstr (I18N/base)
                               "skaro.no.dir" kn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn podMeta

  "Create metadata for an application bundle"
  ^AppGist
  [app conf urlToApp]
  {:pre [(map? conf)]}

  (let [pid (juid)
        info
        (merge {:version "1.0"
                :path urlToApp
                :name app
                :main "noname"} (:info conf))
        impl
        (->> (assoc conf :info info)
             (muble<> ))]
    (log/info "pod-meta:\n%s" (.impl impl))
    (with-meta
      (reify
        AppGist
        (id [_] (format "%s{%s}" pid (:name info)))
        (version [_] (:version info))
        (getx [_] impl))
      {:typeid  ::AppGist})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


