(ns spec-tools.core
  (:require [clojure.spec :as s]))

(def ^:dynamic *conform-mode* nil)

(def +error-code+ #?(:clj  :clojure.spec/invalid
                     :cljs :cljs.spec/invalid))

(defn- double-like? [x]
  (#?(:clj  double?
      :cljs number?) x))

(defn string->int [x]
  (if (string? x)
    (try
      #?(:clj  (Integer/parseInt x)
         :cljs (js/parseInt x 10))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        +error-code+))))

(defn string->long [x]
  (if (string? x)
    (try
      #?(:clj  (Long/parseLong x)
         :cljs (js/parseInt x 10))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        +error-code+))))

(defn string->double [x]
  (if (string? x)
    (try
      #?(:clj  (Double/parseDouble x)
         :cljs (js/parseFloat x))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        +error-code+))))

(defn string->keyword [x]
  (if (string? x)
    (keyword x)))

(defn string->boolean [x]
  (if (string? x)
    (cond
      (= "true" x) true
      (= "false" x) false
      :else +error-code+)))

(def +conformation+
  {:string [string? {integer? string->int
                     int? string->long
                     double-like? string->double
                     keyword? string->keyword
                     boolean? string->boolean}]})

(defn dynamic-conformer [pred]
  (with-meta
    (s/conformer
      (fn [x]
        (if (pred x)
          x
          (if-let [[accept? conformers] (+conformation+ *conform-mode*)]
            (if (accept? x)
              (if-let [conformer (get conformers pred)]
                (conformer x)
                +error-code+)
              +error-code+)
            +error-code+))))
    {::pred pred}))

(defn conform
  ([spec value]
   (s/conform spec value))
  ([mode spec value]
   (binding [*conform-mode* mode]
     (s/conform spec value))))

;;
;; types
;;

(def x-integer? (dynamic-conformer integer?))
(def x-int? (dynamic-conformer int?))
(def x-double? (dynamic-conformer double-like?))
(def x-keyword? (dynamic-conformer keyword?))
(def x-boolean? (dynamic-conformer boolean?))
