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
      :author "kenl" }

  czlab.xlib.util.logging

  (:require [clojure.tools.logging :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(defmacro trace "" [& args]
  `(when (log/enabled? :trace) (log/logf :trace ~@args)))

(defmacro debug "" [& args]
  `(when (log/enabled? :debug) (log/logf :debug ~@args)))

(defmacro info "" [& args]
  `(when (log/enabled? :info) (log/logf :info ~@args)))

(defmacro warn "" [& args]
  `(when (log/enabled? :warn) (log/logf :warn ~@args)))

(defmacro error "" [& args]
  `(when (log/enabled? :error) (log/logf :error ~@args)))

(defmacro fatal "" [& args]
  `(when (log/enabled? :fatal) (log/logf :fatal ~@args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

