;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.etc.shell

  (:gen-class)

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.i18n.resources :only [GetResource]]
        [czlabclj.xlib.util.files :only [DirRead?]]
        [czlabclj.tardis.etc.core])

  (:import  [java.util ResourceBundle Locale]
            [com.zotohlab.frwk.i18n I18N]
            [java.io File]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn -main "Main Entry"

  [& args]

  (with-local-vars [rcb (GetResource "czlabclj/tardis/etc/Resources"
                                     (Locale/getDefault))
                    ok false]
    (I18N/setBase @rcb)
    (when (> (count args) 0)
      (let [home (File. ^String (first args))]
        (when (DirRead? home)
          ;;(log/info "set SKARO_HOME=" home)
          (var-set ok true)
          (BootAndRun home rcb (drop 1 args)))))
    (when-not @ok (Usage))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private shell-eof nil)

