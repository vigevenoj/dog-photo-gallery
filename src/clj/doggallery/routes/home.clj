(ns doggallery.routes.home
  (:require
   [doggallery.layout :as layout]
   [doggallery.db.core :as db]
   [clj-uuid :as uuid]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [doggallery.middleware :as middleware]
   [java-time :as jt]
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn about-page [request]
  (layout/render request "about.html"))

(defn recent-page [request]
  (let [recent-photos (db/get-recent-photos {:limit 12})]
    (layout/render request "photo-gallery.html" {:photos recent-photos})))

(defn memories-page [request]
  (let [month (-> request :path-params :month)
        day (-> request :path-params :day)
        memories-photos (db/get-previous-years-photos {:day day :month month})]
    (layout/render request "photo-gallery.html" {:photos memories-photos})))

(defn single-photo [request]
  (let [photo-uuid (-> request :path-params :photo-uuid)
        info (db/get-dog-photo-by-uuid {:name (uuid/as-uuid photo-uuid)})]
    (layout/render request "single-photo.html" {:photo-uuid photo-uuid
                                                :memories {:month (jt/as (jt/local-date (:taken info)) :month-of-year)
                                                           :day (jt/as (jt/local-date (:taken info)) :day-of-month)}
                                                :older (:older info)
                                                :newer (:newer info)})))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/photo/:photo-uuid" {:get single-photo
                          :parameters {:photo-uuid uuid?}}]
   ["/recent" {:get recent-page}]
   ["/memories/:month/:day" {:get memories-page
                             :parameters {:month int?
                                          :day int?}}]
   ["/about" {:get about-page}]])
