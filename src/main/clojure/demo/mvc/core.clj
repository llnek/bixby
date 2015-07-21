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

(ns ^:no-doc
    ^{:author "kenl"}

  demo.mvc.core

  (:require [czlabclj.xlib.util.process :refer [DelayExec]]
            [czlabclj.xlib.util.core :refer [notnil?]]
            [czlabclj.xlib.util.str :refer [nsb]])

  (:require [clojure.tools.logging :as log])

  (:import  [com.zotohlab.wflow WHandler Job FlowNode PTask]
            [com.zotohlab.skaro.io HTTPEvent HTTPResult]
            [com.zotohlab.skaro.core Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private ^String FMTHtml
  (str "  <html><head>"
       "<title>Skaro: Test Web</title>"
       "<link rel=\"shortcut icon\" href=\"/public/media/site/favicon.ico\"/>"
       "<link type=\"text/css\" rel=\"stylesheet\" href=\"/public/styles/site/main.css\"/>"
       "<script type=\"text/javascript\" src=\"/public/scripts/site/test.js\"></script>"
       "</head>"
       "<body><h1>Bonjour!</h1><br/>"
       "<button type=\"button\" onclick=\"pop();\">Click Me!</button>"
       "</body></html>"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Demo [] WHandler

  (run [_  j]
    (require 'demo.mvc.core)
    (let [^HTTPEvent ev (.event ^Job j)
          res (.getResultObj ev) ]
      (doto res
        (.setContent FMTHtml)
        (.setStatus 200))
      (.replyResult ev))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

