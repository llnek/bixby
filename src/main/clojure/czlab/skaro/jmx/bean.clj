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

  czlab.skaro.jmx.bean

  (:require
    [czlab.xlib.logging :as log])

  (:use [czlab.xlib.meta]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import
    [java.lang Exception IllegalArgumentException]
    [java.lang.reflect Field Method]
    [czlab.skaro.etc NameParams]
    [java.util Arrays]
    [czlab.xlib Muble]
    [javax.management Attribute AttributeList
     AttributeNotFoundException
     DynamicMBean
     MBeanAttributeInfo
     MBeanException
     MBeanInfo
     MBeanOperationInfo
     MBeanParameterInfo
     ReflectionException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol ^:private BPropInfo

  ""

  (set-setter [_ ^Method m] )
  (set-getter [_ ^Method m] )
  (^Method get-getter [_] )
  (^Method get-setter [_] )
  (^Class get-type [_] )
  (^String get-desc [_] )
  (^String get-name [_] )
  (^boolean is-query? [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol ^:private BFieldInfo

  ""

  (^boolean is-getter? [_] )
  (^boolean is-setter? [_] )
  (^Field get-field [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkBFieldInfo

  ""
  [^Field fld getr? setr?]

  (reify BFieldInfo
    (is-getter? [_] getr?)
    (is-setter? [_] setr?)
    (get-field [_] fld)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkBPropInfo

  ""
  [^String prop ^String descn ^Method getr ^Method setr]

  (let
    [impl (muble<> {:getr getr
                    :setr setr
                    :type nil})]
    (reify BPropInfo
      (get-type [this]
        (if-some [g (get-getter this)]
          (.getReturnType g)
          (let [ps (some-> (get-setter this)
                           (.getParameterTypes ))]
            (if (== 1 (count ps)) (first ps)))))
      (set-setter [_ m] (.setv impl :setr m))
      (set-getter [_ m] (.setv impl :getr m))
      (get-setter [_] (.getv impl :setr))
      (get-getter [_] (.getv impl :getr))
      (get-desc [_] descn)
      (get-name [_] prop)
      (is-query? [this]
        (if-some [g (get-getter this)]
          (and (-> (.getName g)
                   (.startsWith "is"))
               (isBoolean? (get-type this)))
          false)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwUnknownError

  ""
  [attr]

  (trap! AttributeNotFoundException
         (str "Unknown property " attr)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwBeanError

  ""
  [^String msg]

  (trap! MBeanException (exp! Exception msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- assertArgs

  ""
  [mtd ptypes n]

  (if (not= n (count ptypes))
    (throwBadArg (str "\"" mtd "\" needs " n "args."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetPropName

  ""
  ^String
  [^String mn]

  (let
    [pos (cond
           (or (.startsWith mn "get")
               (.startsWith mn "set")) 3
           (.startsWith mn "is") 2
           :else -1) ]
    (if (< pos 0)
      ""
      (str (Character/toLowerCase (.charAt mn pos))
           (.substring mn (+ pos 1))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkParameterInfo

  ""
  [^Method mtd]

  (pcoll!
    (reduce
      #(let [[t n] %2]
         (->> (MBeanParameterInfo.
                (format "p%d" n)
                (.getName ^Class t)
                "")
              (conj! %1)))
      (transient [])
      (partition
        2
        (interleave
          (vec (.getParameterTypes mtd))
          (map inc (range)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- testJmxType

  "ok if primitive types"
  ^Class
  [^Class cz]

  (if (or (isBoolean? cz)
          (isVoid? cz)
          (isObject? cz)
          (isString? cz)
          (isShort? cz)
          (isLong? cz)
          (isInt? cz)
          (isDouble? cz)
          (isFloat? cz)
          (isChar? cz))
    cz))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- testJmxTypes

  "Make sure we are dealing with primitive types"
  [^Class rtype ptypes]

  (if (and (not (empty? ptypes))
           (true? (some #(if (testJmxType %) false true)
                        (seq ptypes))) )
    nil
    (testJmxType rtype)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleProps2

  ""
  [^Method mtd propsBin]

  (let
    [ptypes (.getParameterTypes mtd)
     rtype (.getReturnType mtd)
     mn (.getName mtd)
     pname (maybeGetPropName mn)
     methodInfo (propsBin pname)]
    (cond
      (and (or (.startsWith mn "get")
               (.startsWith mn "is"))
           (empty? ptypes))
      (if (nil? methodInfo)
        (->> (mkBPropInfo pname "" mtd nil)
             (assoc! propsBin pname))
        (do
          (set-getter methodInfo mtd)
          propsBin))

      (and (.startsWith mn "set")
           (== 1 (count ptypes))
      (if (nil? methodInfo)
        (->> (mkBPropInfo pname "" nil mtd)
             (assoc! propsBin pname))
        (do
          (set-setter methodInfo mtd)
          propsBin))

      :else propsBin))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleProps

  ""
  [^Class cz]

  (let
    [rc
     (pcoll!
       (reduce #(handleProps2 %2 %1)
               (transient {}) (.getMethods cz)))
     ba
     (pcoll!
       (reduce
         #(let [[k v] %2]
            (if-some
              [mt (testJmxType (get-type v))]
              (->>
                (MBeanAttributeInfo.
                  (get-name v)
                  (.getName mt)
                  (get-desc v)
                  (some? (get-getter v))
                  (some? (get-setter v))
                  (is-query? v))
                (conj! %1))
              %1))
         (transient []) rc))]
    [ba rc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleFlds

  ""
  [^Class cz]

  (let
    [dcls (.getDeclaredFields cz)
     flds
     (pcoll!
       (reduce
         #(let [^Field f %2
                fnm (.getName f)]
            (if (.isAccessible f)
              (->> (mkBFieldInfo f true true)
                   (assoc! %1 fnm))
              %1))
         (transient {}) dcls))
     rc
     (pcoll!
       (reduce
         #(let [^Field f %2
                t (.getType f)
                fnm (.getName f)]
            (if-not (.isAccessible f)
              (->>
                (MBeanAttributeInfo.
                  fnm
                  (.getName t)
                  (str fnm " attribute")
                  true
                  true
                  (and (.startsWith fnm "is")
                       (isBoolean? t)))
                (conj! %1))
              %1))
         (transient {}) dcls))]
    [rc flds]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleMethods2

  ""
  [^Method m mtds rc]

  (let
    [ptypes (.getParameterTypes m)
     rtype (.getReturnType m)
     mn (.getName m)]
    (if (some? (testJmxTypes rtype
                             ptypes))
      [(assoc! mtds
               (->> ptypes
                    (map #(.getName ^Class %))
                    (into-array String)
                    (NameParams. mn))
               m)
       (->>
         (MBeanOperationInfo.
           mn
           (str mn " operation")
           (->> (mkParameterInfo m)
                (into-array MBeanParameterInfo))
           (.getName rtype)
           MBeanOperationInfo/ACTION_INFO)
         (conj! rc))]
      [mtds rc])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleMethods

  ""
  [^Class cz]

  (log/info "jmx-bean: processing methods for class: %s" cz)
  (loop
    [ms (seq (.getMethods cz))
     mtds (transient {})
     rc (transient [])]
    (if (empty? ms)
      [(pcoll! rc) (pcoll! mtds)]
      (let [[m r]
            (handleMethods2 (first ms) mtds rc)]
        (recur (rest ms)
               m
               r)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn mkJmxBean

  "Make a JMX bean from this object"
  ^DynamicMBean
  [^Object obj]

  (let
    [cz (.getClass obj)
     impl (muble<>)
     ;; we are ignoring props and fields
     propsMap {}
     fldsMap {}
     ps []
     fs []
     ;; just methods
     [ms mtdsMap]
     (handleMethods cz)
     bi (MBeanInfo.
          (.getName cz)
          (str "Information about: " cz)
          (into-array MBeanAttributeInfo (concat ps fs))
          nil
          (into-array MBeanOperationInfo ms)
          nil) ]
    (reify DynamicMBean
      (getAttribute [_ attrName]
        (let [prop (get propsMap attrName)
              fld (get fldsMap attrName) ]
          (cond
            (nil? prop)
            (do
              (when (or (nil? fld)
                        (not (is-getter fld)))
                (throwUnknownError attrName))
              (-> ^Field
                  (get-field fld)
                  (.get obj)))

            (nil? (get-getter prop))
            (throwUnknownError attrName)

            :else
            (-> ^Method
                (get-getter prop)
                (.invoke obj (object-array 0))))))

      (getAttributes [this attrNames]
        (let [rcl (AttributeList.) ]
          (doseq [^String nm (seq attrNames) ]
            (try
              (->> (->> (.getAttribute this nm)
                        (Attribute. nm))
                   (.add rcl))
              (catch Throwable e#
                (log/error e# "")
                (->> (->> (.getMessage e#)
                          (Attribute. nm))
                     (.add rcl)))))
          rcl))

      (getMBeanInfo [_] bi)

      (setAttribute [_ a]
        (let [^Attribute attr a
              v (.getValue attr)
              an (.getName attr)
              prop (propsMap an)
              fld (fldsMap an) ]
          (cond
            (nil? prop)
            (do
              (when (or (nil? fld)
                        (not (is-setter fld)))
                (throwUnknownError an))
              (-> ^Field
                  (get-field fld)
                  (.set  obj v)))

            (nil? (get-setter prop))
            (throwUnknownError an)

            :else
            (-> ^Method
                (get-setter prop)
                (.invoke obj v)))))

      (setAttributes [this attrs]
        (let [rcl (AttributeList. (count attrs)) ]
          (doseq [^Attribute a (seq attrs)
                 :let [nn (.getName a) ]]
            (try
              (.setAttribute this a)
              (.add rcl
                    (Attribute. nn
                                (.getAttribute this nn)))
              (catch Throwable e#
                (log/error e# "")
                (.add rcl
                      (Attribute. nn
                                  (.getMessage e#))))))
          rcl))

      (invoke [_ opName params sig]
        (let [^Method mtd (mtdsMap (NameParams. opName sig)) ]
          (log/debug "JMX-invoking method %s%s%s%s%s"
                     opName
                     "\n(params) "
                     (seq params)
                     "\n(sig) " (seq sig))
          (when (nil? mtd)
            (throwBeanError (str "Unknown operation \"" opName "\"")))
          (try!
            (if (empty? params)
              (.invoke mtd obj (object-array 0))
              (.invoke mtd obj params))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


