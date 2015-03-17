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

  cmzlabclj.tardis.mvc.statics

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core
         :only [notnil? spos? ToJavaInt MubleAPI Try! NiceFPath] ]
        [cmzlabclj.tardis.io.triggers]
        [cmzlabclj.tardis.io.http :only [HttpBasicConfig] ]
        [cmzlabclj.tardis.io.netty]
        [cmzlabclj.tardis.io.core]
        [cmzlabclj.tardis.core.sys]
        [cmzlabclj.tardis.core.wfs]
        [cmzlabclj.tardis.core.constants]
        [cmzlabclj.tardis.mvc.templates :only [MakeWebAsset] ]
        [cmzlabclj.tardis.mvc.comms]
        [cmzlabclj.nucleus.util.str :only [hgl? nsb strim] ]
        [cmzlabclj.nucleus.util.meta :only [MakeObj] ]
        [cmzlabclj.nucleus.net.routes])

  (:import  [com.zotohlab.wflow FlowNode Activity Pipeline
                                PipelineDelegate PTask Work]
            [com.zotohlab.wflow.core Job]
            [com.zotohlab.frwk.netty NettyFW]
            [org.apache.commons.lang3 StringUtils]
            [java.util Date]
            [java.io File]
            [com.zotohlab.frwk.io XData]
            [com.google.gson JsonObject]
            [com.zotohlab.gallifrey.io HTTPEvent HTTPResult Emitter]
            [io.netty.channel Channel ChannelFuture]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype StaticAssetHandler [] PipelineDelegate

  (getStartActivity [_  pipe]
    (DefWFTask
      (fn [cur ^Job job arg]
        (let [^HTTPEvent evt (.event job)
              res (.getResultObj evt) ] 
          (HandleStatic (.emitter evt)
                        evt
                        res
                        (.getv job EV_OPTS)) 
          nil))))

  (onStop [_ pipe]
    (log/debug "Nothing to be done here, just stop please."))

  (onError [ _ err curPt]
    (log/error "Oops, I got an error!")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private statics-eof nil)

