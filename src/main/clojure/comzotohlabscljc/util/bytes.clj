
(ns ^{ :doc "Utililties for handling byte[] conversions to/from numbers."
      :author "kenl" }
  comzotohlabscljc.util.bytes)

(use '[clojure.tools.logging :only [info warn error debug]])

(import '(java.nio ByteBuffer CharBuffer) )
(import '(java.nio.charset Charset) )
(import '(java.io
  ByteArrayOutputStream ByteArrayInputStream
  DataOutputStream DataInputStream) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti write-bytes "Write this long value out as byte[]." class )

(defn to-bytes "Convert char[] to byte[]."
  ^bytes
  [^chars chArray ^String encoding]
  (.array (.encode (Charset/forName encoding)
                   (CharBuffer/wrap chArray)) ) )

(defn to-chars "Convert byte[] to char[]."
  ^chars
  [^bytes byteArray ^String encoding]
  (.array (.decode (Charset/forName encoding)
                   (ByteBuffer/wrap byteArray)) ) )

(defn read-long "Return a long by scanning the byte[]."
  [^bytes byteArray]
  (.readLong (DataInputStream. (ByteArrayInputStream. byteArray)) ))

(defn read-int "Return an int by scanning the byte[]."
  [^bytes byteArray]
  (.readInt (DataInputStream. (ByteArrayInputStream. byteArray)) ))

(defmethod write-bytes Integer
  ^bytes
  [nnum]
  (with-open [ baos (ByteArrayOutputStream. (int 4096)) ]
    (let [ ds (DataOutputStream. baos ) ]
      (.writeInt ds (int nnum))
      (.flush ds)
      (.toByteArray baos) )))

(defmethod write-bytes Long
  ^bytes
  [nnum]
  (with-open [ baos (ByteArrayOutputStream. (int 4096)) ]
    (let [ ds (DataOutputStream. baos ) ]
      (.writeLong ds ^long nnum)
      (.flush ds)
      (.toByteArray baos) )))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private bytes-eof nil)

