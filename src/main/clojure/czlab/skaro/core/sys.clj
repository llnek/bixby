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
    [czlab.xlib.util.core :refer [Muble MakeMMap FPath]]
    [czlab.xlib.util.files
    :refer [ChangeFileContent ReadOneFile ReadOneUrl]])

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs]
    [clojure.java.io :as io])

  (:use [czlab.skaro.core.consts])

  (:import
    [org.apache.commons.io FilenameUtils FileUtils]
    [com.zotohlab.skaro.core Context]
    [java.io File]
    [org.apache.commons.lang3 StringUtils]
    [com.zotohlab.frwk.core Hierarchial Identifiable Versioned]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; An application must implement this protocol.
;;
(defprotocol CljAppMain

  "Main Application API"

  (contextualize [_ ctr] )
  (configure [_ options] )
  (initialize [_] )
  (start [_] )
  (stop [_])
  (dispose [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Rego

  "Registry API"

  (iter* [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti CompContextualize
  "Contextualize a component" (fn [a arg] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti CompCompose
  "Compose a component" (fn [a rego] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti CompConfigure
  "Configure a component" (fn [a options] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti CompInitialize
  "Init a component" (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SynthesizeComponent

  "Synthesize a component
   Note the ordering
   1. contextualize
   2. compose
   3. configure
   4. initialize"

  ^czlab.xlib.util.core.Muble
  [co options]

  (let [{:keys [props rego ctx]} options]
    (when (some? ctx) (CompContextualize co ctx))
    (when (some? rego) (CompCompose co rego))
    (when (some? props) (CompConfigure co props))
    (CompInitialize co)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeContext

  "Create a context object"

  ^czlab.xlib.util.core.Muble
  []

  (let [impl (MakeMMap) ]
    (reify Muble
      (setf! [_ k v] (.setf! impl k v) )
      (seq* [_] (.seq* impl))
      (toEDN [_] (.toEDN impl))
      (getf [_ k] (.getf impl k) )
      (clrf! [_ k] (.clrf! impl k) )
      (clear! [_] (.clear! impl)))
  ))

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
    rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompCloneContext

  "Shallow copy"

  ^czlab.xlib.util.core.Muble

  [^Context co ^czlab.xlib.util.core.Muble ctx]

  (when (some? ctx)
    (let [x (MakeContext) ]
      (doseq [[k v] (.seq* ctx) ]
        (.setf! x k v))
      (.setx co x)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompContextualize :default

  [co arg]

  (CompCloneContext co arg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :default

  [co props]

  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :default

  [co]

  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompCompose :default

  [co rego]

  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

