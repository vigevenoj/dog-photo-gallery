(ns doggallery.images
    (:require
      [clojure.java.io :as io]
      ;[pantomime.mime :refer [mime-type-of]]
      [remworks.exif-reader :as exif])
  (:import java.nio.file.Files))

; These keys are the ones used for basic data persistence
; we use date-time-original for the "taken" field
(def core-exif-keys [:date-time :date-time-original
                     :gps-altitude :gps-date-stamp
                     :gps-latitude :gps-latitude-ref
                     :gps-longitude :gps-longitude-ref])

(def image-file-types '("image/jpeg"))

(defn is-image-file?
  "Return true if this file is an image type we can handle"
  [file]
  (boolean (some #{(Files/probeContentType (.toPath file))} image-file-types)))

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

(defn file->bytes
  "Convert a file to byte array, in order to store it in postgres"
  [file]
  (with-open [xin (io/input-stream file)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))