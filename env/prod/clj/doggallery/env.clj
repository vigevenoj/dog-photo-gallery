(ns doggallery.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[doggallery started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[doggallery has shut down successfully]=-"))
   :middleware identity})
