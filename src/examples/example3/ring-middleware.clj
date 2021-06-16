(ns a2t-clj.ring-middleware
  (:require [a2t-clj.access :as access]
    [a2t-clj.config :as config]
    [a2t-clj.db :as db]
    [a2t-clj.entities :as entities]
    [a2t-clj.exception :as exception]
    [a2t-clj.log :as log]
    [a2t-clj.response :as response]
    [a2t-clj.security :as security]
    [a2t-clj.sharehub :as sharehub]
    [dataframe.mongo :as mgdb]
    [compojure.core :as cc]
    [compojure.handler :as handler]
    ;; [ring.middleware.defaults :refer [wrap-defaults secure-site-defaults]]
    [a2t-clj.rr-logger :refer [wrap-rr-logger]]
    [ring.middleware.conditional :as c :refer [if-url-starts-with]]
    [compojure.route :as route]
    [ring.util.response :refer [redirect file-response]]
    [slingshot.slingshot :refer [try+]]))

(defn update-persisted-config
  "Updates the current persisted-config object with the config data from the
  persistence tier." [config]
  (reset! config/persisted-config config))

(defn init
  "Set up logging, database connections and application configuration." []
  ;; Set up console and file logging.
  (log/logging-init)
  (log/add-console-log-appender)
  (log/add-file-log-appender)
  ;; Set up the global DB object.
  (db/db-setup config/host config/port config/db config/staging-db
    config/temp-db config/archive-db config/user config/pwd)
  (mgdb/mongo-start)
  ;; Set up MongoDB logging.
  ;; Provides no value.. Disable for now.
  (log/add-mongodb-log-appender)
  ;; Set up config object.
  (update-persisted-config (db/get-map "config" {})))

(def entity-list
  "The list of all entities (collections) supported by the application.  Each
  member is a map of :type, :access-map, and :response-map."
  (entities/gen-entities {}))

(def http-methods
  "A list of all possible endpoints that could potentially be applied to
  entities."
  [{:type :list :method :get :url "" :response response/list-response}
   {:type :create :method :post :url "" :response response/create-response}
   {:type :bulk-update :method :put :url "" :response response/bulk-update-response}
   {:type :head :method :head :url "/:id" :response nil}
   {:type :get :method :get :url "/:id" :response response/get-response}
   {:type :update :method :put :url "/:id" :response response/update-response}
   {:type :delete :method :delete :url "/:id" :response response/delete-response}])

(defn- entity-handler
  "Return a Ring handler (that a route can be created for) for the given entity
  and method (an entry within http-methods)." [entity method]
  ;; TODO: Also read in and use the response map.
  (fn [request]
    (let [current-user (security/get-current-user-object)]
      (if (access/has-access-to-route entity (:type method) current-user)
        ((:response method) entity request current-user)
        (exception/not-authorized)))))

(def app-routes
  "The vector of routes available in this application.  A route will only be
  created if there exists an entry for that entity in its access-map. Returns a
  Ring handler by combining several handlers into one."
  (apply cc/routes
    (flatten
      [(for [entity entity-list
             method http-methods]
         ;; Add routes for each entity/method combo.
         (cc/make-route
           (:method method)
           (str (when config/front-end-bundle "/dist") "/ws/irfoo/v1/"
             (:url entity) (:url method))
           ;; Each route contains a handler.
           (entity-handler entity method)))
       ;; Add sharehub routes
       (cc/GET "/sharehub" [] (sharehub/get-files))
       (cc/GET "/sharehub/:filename" [filename]
         (file-response (str config/sharehub-dir "/" filename)))
       (cc/POST "/sharehub" req (sharehub/upload req)
         (redirect (str (when config/front-end-bundle "/dist")
                     "/index.html#/sharehub")))
       ;; Add default routes.
       (cc/make-route
         :get "/" (fn [req] (redirect
                              (str (when config/front-end-bundle "/dist")
                                "/index.html"))))
       (route/resources "/")
       (route/not-found "Not Found")])))

(defn wrap-error-handling
  "A middleware function that catches errors and returns the error message to
  the front end." [handler]
  (fn [request]
    (try+
      (handler request)
      (catch [:type :web] {:keys [status message]}
        {:status  status
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body    message}))))

(defn wrap-reload-spring
  "A middleware function that reloads the Spring application context on every
  HTTP request." [handler]
  (fn [request]
    (let [application-context (security/application-context request)]
      (handler request))))

(def app                                                    ; Default handler defined in project.clj (:ring > :handler).
  (-> (handler/site app-routes)                             ; Site adds middleware.
    (wrap-reload-spring)
    (wrap-error-handling)
    (if-url-starts-with "/ws" wrap-rr-logger)))
