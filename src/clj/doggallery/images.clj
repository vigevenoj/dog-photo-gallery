(ns doggallery.images
    (:require
      [clojure.java.io :as io]
      [remworks.exif-reader :as exif]))

; These keys are the ones used for basic data persistence
; we use date-time-original for the "taken" field
(def core-exif-keys [:date-time :date-time-original
                     :gps-altitude :gps-date-stamp
                     :gps-latitude :gps-latitude-ref
                     :gps-longitude :gps-longitude-ref])

(defn single-image-full-metadata
      "Return the full map of EXIF metadata from an image"
      [image]
      (exif/from-jpeg (io/as-file image)))

(defn single-image-core-metadata
      "Return date and location from EXIF metadata in an image"
      [image]
      (select-keys
        (exif/from-jpeg (io/as-file image))
        core-exif-keys))

(defn core-keys [metadata]
  (select-keys metadata core-exif-keys))

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))