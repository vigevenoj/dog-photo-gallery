(ns doggallery.bulk
  (:require
    [clojure.tools.logging :as log]
    [doggallery.db.core :as db]
    [doggallery.images :as images]))

; todo implement bulk-add functionality:
; * read a directory of files and add images to database
; * read a zip file or tarball and add images from it to the database