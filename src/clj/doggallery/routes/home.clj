(ns doggallery.routes.home
  (:require
   [doggallery.layout :as layout]
   [doggallery.db.core :as db]
   [clojure.java.io :as io]
   [doggallery.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn recent-page [request]
  (layout/render request "recent.html"))

(defn single-photo [request]
  (layout/render request "single-photo.html"))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/photo-test" {:get single-photo}]
   ["/docs" {:get (fn [_]
                    (-> (response/ok (-> "docs/docs.md" io/resource slurp))
                        (response/header "Content-Type" "text/plain; charset=utf-8")))}]])

