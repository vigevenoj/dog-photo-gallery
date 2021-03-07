(ns doggallery.images
    (:require
      [clojure.java.io :as io]
      [remworks.exif-reader :as exif]))

(defn single-image-full-metadata
      "Return the full map of EXIF metadata from an image"
      [image]
      (exif/from-jpeg (io/as-file image)))

(defn single-image-core-metadata
      "Return date and location from EXIF metadata in an image"
      [image]
      (select-keys
        (exif/from-jpeg (io/as-file image))
        [:date-time :date-time-original
         :gps-altitude :gps-date-stamp
         :gps-latitude :gps-latitude-ref
         :gps-longitude :gps-longitude-ref]))