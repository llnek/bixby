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

  czlabclj.tardis.etc.shell

  (:gen-class)

  (:require [czlabclj.xlib.util.files :refer [DirRead?]])

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io])

  (:use [czlabclj.xlib.i18n.resources]
        [czlabclj.tardis.etc.core])

  (:import  [java.util ResourceBundle Locale]
            [com.zotohlab.frwk.i18n I18N]
            [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private VERPROPS "com/zotohlab/skaro/version.properties")
(def ^:private RCB "czlabclj/tardis/etc/Resources")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -main "Main Entry"

  [& args]

  (with-local-vars [ver (LoadResource VERPROPS)
                    rcb (GetResource RCB)
                    ok false]
    (->> (.getString ^ResourceBundle @ver "version")
         (System/setProperty "skaro.version"))
    (I18N/setBase @rcb)
    (when-not (empty? args)
      (let [home (io/file (first args))]
        (when (DirRead? home)
          ;;(log/info "set SKARO_HOME=" home)
          (var-set ok true)
          (BootAndRun home rcb (drop 1 args)))))
    (when-not @ok (Usage))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

