;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.core.sys

  (:require [clojure.tools.logging :as log])

  (:use [czlabclj.xlib.util.core :only [Muble MakeMMap NiceFPath]]
        [czlabclj.xlib.util.files :only [ReadOneFile ReadOneUrl]]
        [czlabclj.tardis.core.consts])

  (:import  [org.apache.commons.io FilenameUtils FileUtils]
            [java.io File]
            [org.apache.commons.lang3 StringUtils]
            [com.zotohlab.frwk.core Hierarchial Identifiable Versioned]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; An application must implement this protocol.
;;
(defprotocol CljAppMain

  ""

  (contextualize [_ ctr] )
  (configure [_ options] )
  (initialize [_] )
  (start [_] )
  (stop [_])
  (dispose [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Elmt

  ""

  (setCtx! [_ ctx] )
  (getCtx [_] )
  (setAttr! [_ a v] )
  (clrAttr! [_ a] )
  (toEDN [_])
  (getAttr [_ a] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol Rego

  ""

  (seq* [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^czlabclj.tardis.core.sys.Elmt
          CompContextualize

  ""

  (fn [a ctx] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^czlabclj.tardis.core.sys.Elmt
          CompCompose

  ""

  (fn [a rego] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^czlabclj.tardis.core.sys.Elmt
          CompConfigure

  ""

  (fn [a options] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^czlabclj.tardis.core.sys.Elmt
          CompInitialize

  ""

  (fn [a] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SynthesizeComponent "Note the ordering."

  ^czlabclj.tardis.core.sys.Elmt
  [c options]

  (let [props (:props options)
        rego (:rego options)
        ctx (:ctx options) ]
   (when-not (nil? ctx) (CompContextualize c ctx))
   (when-not (nil? rego) (CompCompose c rego))
   (when-not (nil? props) (CompConfigure c props))
   (CompInitialize c)
   c
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeContext ""

  ^czlabclj.xlib.util.core.Muble
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
(defn ReadConf ""

  ^String
  [^File appDir ^String confile]

  (let [cs (ReadOneFile (File. appDir (str DN_CONF "/" confile)))
        rc (StringUtils/replace cs
                                "${appdir}"
                                (NiceFPath appDir)) ]
    (log/debug "[" confile "]\n" rc)
    rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CompCloneContext "Shallow copy."

  ^czlabclj.tardis.core.sys.Elmt

  [^czlabclj.tardis.core.sys.Elmt co
   ^czlabclj.xlib.util.core.Muble ctx]

  (when-not (nil? ctx)
    (let [x (MakeContext) ]
      (doseq [[k v] (.seq* ctx) ]
        (.setf! x k v))
      (.setCtx! co x)))
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
;;
(def ^:private sys-eof nil)

