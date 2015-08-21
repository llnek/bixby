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

  czlab.skaro.mvc.ftlshim

  (:require
    [czlab.xlib.util.core :refer [ThrowBadArg]]
    [clojure.walk :as cw :refer [postwalk]]
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io])

  (:import
    [freemarker.template TemplateMethodModelEx
    TemplateBooleanModel
    TemplateCollectionModel
    TemplateDateModel
    TemplateHashModelEx
    TemplateNumberModel
    TemplateScalarModel
    TemplateSequenceModel
    TemplateMethodModel
    Configuration DefaultObjectWrapper]
    [java.io File Writer StringWriter]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol FtlCljAPI (ftl->clj [obj] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(extend-protocol FtlCljAPI

  TemplateBooleanModel
  (ftl->clj [obj]
    (.getAsBoolean obj))

  TemplateCollectionModel
  (ftl->clj [obj]
    (when-some [itr (.iterator obj)]
      (loop [acc []]
        (if (.hasNext itr)
          (recur (conj acc (ftl->clj (.next itr))))
          acc))))

  TemplateDateModel
  (ftl->clj [obj]
    (.getAsDate obj))

  TemplateHashModelEx
  (ftl->clj [obj]
    (zipmap (ftl->clj (.keys obj))
            (ftl->clj (.values obj))))

  TemplateNumberModel
  (ftl->clj [obj]
    (.getAsNumber obj))

  TemplateScalarModel
  (ftl->clj [obj]
    (.getAsString obj))

  TemplateSequenceModel
  (ftl->clj [obj]
    (for [i (range (.size obj))]
      (ftl->clj (.get obj i))))

  Object
  (ftl->clj [obj]
    (ThrowBadArg (format "Can't convert %s to clj" (class obj)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fn->method ""

  [func]

  (reify
    TemplateMethodModelEx
    (exec [_ args]
      (apply func (map ftl->clj args)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- strkey  ""

  [[k v]]

  (if (keyword? k)
    [(.replace (name k) "-" "_") v]
    [k v]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- map->model

  "Stringifies keys replacing hyphens with underscores,
   and replaces functions with template methods"

  [m]

  (cw/postwalk (fn [x] (cond (map? x) (into {} (map strkey x))
                          (fn? x) (fn->method x)
                          :else x))
            m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GenFtlConfig ""

  [ & {:keys [root shared] :or {shared {}}} ]

  (let [cfg (Configuration.)]
    (when (.exists ^File root)
      (log/info "freemarker template source: %s" root)
      (doto cfg
        (.setDirectoryForTemplateLoading root)
        (.setObjectWrapper (DefaultObjectWrapper.)))
      (doseq [[k v] (map->model shared)]
        (.setSharedVariable cfg ^String k v)))
    cfg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RenderFtl

  "Renders a template given by Configuration cfg and a path
   using model as input and writes it to out
   If out is not provided, returns a string
   If translate-model? is true, map->model is run on the model"

  (^String [^Configuration cfg path model]
    (RenderFtl cfg (StringWriter.) path model))

  (^String [^Configuration cfg ^Writer out
   ^String path model & {:keys [translate-model?]
                         :or {translate-model? true}}]
    (when-some [tpl (.getTemplate cfg path)]
      (.process tpl
                (if translate-model? (map->model model) model) out))
    (str out)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

