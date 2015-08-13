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

  czlab.xlib.jmx.names

  (:require
    [czlab.xlib.util.logging :as log]
    [clojure.string :as cs])

  (:import
    [javax.management ObjectName]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ObjectName*

  "paths: [ \"a=b\" \"c=d\" ]
   domain: com.acme
   beanName: mybean"

  ^ObjectName
  [^String domain ^String beanName & [paths]]

  (let [paths (or paths [])
        sb (StringBuilder.)
        cs (seq paths) ]
    (doto sb
      (.append domain)
      (.append ":")
      (.append (cs/join "," cs)))
    (when-not (empty? cs) (.append sb ","))
    (doto sb
      (.append "name=")
      (.append beanName))
    (ObjectName. (.toString sb))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

