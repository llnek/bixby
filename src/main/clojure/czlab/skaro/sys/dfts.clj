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
    [czlab.xlib.resources :refer [rstr]]
    [czlab.xlib.str :refer [hgl?]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.core
     :refer [test-cond
             seqint2
             inst?
             juid
             trap!
             muble<>
             test-nestr]]
    [czlab.xlib.files
     :refer [fileRead?
             dirReadWrite?]])

  (:use [czlab.skaro.sys.core]
        [czlab.wflow.core])

  (:import
    [czlab.skaro.io HttpEvent HttpResult]
    [czlab.wflow WorkStream Job StepError]
    [czlab.xlib
     Versioned
     Muble
     I18N
     CU
     Hierarchial
     Identifiable]
    [czlab.skaro.server
     ConfigError
     AppGist
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
  [^String app conf urlToApp]
  {:pre [(map? conf)]}

  (let [pid (juid)
        info
        (merge {:version "1.0"
                :name app
                :main ""}
               (:info conf)
               {:path urlToApp})
        impl (muble<> info)]
    (log/info "pod-meta:\n%s" (.impl impl))
    (with-meta
      (reify
        AppGist
        (id [_] (format "%s{%s}" (:name info) pid))
        (version [_] (:version info))
        (getx [_] impl))
      {:typeid  ::AppGist})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Internal flows
(defn- mkWork

  ""
  ^WorkStream
  [s]

  (workStream<>
    (script<>
      (fn [_ ^Job job]
        (let [^HttpEvent evt (.event job)
              ^HttpResult
              res (.resultObj evt) ]
          (.setStatus res s)
          (.replyResult evt)
          nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkInternalFlow

  ""
  ^WorkStream
  [^Job j s]

  (let [evt (.event j)]
    (if (inst? HttpEvent evt)
      (mkWork s)
      (workStream<>
        (script<>
          #(log/error "Unhandled event, '%s' {job:#s}"
                      (:typeid (meta evt))
                      (.id ^Job %2)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn fatalErrorFlow<>

  ^WorkStream
  [^Job job]

  (mkInternalFlow job 500))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn orphanFlow<>

  ""
  ^WorkStream
  [^Job job]

  (mkInternalFlow job 501))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


