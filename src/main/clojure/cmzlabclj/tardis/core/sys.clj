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

  cmzlabclj.tardis.core.sys

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core :only [MubleAPI MakeMMap NiceFPath] ]
        [cmzlabclj.nucleus.util.files :only [ReadOneFile ReadOneUrl] ]
        [cmzlabclj.tardis.core.constants])

  (:import  [org.apache.commons.io FilenameUtils FileUtils]
            [java.io File]
            [org.apache.commons.lang3 StringUtils]
            [com.zotohlab.frwk.core Hierarchial Identifiable Versioned]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Element

  ""

  (setCtx! [_ ctx] )
  (getCtx [_] )
  (setAttr! [_ a v] )
  (clrAttr! [_ a] )
  (toEDN [_])
  (getAttr [_ a] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Registry

  ""

  (seq* [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^cmzlabclj.tardis.core.sys.Element
          CompContextualize

  ""

  (fn [a ctx] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^cmzlabclj.tardis.core.sys.Element
          CompCompose

  ""

  (fn [a rego] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^cmzlabclj.tardis.core.sys.Element
          CompConfigure

  ""

  (fn [a options] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^cmzlabclj.tardis.core.sys.Element
          CompInitialize

  ""

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SynthesizeComponent ""

  ^cmzlabclj.tardis.core.sys.Element
  [c options]

  (let [rego (:rego options)
        ctx (:ctx options)
        props (:props options) ]
   (when-not (nil? rego) (CompCompose c rego))
   (when-not (nil? ctx) (CompContextualize c ctx))
   (when-not (nil? props) (CompConfigure c props))
   (CompInitialize c)
   c
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeContext ""

  ^cmzlabclj.nucleus.util.core.MubleAPI
  []

  (let [impl (MakeMMap) ]
    (reify MubleAPI
      (setf! [_ k v] (.setf! impl k v) )
      (seq* [_] (.seq* impl))
      (getf [_ k] (.getf impl k) )
      (clrf! [_ k] (.clrf! impl k) )
      (clear! [_] (.clear! impl)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ReadConf ""

  ^String
  [^File appDir ^String confile]

  (let [cfgDir (File. appDir ^String DN_CONF)
        cs (ReadOneFile (File. cfgDir confile))
        rc (StringUtils/replace cs "${appdir}" (NiceFPath appDir)) ]
    (log/debug "[" confile "]\n" rc)
    rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompCloneContext

  [^cmzlabclj.tardis.core.sys.Element
   co
   ^cmzlabclj.nucleus.util.core.MubleAPI
   ctx]

  (when-not (nil? ctx)
    (let [x (MakeContext) ]
      (doseq [[k v] (.seq* ctx) ]
        (.setf! x k v))
      (.setCtx! co x)))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompContextualize :default

  [co ctx]

  (CompCloneContext co ctx))

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
;;
(def ^:private sys-eof nil)

