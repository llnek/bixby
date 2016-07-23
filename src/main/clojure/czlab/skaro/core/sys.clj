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

  czlab.skaro.core.sys

  (:require
    [czlab.xlib.files :refer [changeContent readFile readUrl]]
    [czlab.xlib.core :refer [mubleObj! fpath]]
    [czlab.xlib.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io])

  (:use [czlab.skaro.core.consts])

  (:import
    [org.apache.commons.io FilenameUtils FileUtils]
    [czlab.skaro.server Context]
    [czlab.xlib
     Hierarchial
     Muble
     Identifiable
     Versioned]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti comp->contextualize
  "Contextualize a component" (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti comp->compose
  "Compose a component" (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti comp->configure
  "Configure a component" (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti comp->initialize
  "Init a component" (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn comp->synthesize

  "Synthesize a component
   Note the ordering
   1. contextualize
   2. compose
   3. configure
   4. initialize"

  ^Muble
  [co options]

  (let [{:keys [props rego ctx]} options]
    (comp->contextualize co ctx)
    (comp->compose co rego)
    (comp->configure co props)
    (comp->initialize co)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn context<+>
  "Create a context object"
  ^Muble
  []
  (mubleObj!))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn readConf

  "Parse a edn configuration file"
  ^String
  [^File appDir ^String confile]

  (let [rc (-> (io/file appDir DN_CONF confile)
               (changeContent
                 #(cs/replace %
                              "${appdir}"
                              (fpath appDir)))) ]
    (log/debug "[%s]\n%s" confile rc)
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn cloneContext

  "Shallow copy"
  ^Muble
  [^Context co ^Muble ctx]

  (let [x (context<>) ]
    (doseq [[k v] (some-> ctx (.seq))]
      (.setv x k v))
    (.setx co x)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->contextualize :default [co arg] (cloneContext co arg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->configure :default [co props] co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize :default [co] co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->compose :default [co rego] co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


