(ns a2t-clj.rr-logger
  (:require [a2t-clj.config :as config]
    [clojure.string :as str]
    [a2t-clj.dal :as dal])
  (:import (org.bson.types ObjectId)))

;; Specialized Request/Response logger for ring middleware

(defn- get-req-method [request]
  (-> (:request-method request) name clojure.string/upper-case))

(defn- get-req-body! [{:keys [body] :as request}]
  (if (nil? body) "" (slurp body)))

(defn- refill-req-with-body [request body-str]
  (assoc request :body (java.io.ByteArrayInputStream. (.getBytes body-str))))

(defn- get-body [body]
  (if (string? body)
    (if (empty? body)
      (str "[no body]")
      (str body))
    (str (type body))))

(defn- write-to-md [request response req-body]
  (with-open [w (clojure.java.io/writer config/rr-logging-file :append true)]
    (.write w (str "#### METHOD \n * `"
                (str/upper-case (name (:request-method request))) "`\n"
                "#### URI \n * `" (:uri request) "`\n"
                "#### QUERY \n * `" (:query-string request) "`\n"
                "#### BODY \n```json\n" (get-body req-body) "\n```\n"
                "#### RESPONSE \n```json\n" (get-body (:body response))
                "\n```\n\n"))))

(defn- write-to-db [request response req-body]
  (dal/create :rr-log
    "rr-log" {}
    {:method   (str/upper-case (name (:request-method request)))
     :uri      (:uri request)
     :query    (:query-string request)
     :body     (get-body req-body)
     :response (get-body (:body response))}))

(defn wrap-rr-logger [handler]
  (fn [request]
    (let [req-body (get-req-body! request)
          req-orig (if (empty? req-body)
                     request
                     (refill-req-with-body request req-body))
          response (handler req-orig)]
      (if (true? config/rr-logging)
        (if (= "md" config/rr-logging-type)
          (write-to-md request response req-body)
          (write-to-db request response req-body)))
      response)))
