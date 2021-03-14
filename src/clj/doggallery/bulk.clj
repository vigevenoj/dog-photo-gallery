(ns doggallery.bulk
  (:require
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [doggallery.db.core :as db]
    [doggallery.images :as images]))

; todo implement bulk-add functionality:
; * read a directory of files and add images to database
; * read a zip file or tarball and add images from it to the database

; todo handle errors in saving images
(defn save-image
  "Save an image to the database, given an image file and a user ID"
  [image userid]
  (let [meta (images/single-image-full-metadata image)
        photo-id (db/add-dog-photo! {:name (.getName image)
                                     :userid userid
                                     :taken (:date-time-original meta)
                                     :metadata meta
                                     :photo (images/file->bytes image)})]
    photo-id))

; todo handle accumulating success/failure for each file
; todo handle errors reading files (that .DS_Store file did something weird)
; todo handle user ID--do we apply a partial or something
(defn load-directory
  [directory userid]
  (let [image-paths (filter images/is-image-file? (file-seq (io/file directory)))]
    (map save-image image-paths)))

