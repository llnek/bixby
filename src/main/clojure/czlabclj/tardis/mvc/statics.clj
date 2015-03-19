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

  czlabclj.tardis.mvc.statics

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.core
         :only 
         [notnil? spos? ToJavaInt MubleAPI Try! NiceFPath]]
        [czlabclj.tardis.io.triggers]
        [czlabclj.tardis.io.http :only [HttpBasicConfig]]
        [czlabclj.tardis.io.netty]
        [czlabclj.tardis.io.core]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.core.wfs]
        [czlabclj.tardis.core.constants]
        [czlabclj.tardis.mvc.templates :only [MakeWebAsset]]
        [czlabclj.tardis.mvc.comms]
        [czlabclj.xlib.util.str :only [hgl? nsb strim]]
        [czlabclj.xlib.util.meta :only [MakeObj]]
        [czlabclj.xlib.net.routes])

  (:import  [com.zotohlab.gallifrey.io HTTPEvent HTTPResult Emitter]
            [com.zotohlab.wflow FlowNode Activity Pipeline
             PipelineDelegate PTask Work]
            [com.zotohlab.wflow.core Job]
            [com.zotohlab.frwk.netty NettyFW]
            [org.apache.commons.lang3 StringUtils]
            [java.util Date]
            [java.io File]
            [com.zotohlab.frwk.io XData]
            [com.google.gson JsonObject]
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

