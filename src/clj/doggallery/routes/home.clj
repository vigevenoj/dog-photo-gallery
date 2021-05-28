(ns doggallery.routes.home
  (:require
   [doggallery.layout :as layout]
   [doggallery.db.core :as db]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [doggallery.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn recent-page [request]
  (let [recent-photos (db/get-recent-photos {:limit 12})]
    (layout/render request "recent.html" {:recent-photos recent-photos})))

(defn single-photo [request]
  (let [photo-uuid (-> request :path-params :photo-uuid)]
    (log/warn (type photo-uuid))
    (layout/render request "single-photo.html" {:photo-uuid photo-uuid})))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/photo/:photo-uuid" {:get single-photo}]
   ["/recent" {:get recent-page}]
   ["/docs" {:get (fn [_]
                    (-> (response/ok (-> "docs/docs.md" io/resource slurp))
                        (response/header "Content-Type" "text/plain; charset=utf-8")))}]])

