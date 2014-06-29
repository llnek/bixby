(ns
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeIdGeneratorService ""

  []

  (let []
    (reify UIDGeneratorService
      (generateFor [_ klazz] (str (.getSimpleName ^Class klazz) (NextLong)))
      (generate [_] (str (-> (InetAddress/getLocalHost)
                             (.getHostName))
                         "-"
                         (NextLong))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeLookupService ""

  []

  (let [ roomMap (MakeMMap) ]
    (reify LookupService
      (findRoom [_ ctx] (.getf roomMap (keyword ctx)))
      (findGame [_ ctx] nil)
      (findPlayer [_ ctx] (DefaultPlayer.)))

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private simple-eof nil)

