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

  czlab.skaro.core.sys

  (:require
    [czlab.xlib.util.core :refer [MubleObj FPath]]
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io]
    [czlab.xlib.util.files
    :refer [ChangeFileContent
    ReadOneFile ReadOneUrl]])

  (:use [czlab.skaro.core.consts])

  (:import
    [com.zotohlab.frwk.core Hierarchial Identifiable Versioned]
    [org.apache.commons.io FilenameUtils FileUtils]
    [com.zotohlab.skaro.core Context Muble]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti CompContextualize
  "Contextualize a component" (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti CompCompose
  "Compose a component" (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti CompConfigure
  "Configure a component" (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti CompInitialize
  "Init a component" (fn [a & args] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SynthesizeComponent

  "Synthesize a component
   Note the ordering
   1. contextualize
   2. compose
   3. configure
   4. initialize"

  ^Muble
  [co options]

  (let [{:keys [props rego ctx]} options]
    (CompContextualize co ctx)
    (CompCompose co rego)
    (CompConfigure co props)
    (CompInitialize co)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeContext

  "Create a context object"

  ^Muble
  []

  (MubleObj))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadConf

  "Parse a edn configuration file"

  ^String
  [^File appDir ^String confile]

  (let [rc (-> (io/file appDir DN_CONF confile)
               (ChangeFileContent
                 #(cs/replace %
                              "${appdir}"
                              (FPath appDir)))) ]
    (log/debug "[%s]\n%s" confile rc)
    rc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompCloneContext

  "Shallow copy"

  ^Muble
  [^Context co ^Muble ctx]

  (let [x (MakeContext) ]
    (doseq [[k v] (some-> ctx (.seq))]
      (.setv x k v))
    (.setx co x)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompContextualize :default

  [co arg]

  (CompCloneContext co arg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :default [co props] co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :default [co] co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompCompose :default [co rego] co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

