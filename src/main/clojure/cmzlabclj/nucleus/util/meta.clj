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

(ns ^{  :doc "Utility functions for class related or reflection related operations."
        :author "kenl" }

  cmzlabclj.nucleus.util.meta

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])
  (:use [cmzlabclj.nucleus.util.str :only [EqAny? hgl?] ]
        [cmzlabclj.nucleus.util.core :only [test-nonil] ])
  (:import  [java.lang.reflect Member Field Method Modifier]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti IsChild? "Returns true if clazz is subclass of this base class."
  (fn [a b]
    (if (instance? Class b)
      :class
      :object)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IsChild? :class

  [^Class basz ^Class cz]

  (if (or (nil? basz) (nil? cz))
    false
    (.isAssignableFrom basz cz)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IsChild? :object

  [^Class basz ^Object obj]

  (if (or (nil? basz) (nil? obj))
    false
    (IsChild? basz (.getClass obj))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BytesClass "Return the java class for byte[]."

  ^Class
  []

  (Class/forName "[B"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CharsClass "Return the java class for char[]."

  ^Class
  []

  (Class/forName "[C"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsBoolean? "True if class is Boolean."

  [^Class classObj]

  (EqAny? (.getName classObj) ["boolean" "Boolean" "java.lang.Boolean"] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsVoid? "True if class is Void."

  [^Class classObj]

  (EqAny? (.getName classObj) ["void" "Void" "java.lang.Void"] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsChar? "True if class is Char."

  [^Class classObj]

  (EqAny? (.getName classObj) [ "char" "Char" "java.lang.Character" ] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsInt? "True if class is Int."

  [^Class classObj]

  (EqAny? (.getName classObj) [ "int" "Int" "java.lang.Integer" ] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsLong? "True if class is Long."

  [^Class classObj]

  (EqAny? (.getName classObj) [ "long" "Long" "java.lang.Long" ] ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsFloat? "True if class is Float."

  [^Class classObj]

  (EqAny? (.getName classObj) [ "float" "Float" "java.lang.Float" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsDouble? "True if class is Double."

  [^Class classObj]

  (EqAny? (.getName classObj) [ "double" "Double" "java.lang.Double" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsByte? "True if class is Byte."

  [^Class classObj]

  (EqAny? (.getName classObj) [ "byte" "Byte" "java.lang.Byte" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsShort? "True if class is Short."

  [^Class classObj]

  (EqAny? (.getName classObj) [ "short" "Short" "java.lang.Short" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsString? "True if class is String."

  [^Class classObj]

  (EqAny? (.getName classObj) [ "String" "java.lang.String" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsObject? "True if class is Object."

  [^Class classObj]

  (EqAny? (.getName classObj) [ "Object" "java.lang.Object" ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IsBytes? "True if class is byte[]."

  [^Class classObj]

  (= classObj (BytesClass)) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ForName "Load a java class by name."

  (^Class [^String z] (ForName z nil))

  (^Class [^String z ^ClassLoader cl]
          (if (nil? cl)
            (java.lang.Class/forName z)
            (java.lang.Class/forName z true cl))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetCldr "Get the current classloader."

  (^ClassLoader [] (GetCldr nil))

  (^ClassLoader [^ClassLoader cl]
                (if (nil? cl)
                  (.getContextClassLoader (Thread/currentThread))
                  cl)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SetCldr "Set current classloader."

  [^ClassLoader cl]

  (test-nonil "class-loader" cl)
  (.setContextClassLoader (Thread/currentThread) cl))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn LoadClass "Load this class by name."

  (^Class [^String clazzName]
          (LoadClass clazzName nil))

  (^Class [^String clazzName ^ClassLoader cl]
          (if (not (hgl? clazzName))
            nil
            (.loadClass (GetCldr cl) clazzName))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^Object MakeObjArg1 class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod MakeObjArg1 Class

  [^Class cz ^Object arg1]

  (test-nonil "java-class" cz)
  (let [cargs (make-array Object 1)
        ca (make-array Class 1) ]
    (aset #^"[Ljava.lang.Object;" cargs 0 arg1)
    (aset #^"[Ljava.lang.Class;" ca 0 Object)
    (.newInstance (.getDeclaredConstructor cz ca)
                  cargs)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod MakeObjArg1 String

  [^String cz ^Object arg1]

  (MakeObjArg1 (LoadClass cz) arg1))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CtorObj ""

  ^Object
  [^Class cz]

  (test-nonil "java-class" cz)
  (.newInstance (.getDeclaredConstructor cz (make-array Class 0))
                (make-array Object 0)  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeObj "Make an object of this class by calling the default constructor."

  (^Object [^String clazzName]
           (MakeObj clazzName nil))

  (^Object [^String clazzName
            ^ClassLoader cl]
           (if (not (hgl? clazzName))
             nil
             (CtorObj (LoadClass clazzName cl)))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListParents "List all parent classes."

  [^Class javaClass]

  (let [rc (loop [sum (transient [])
                  par javaClass ]
             (if (nil? par)
               (persistent! sum)
               (recur (conj! sum par)
                      (.getSuperclass par)))) ]
    ;; since we always add the original class, we need to ignore it on return
    (if (> (count rc) 1)
      (rest rc)
      [])
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- iterXXX ""

  [^Class cz ^long level getDeclXXX bin ]

  (let [props (getDeclXXX cz) ]
    (reduce (fn [sum ^Member m]
              (let [x (.getModifiers m) ]
                (if (and (> level 0)
                         (or (Modifier/isStatic x)
                             (Modifier/isPrivate x)) )
                  sum
                  (assoc! sum (.getName m) m))))
            bin
            props)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- listMtds ""

  [^Class cz ^long level ]

  (let [par (.getSuperclass cz) ]
    (iterXXX cz
             level
             #(.getDeclaredMethods ^Class %)
             (if (nil? par)
               (transient {})
               (listMtds par (inc level))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- listFlds ""

  [^Class cz ^long level ]

  (let [par (.getSuperclass cz) ]
    (iterXXX cz
             level
             #(.getDeclaredFields ^Class %)
             (if (nil? par)
               (transient {})
               (listFlds par (inc level))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListMethods "List all methods belonging to this class, including inherited ones."

  [^Class javaClass]

  (vals (if (nil? javaClass)
          {}
          (persistent! (listMtds javaClass 0 )))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ListFields "List all fields belonging to this class, including inherited ones."

  [^Class javaClass]

  (vals (if (nil? javaClass)
          {}
          (persistent! (listFlds javaClass 0 )))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private meta-eof nil)

