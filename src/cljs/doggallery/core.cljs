(ns doggallery.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.dom :as rdom]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [goog.events :as events]
    [goog.history.EventType :as HistoryEventType]
    [markdown.core :refer [md->html]]
    [doggallery.ajax :as ajax]
    [doggallery.events]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [clojure.string :as string])
  (:import goog.History))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href   uri
    :class (when (= page @(rf/subscribe [:common/page])) :is-active)}
   title])

(defn single-image-view []
  [:section.section>div.container;>div.content
   {:style {:max-width "600px"}}
   (when-let [current-photo @(rf/subscribe [:current-photo])]
     [:div {:class "card"}
      [:div {:class "card-image"}
       [:div {:class "image is-4by3"}
        [:img {:src (str "/api/photos/" (:name current-photo) "/image")}]]]
      [:div {:class "card-footer"}
       [:a {:href (rfe/href ::single-photo {:photo-uuid (:older current-photo)})
            :class "card-footer-item"} "Older"]
       [:a {:href (rfe/href ::memories {:month 1
                                        :day 1})
            :class "card-footer-item"} "Memories"]
       [:a {:href (rfe/href ::single-photo {:photo-uuid (:newer current-photo)})
            :class "card-footer-item"} "Newer"]]])])



(defn single-image-thumbnail [photo]
   [:div {:class "column is-one-quarter-desktop is-one-half-tablet"}
    [:div.card
     [:div.card-image
      [:figure {:class "image is-3by2"}
       [:a {:href (rfe/href ::single-photo {:photo-uuid (:name photo)})}
        [:img {:src (str "/api/photos/" (:name photo) "/thumbnail")}]]]
      [:div.card-content (:taken photo)]]]])

(defn gallery-view []
  [:section.section ;>div.container>div.content
   [:div {:class "columns is-multiline"}
    (when-let [photo-list @(rf/subscribe [:photo-list])]
      (for [photo photo-list]
        ^{:key (:name photo)} [single-image-thumbnail photo]))]])



(defn navbar [] 
  (r/with-let [expanded? ( r/atom false)]
              [:nav.navbar.is-info>div.container
               [:div.navbar-brand
                [:a.navbar-item {:href "/" :style {:font-weight :bold}} "Dog Gallery"]
                [:span.navbar-burger.burger
                 {:data-target :nav-menu
                  :on-click #(swap! expanded? not)
                  :class (when @expanded? :is-active)}
                 [:span][:span][:span]]]
               [:div#nav-menu.navbar-menu
                {:class (when @expanded? :is-active)}
                [:div.navbar-start
                 [nav-link "#/" "Home" ::home]
                 [nav-link "#/recent" "Recent" ::recent]
                 [nav-link "#/about" "About" ::about]]]]))

(defn about-page []
  [:section.section>div.container>div.content
   [:div
    [:p "I don't know about you, but I love seeing pictures of my dogs.
   I like being reminded about what they were up to a year ago, and I'll
   probably want to be reminded about their past antics on any given day of the year."]
    [:p "I couldn't find a photo-sharing site that really met my needs, so I wrote this."]
    [:p "It's a basic photo-displaying site with an API that suits my needs,
    storing photo metadata in PostgreSQL, image data in an
    s3-compatible object storage system, and using imgproxy
    to handle image resizing operations."]
    [:p "Source code available at "
     [:a {:href "https://github.com/vigevenoj/dog-photo-gallery"} "github.com/vigevenoj/dog-photo-gallery"]]]])

(defn home-page []
  [:section.section>div.container>div.content
   [:h1 "Anya and Potato's Photo Gallery"]
   [:p "Check out the recent pictures"]])

(defn page []
  (if-let [page @(rf/subscribe [:common/page])]
    [:div
     [navbar]
     [page]]))

(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))

(def router
  (reitit/router
    [["/" {:name        ::home
           :view        #'home-page
           :controllers [{:start (fn [_] (rf/dispatch [:page/init-home]))}]}]
     ["/recent" {:name ::recent
                 :view #'gallery-view
                 :controllers [{:start (fn [_] (rf/dispatch [:page/fetch-recent-photos]))}]}]
     ["/photo/:photo-uuid" {:name ::single-photo
                            :view #'single-image-view
                            :controllers [{:parameters {:path [:photo-uuid]}
                                           :start (fn [parameters] (do
                                                                     (js/console.log "starting single-photo" (-> parameters :path :photo-uuid))
                                                                     (rf/dispatch [:page/fetch-single-photo (-> parameters :path :photo-uuid)])))}]}]
     ["/memories/:month/:day" {:name ::memories
                               :view #'gallery-view}]
     ["/about" {:name ::about
                :view #'about-page}]]))

(defn start-router! []
  (rfe/start!
    router
    navigate!
    {}))

;; -------------------------
;; Initialize app
(defn mount-components []
  (rf/clear-subscription-cache!)
  (rdom/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (start-router!)
  (ajax/load-interceptors!)
  (mount-components))
