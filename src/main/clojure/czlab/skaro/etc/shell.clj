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

  czlab.skaro.etc.shell

  (:gen-class)

  (:require
    [czlab.xlib.util.files :refer [DirRead?]]
    [czlab.xlib.util.logging :as log]
    [clojure.java.io :as io])

  (:use [czlab.xlib.i18n.resources]
        [czlab.skaro.etc.core])

  (:import
    [java.util ResourceBundle Locale]
    [com.zotohlab.frwk.i18n I18N]
    [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private VERPROPS "com/zotohlab/skaro/version.properties")
(def ^:private RCB "czlab.skaro.etc/Resources")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -main "Main Entry"

  [& args]

  (let [ver (LoadResource VERPROPS)
        rcb (GetResource RCB)]
    (with-local-vars [ok false]
      (->> (.getString ver "version")
           (System/setProperty "skaro.version"))
      (I18N/setBase rcb)
      (when-some [home (io/file (first args))]
        (when (DirRead? home)
          (var-set ok true)
          (apply BootAndRun home rcb (drop 1 args))))
      (when-not @ok (Usage)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

