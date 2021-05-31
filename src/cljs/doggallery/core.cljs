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

(defn single-image-big [photo]
  [:section.section>div.container>div.content
   {:style "max-width:600px"}
   [:div {:class "card"}
    [:div {:class "card-image"}
     [:div {:class "image is-4by3"}
      [:img {:src (str "/api/photos/" (:name photo) "/image")}]]]
    [:div {:class "card-footer"}
     [:a {:href "#" :class "card-footer-item"} "Older"]
     [:a {:href "#" :class "card-footer-item"} "Memories"]
     [:a {:href "#" :class "card-footer-item"} "Newer"]]]])

(defn single-image-thumbnail [photo]
  [:div {:class "column is-one-quarter-desktop is-one-half-tablet"}
   [:div.card
    [:div.card-image
     [:figure {:class "image is-3by2"}
      [:a {:href (str "/photo/" (:name photo))}
       [:img {:src (str "/api/photos/" (:name photo) "/thumbnail")}]]]
     [:div.card-content (:taken photo)]]]])

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
                 [nav-link "#/" "Home" :home]
                 [nav-link "#/about" "About" :about]]]]))

(defn about-page []
  [:section.section>div.container>div.content
   [:img {:src "/img/warning_clojure.png"}]
   [:div
    [:a {:href "https://github.com/vigevenoj/dog-photo-gallery"}
     [:p "Source code at github.com/vigevenoj/dog-photo-gallery"]]]])

(defn home-page []
  [:section.section>div.container>div.content
   (when-let [docs @(rf/subscribe [:docs])]
     [:div {:dangerouslySetInnerHTML {:__html (md->html docs)}}])])

(defn page []
  (if-let [page @(rf/subscribe [:common/page])]
    [:div
     [navbar]
     [page]]))

(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))

(def router
  (reitit/router
    [["/" {:name        :home
           :view        #'home-page
           :controllers [{:start (fn [_] (rf/dispatch [:page/init-home]))}]}]
     ["/recent" {:name :recent}]
     ["/photo/:photo-uuid" {:name :single-photo}]
     ["/memories/:month/:day" {:name :memories}]
     ["/about" {:name :about
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
