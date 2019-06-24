(ns spec-tools.transform
  #?(:cljs (:refer-clojure :exclude [Inst Keyword UUID]))
  (:require [clojure.spec.alpha :as s]
            #?@(:cljs [[goog.date.UtcDateTime]
                       [goog.date.Date]])
            [clojure.set :as set]
            [spec-tools.parse :as parse]
            [clojure.string :as str]
            [spec-tools.impl :as impl])
  #?(:clj
     (:import (java.util Date UUID)
              (java.time Instant ZoneId)
              (java.time.format DateTimeFormatter DateTimeFormatterBuilder)
              (java.time.temporal ChronoField))))

;;
;; Keywords
;;

(defn keyword->string [_ x]
  (if (keyword? x)
    (impl/qualified-name x)
    x))

(defn keyword-or-string-> [f]
  (fn [spec x]
    (cond
      (keyword? x) (f spec (keyword->string spec x))
      (string? x) (f spec x)
      :else x)))

(defn keyword-> [f]
  (fn [spec x]
    (cond
      (keyword? x) (f spec (keyword->string spec x))
      :else x)))
;;
;; Strings
;;

(defn string->long [_ x]
  (if (string? x)
    (try
      #?(:clj  (Long/parseLong x)
         :cljs (let [x' (js/parseInt x 10)]
                 (if (js/isNaN x') x x')))
      (catch #?(:clj Exception, :cljs js/Error) _ x))
    x))

(defn string->double [_ x]
  (if (string? x)
    (try
      #?(:clj  (Double/parseDouble x)
         :cljs (let [x' (js/parseFloat x)]
                 (if (js/isNaN x') x x')))
      (catch #?(:clj Exception, :cljs js/Error) _ x))
    x))

(defn string->keyword [_ x]
  (if (string? x)
    (keyword x)
    x))

(defn string->boolean [_ x]
  (if (string? x)
    (cond
      (= "true" x) true
      (= "false" x) false
      :else x)
    x))

(defn string->uuid [_ x]
  (if (string? x)
    (try
      #?(:clj  (UUID/fromString x)
         ;; http://stackoverflow.com/questions/7905929/how-to-test-valid-uuid-guid
         :cljs (if (re-find #"^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$" x)
                 (uuid x)
                 x))
      (catch #?(:clj Exception, :cljs js/Error) _ x))
    x))

#?(:clj
   (def ^DateTimeFormatter +string->date-format+
     (-> (DateTimeFormatterBuilder.)
         (.appendPattern "yyyy-MM-dd['T'HH:mm:ss[.SSS][XXXX][XXXXX]]")
         (.parseDefaulting ChronoField/HOUR_OF_DAY 0)
         (.parseDefaulting ChronoField/OFFSET_SECONDS 0)
         (.toFormatter))))

(defn string->date [_ x]
  (if (string? x)
    (try
      #?(:clj  (Date/from (Instant/from (.parse +string->date-format+ x)))
         :cljs (js/Date. (.getTime (goog.date.UtcDateTime.fromIsoString x))))
      (catch #?(:clj Exception, :cljs js/Error) _ x))
    x))

#?(:clj
   (def ^DateTimeFormatter +date->string-format+
     (-> (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
         (.withZone (ZoneId/of "UTC")))))

(defn date->string [_ x]
  (if (inst? x)
    (try
      #?(:clj  (.format +date->string-format+ (Instant/ofEpochMilli (inst-ms x)))
         :cljs (.toISOString x))
      (catch #?(:clj Exception, :cljs js/Error) _ x))
    x))

(defn string->symbol [_ x]
  (if (string? x)
    (symbol x)
    x))

(defn string->nil [_ x]
  (if (= "" x)
    nil
    x))

(defn any->string [_ x]
  (if-not (nil? x)
    (str x)))

(defn number->double [_ x]
  (if (number? x)
    (double x)
    x))

(defn any->any [_ x] x)

;;
;; Maps
;;

(defn strip-extra-keys [{:keys [::parse/keys]} x]
  (if (and keys (map? x))
    (select-keys x keys)
    x))

;; TODO: remove this as it couples transformation & validation?
(defn fail-on-extra-keys [{:keys [::parse/keys]} x]
  (if (and (map? x) (not (set/subset? (-> x (clojure.core/keys) (set)) keys)))
    ::s/invalid
    x))

;;
;; Tuples
;;

(defn strip-extra-values [{:keys [::parse/items]} x]
  (let [size (count items)]
    (if (and (vector? x) (> (count x) size))
      (subvec x 0 size)
      x)))

;;
;; type decoders
;;

(def json-type-decoders
  (merge
    {:keyword string->keyword
     :uuid (keyword-or-string-> string->uuid)
     :date (keyword-or-string-> string->date)
     :symbol (keyword-or-string-> string->symbol)
     :long (keyword-> string->long)
     :double (keyword-> string->double)
     :boolean (keyword-> string->boolean)
     :string keyword->string}
    #?(:clj
       {:uri nil
        :bigdec nil
        :ratio nil})))

(def string-type-decoders
  (merge
    json-type-decoders
    {:long (keyword-or-string-> string->long)
     :double (keyword-or-string-> string->double)
     :boolean (keyword-or-string-> string->boolean)}))

(def strip-extra-keys-type-decoders
  {:map strip-extra-keys})

(def fail-on-extra-keys-type-decoders
  {:map fail-on-extra-keys})

(def strip-extra-values-type-decoders
  {:tuple strip-extra-values})

;;
;; type encoders
;;

(def json-type-encoders
  {:keyword keyword->string
   :symbol any->string
   :uuid any->string
   :uri any->string
   :bigdec any->string
   :date date->string
   :map any->any
   :set any->any
   :vector any->any
   #?@(:clj [:ratio number->double])})

(def string-type-encoders
  (merge
    json-type-encoders
    {:long any->string
     :double any->string}))
