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
    [czlab.xlib.core
     :refer [trap!
             exp!
             throwBadArg
             muble<> try!]]
    [czlab.xlib.logging :as log]
    [czlab.xlib.str :refer [hasAny?]])

  (:use [czlab.xlib.meta])

  (:import
    [java.lang Exception IllegalArgumentException]
    [czlab.skaro.etc NameParams]
    [java.lang.reflect Field Method]
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

  (get-type [_] )
  (get-desc [_] )
  (get-getter [_] )
  (get-name [_] )
  (get-setter [_] )
  (set-setter [_ m] )
  (set-getter [_ m] )
  (is-query [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol ^:private BFieldInfo

  ""

  (is-getter [_] )
  (is-setter [_] )
  (get-field [_] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkBFieldInfo

  ""
  [^Field fld getr setr]

  (reify BFieldInfo
    (is-getter [_] getr)
    (is-setter [_] setr)
    (get-field [_] fld)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkBPropInfo

  ""
  [^String prop ^String descn ^Method getr ^Method setr]

  (let
    [impl (muble<> {:getr getr
                    :setr setr
                    :type nil}) ]
    (reify BPropInfo
      (get-type [this]
        (let [^Method g (get-getter this)
              ^Method s (get-setter this) ]
          (if (some? g)
            (.getReturnType g)
            (when (some? s)
              (let [ps (.getParameterTypes s) ]
                (when (== 1 (count ps)) (first ps)))))))
      (get-getter [_] (.getv impl :getr))
      (get-name [_] prop)
      (get-desc [_] descn)
      (get-setter [_] (.getv impl :setr))
      (set-setter [_ m] (.setv impl :setr m))
      (set-getter [_ m] (.setv impl :getr m))
      (is-query [this]
        (if-some [^Method g (get-getter this) ]
          (and (-> g (.getName)
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

  (when (not= n (count ptypes))
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

  (with-local-vars
    [ptypes (.getParameterTypes mtd)
     ctr 1
     rc (transient []) ]
    (doseq [^Class t (seq @ptypes) ]
      (var-set rc
               (->> (new MBeanParameterInfo
                         (str "p" @ctr) (.getName t) "")
                    (conj! @rc)))
      (var-set ctr (inc @ctr)))
    (persistent! @rc)))

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
    cz
    nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- testJmxTypes

  "Make sure we are dealing with primitive types"
  [^Class rtype ptypes]

  (if (and (not (empty? ptypes))
           (true? (some #(if (testJmxType %) false true)
                        (seq ptypes))) )
    false
    (testJmxType rtype)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleProps

  ""
  [^Class cz]

  (with-local-vars
    [mtds (.getMethods cz)
     props (transient {})
     ba (transient []) ]
    (doseq [^Method mtd (seq @mtds)
           :let
           [ptypes (.getParameterTypes mtd)
            rtype (.getReturnType mtd)
            mn (.getName mtd)
            pname (maybeGetPropName mn)
            methodInfo (props pname) ] ]
      (cond
        (or (.startsWith mn "get")
            (.startsWith mn "is"))
        (when (empty? ptypes)
          (if (nil? methodInfo)
            (var-set props
                     (->> (mkBPropInfo pname "" mtd nil)
                          (assoc! @props pname)))
            (set-getter methodInfo mtd)))

        (.startsWith mn "set")
        (when (== 1 (count ptypes))
          (if (nil? methodInfo)
            (var-set props
                     (->> (mkBPropInfo pname "" nil mtd)
                          (assoc! @props pname)))
            (set-setter methodInfo mtd)))

        :else nil))
    (let [rc (persistent! @props) ]
      (doseq [[k v] rc]
        (when-some [mt (testJmxType (get-type v)) ]
          (conj! @ba
                 (MBeanAttributeInfo. (get-name v)
                                      (.getName mt)
                                      (get-desc v)
                                      (some? (get-getter v))
                                      (some? (get-setter v))
                                      (is-query v)))))
      [ (persistent! @ba) rc ] )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleFlds

  ""
  [^Class cz]

  (with-local-vars
    [dcls (.getDeclaredFields cz)
     flds (transient {})
     rc (transient []) ]
    (doseq [^Field field (seq @dcls)
           :let
           [fnm (.getName field) ]]
      (when (.isAccessible field)
        (var-set flds
                 (->> (mkBFieldInfo field true true)
                      (assoc! @flds fnm)))
        (var-set rc
                 (->> (MBeanAttributeInfo.
                        fnm
                        (-> field
                            (.getType)
                            (.getName) )
                        (str fnm " attribute")
                        true
                        true
                        (and (.startsWith fnm "is")
                             (isBoolean? (.getType field))))
                      (conj! @rc)))))
    [ (persistent! @rc) (persistent! @flds) ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleMethods

  ""
  [^Class cz]

  (with-local-vars
    [dcls (.getMethods cz)
     metds (transient {})
     rc (transient []) ]
    (log/info "jmx-bean: processing class: %s" cz)
    (doseq [^Method m (seq @dcls)
           :let
           [^Class rtype (.getReturnType m)
            ptypes (.getParameterTypes m)
            mn (.getName m) ]]
      (cond
        (hasAny? mn [ "_QMARK" "_BANG" "_STAR" ])
        nil;;(log/info "JMX-skipping " mn)

        (some? (testJmxTypes rtype ptypes))
        (let [pns (map #(.getName ^Class %) (seq ptypes))
              nameParams (NameParams. mn
                                      (into-array String pns))
              pmInfos (mkParameterInfo m) ]
          (var-set metds (assoc! @metds nameParams m))
          (log/info "jmx-adding method %s" mn)
          (var-set rc
                   (->> (MBeanOperationInfo.
                          mn
                          (str mn " operation")
                          (into-array MBeanParameterInfo pmInfos)
                          (.getName rtype)
                          MBeanOperationInfo/ACTION_INFO )
                        (conj! @rc))))
        :else
        (log/info "JMX-skipping %s" mn) ))
    [ (persistent! @rc) (persistent! @metds) ]))

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


