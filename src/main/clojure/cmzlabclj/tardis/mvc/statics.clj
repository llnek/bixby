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

(ns ^{ :doc ""
       :author "kenl" }

  cmzlabclj.tardis.mvc.statics

  (:use [cmzlabclj.nucleus.util.core :only [notnil? spos? ToJavaInt MubleAPI Try! NiceFPath] ])
  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabclj.tardis.io.triggers])
  (:use [cmzlabclj.tardis.io.http :only [HttpBasicConfig] ])
  (:use [cmzlabclj.tardis.io.netty])
  (:use [cmzlabclj.tardis.io.core])
  (:use [cmzlabclj.tardis.core.sys])
  (:use [cmzlabclj.tardis.core.constants])

  (:use [cmzlabclj.tardis.mvc.templates :only [MakeWebAsset] ])
  (:use [cmzlabclj.tardis.mvc.comms])
  (:use [cmzlabclj.nucleus.util.str :only [hgl? nsb strim] ])
  (:use [cmzlabclj.nucleus.util.meta :only [MakeObj] ])
  (:use [cmzlabclj.nucleus.net.routes])

  (:import ( com.zotohlab.wflow FlowPoint Activity Pipeline PipelineDelegate PTask Work))
  (:import (com.zotohlab.wflow.core Job))

  (:import [com.zotohlab.frwk.netty NettyFW])
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (java.util Date))
  (:import (java.io File))
  (:import (com.zotohlab.frwk.io XData))
  (:import (com.google.gson JsonObject))
  (:import (com.zotohlab.gallifrey.io HTTPEvent HTTPResult Emitter))
  (:import (io.netty.channel Channel ChannelFuture)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype StaticAssetHandler [] PipelineDelegate

  (getStartActivity [_  pipe]
    (PTask. (reify Work
              (perform [_ fw job arg]
                (let [ ^HTTPEvent evt (.event job)
                       ^HTTPResult res (.getResultObj evt) ]
                  (HandleStatic (.emitter evt)
                                evt
                                res
                                (.getv job EV_OPTS))
                  nil)))))

  (onStop [_ pipe]
    (log/debug "nothing to be done here, just stop please."))

  (onError [ _ err curPt]
    (log/error "Oops, I got an error!")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private statics-eof nil)

