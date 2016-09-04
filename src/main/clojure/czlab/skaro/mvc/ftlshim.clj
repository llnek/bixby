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


(ns ^{:doc "Helpers related to Freemarker."
      :author "Kenneth Leung" }

  czlab.skaro.mvc.ftlshim

  (:require
    [clojure.walk :as cw :refer [postwalk]]
    [czlab.xlib.core :refer [throwBadArg]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io])

  (:import
    [freemarker.template
     TemplateMethodModelEx
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
    (when-some
      [itr (.iterator obj)]
      (loop [acc []]
        (if (.hasNext itr)
          (recur (conj acc
                       (ftl->clj (.next itr))))
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
    (throwBadArg
      "Can't convert %s to clj" (class obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fn->method

  ""
  [func]

  (reify
    TemplateMethodModelEx
    (exec [_ args]
      (apply func (map ftl->clj args)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- strkey

  ""
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

  (cw/postwalk
    (fn [x]
      (cond
        (map? x)
        (into {} (map strkey x))
        (fn? x)
        (fn->method x)
        :else x))
    m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn genFtlConfig

  ""
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
(defn renderFtl

  "Renders a template given by Configuration cfg and a path
   using model as input and writes it to out
   If out is not provided, returns a string
   If translate-model? is true, map->model is run on the model"

  (^String [^Configuration cfg path model]
    (renderFtl cfg (StringWriter.) path model))

  (^String [^Configuration cfg ^Writer out
   ^String path model & {:keys [translate-model?]
                         :or {translate-model? true}}]
    (when-some [tpl (.getTemplate cfg path)]
      (.process tpl
                (if translate-model?
                  (map->model model)
                  model)
                out))
    (str out)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


