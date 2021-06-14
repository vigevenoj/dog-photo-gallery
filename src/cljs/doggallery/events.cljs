(ns doggallery.events
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [reitit.frontend.easy :as rfe]
    [reitit.frontend.controllers :as rfc]))

;;dispatchers

(rf/reg-event-db
  :common/navigate
  (fn [db [_ match]]
    (let [old-match (:common/route db)
          new-match (assoc match :controllers
                                 (rfc/apply-controllers (:controllers old-match) match))]
      (assoc db :common/route new-match))))

(rf/reg-fx
  :common/navigate-fx!
  (fn [[k & [params query]]]
    (rfe/push-state k params query)))

(rf/reg-event-fx
  :common/navigate!
  (fn [_ [_ url-key params query]]
    {:common/navigate-fx! [url-key params query]}))

(rf/reg-event-db
  :set-photo
  (fn [db [_ response]]
    (merge db {:current-photo response})))

(rf/reg-event-db
  :photo-fetch-failure
  (fn [_ [_ response]]
    (js/console.log "Failed to fetch: " response)))

(rf/reg-event-db
  :set-recent-photos
  (fn [db [_ response]]
    (merge db {:photo-list (:photos response)})))

(rf/reg-event-fx
  :page/fetch-single-photo
  (fn [{db :db} [_ photo-uuid]]
    {:http-xhrio {:method :get
                  :uri (str "/api/photos/" photo-uuid)
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:set-photo]
                  :on-failure [:photo-fetch-failure]}}))

(rf/reg-event-fx
  :page/fetch-recent-photos
  (fn [{db :db} _]
    (js/console.log "fetching recent photos")
    {:http-xhrio {:method :get
                  :uri "/api/photos"
                  :format          (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:set-recent-photos]}}))

(rf/reg-event-db
  :common/set-error
  (fn [db [_ error]]
    (assoc db :common/error error)))

; leaving this as a reminder of how to dispatch an event on page load
; if I ever want to do dynamic content on the home page instead of
; a welcome with static words
(rf/reg-event-fx
  :page/init-home
  (fn [_ _]
    (js/console.log "Home page initialized")))
;    {:dispatch [:fetch-docs]}))

;;subscriptions

(rf/reg-sub
  :common/route
  (fn [db _]
    (-> db :common/route)))

(rf/reg-sub
  :common/page-id
  :<- [:common/route]
  (fn [route _]
    (-> route :data :name)))

(rf/reg-sub
  :common/page
  :<- [:common/route]
  (fn [route _]
    (-> route :data :view)))

(rf/reg-sub
  :common/error
  (fn [db _]
    (:common/error db)))

(rf/reg-sub
  :photo-list
  (fn [db _]
    (:photo-list db)))

(rf/reg-sub
  :current-photo
  (fn [db _]
    (:current-photo db)))