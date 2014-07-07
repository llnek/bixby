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


(ns ^{ :doc "Locale resources."
       :author "kenl" }

  cmzlabclj.nucleus.i18n.resources

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])

  (:import (java.util PropertyResourceBundle ResourceBundle Locale))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (java.io File FileInputStream))
  (:import (java.net URL))

  (:use [ cmzlabclj.nucleus.util.meta :only [GetCldr] ])
  (:use [ cmzlabclj.nucleus.util.str :only [nsb] ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti LoadResource "Load properties file with localized strings." class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoadResource File

  ^ResourceBundle
  [^File aFile]

  (LoadResource (-> aFile (.toURI) (.toURL))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoadResource URL

  ^ResourceBundle
  [^URL url]

  (with-open [ inp (.openStream url) ]
    (PropertyResourceBundle. inp)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetResource "Return a resource bundle."

  ([^String baseName ^Locale locale] (GetResource baseName locale nil))

  ([^String baseName ^Locale locale ^ClassLoader cl]
    (if (or (nil? baseName)(nil? locale))
      nil
      (ResourceBundle/getBundle baseName locale (GetCldr cl))) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn GetString "Return the string value for this key, pms may contain values for positional substitutions."

  (^String [^ResourceBundle bundle ^String pkey] (GetString bundle pkey []))

  (^String [^ResourceBundle bundle ^String pkey pms]
    (let [ kv (nsb (.getString bundle pkey)) ]
      (if (empty? pms)
        kv
        (loop [ src kv pos 0 ]
          (if (>= pos (count pms))
            src
            (recur (StringUtils/replace src "{}" (nsb (nth pms pos)) 1)
                   (inc pos))))))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private resources-eof nil)

