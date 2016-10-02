(ns spec-tools.parse
  (:require [clojure.spec :as s]
            [clojure.spec :as s]))

(defmulti parse-list first)

(defn parse [spec]
  (if (or (fn? spec) (symbol? spec))
    spec
    (let [form (s/form spec)]
      ((if (seq? form) parse-list identity) form))))

(defn- map-key [required? namespaced?]
  (fn [k]
    [required?
     (if namespaced? k (-> k name keyword))]))

(defmethod parse-list 'clojure.spec/keys [[_ & args]]
  (let [{:keys [req opt req-un opt-un]} (apply hash-map args)
        zipped (fn [required? namespaced? source]
                 (zipmap
                   (map (map-key required? namespaced?) source)
                   (map parse source)))]
    (merge
      (zipped true true req)
      (zipped false true opt)
      (zipped true false req-un)
      (zipped false false opt-un))))

(defmethod parse-list 'clojure.spec/every [[_ pred]]
  [(parse pred)])

(defmethod parse-list 'clojure.spec/and [[_ pred]]
  (parse pred))

(defmethod parse-list 'clojure.core/fn [_]
  ::unknown)

;;
;; spike
;;


(s/def ::order-id integer?)
(s/def ::product-id integer?)
(s/def ::product-name string?)
(s/def ::price double?)
(s/def ::quantity integer?)
(s/def ::name string?)
(s/def ::zip #(> % 10)) ; should be (s/and integer? #(> % 10))
(s/def ::street string?)
(s/def ::country (s/and keyword? #{:fi :po}))
(s/def ::receiver (s/keys :req-un [::name ::street ::zip]
                          :opt-un [::country]))
(s/def ::orderline (s/keys :req [::product-id ::price]
                           :opt-un [::product-name]))
(s/def ::orderlines (s/coll-of ::orderline))
(s/def ::order (s/keys :req-un [::order-id ::orderlines ::receiver]))
(s/def ::order-with-line (s/and ::order #(> (::orderlines 1))))

(parse ::order)
;{[true :order-id] clojure.core/integer?,
; [true :orderlines] [{[true :spec-tools.parse/product-id] clojure.core/integer?,
;                      [true :spec-tools.parse/price] clojure.core/double?,
;                      [false :product-name] clojure.core/string?}],
; [true :receiver] {[true :name] clojure.core/string?,
;                   [true :street] clojure.core/string?,
;                   [true :zip] :spec-tools.parse/unknown,
;                   [false :country] clojure.core/keyword?}}
