(ns)


(defn Jsonify ""

  [^Event evt]

  "")


(defn LookupSession ""
  
  [^String reconKey]

  (if-let [ ps (.getSession reconnRego reconKey) ]
    (CoreUtils/syncExec
      ps
      (reify Callable
        (call [_]
          (if (= Session$Status/NOT_CONNECTED (.getStatus ps))
            (doto ps
              (.setStatus (Session$Status/CONNECTING)))
            nil))))
    nil
  ))

(defn LookupPlayer ""
  
  [options]

  (let [ user (:principal options)
         pwd (:credential options) ]
    nil
  ))

