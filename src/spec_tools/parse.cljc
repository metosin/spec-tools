(ns spec-tools.parse
  (:require [clojure.spec :as s]))

(defn parse [{:keys [parser] :as opts} spec]
  (assert (map? opts) "options should be a map")
  (assert (ifn? parser) "parser is not a ifn")
  (parser
    (if (or (sequential? spec) (keyword? spec))
      (s/form spec) spec)
    opts))

;;
;; A sample parser
;;

(defmulti parse-spec (fn [x _] (if (sequential? x) (first x) x)))

(defn- map-key [required? namespaced?]
  (fn [k]
    [required?
     (if namespaced? k (-> k name keyword))]))

(defmethod parse-spec 'clojure.spec/keys [[_ & args] opts]
  (let [{:keys [req opt req-un opt-un]} (apply hash-map args)
        zipped (fn [required? namespaced? source]
                 (zipmap
                   (map (map-key required? namespaced?) source)
                   (map (partial parse opts) source)))]
    (merge
      (zipped true true req)
      (zipped false true opt)
      (zipped true false req-un)
      (zipped false false opt-un))))

(defmethod parse-spec 'clojure.spec/every [[_ pred] opts]
  [(parse opts pred)])

(defmethod parse-spec 'clojure.spec/and [[_ pred] opts]
  (parse opts pred))

(defmethod parse-spec 'clojure.core/fn [_ _]
  ::unknown)

;; Sample handler for a leaf spec
(defmethod parse-spec 'clojure.core/integer? [x _]
  x)

(defmethod parse-spec :default [x _]
  x)

;;
;; spike
;;

(s/def ::order-id integer?)
(s/def ::product-id integer?)
(s/def ::product-name string?)
(s/def ::price double?)
(s/def ::quantity integer?)
(s/def ::name string?)
(s/def ::zip #(> % 10))
(s/def ::street string?)
(s/def ::country (s/and keyword? #{:fi :po}))
(s/def ::receiver (s/keys :req-un [::name ::street ::zip]
                          :opt-un [::country]))
(s/def ::orderline (s/keys :req [::product-id ::price]
                           :opt-un [::product-name]))
(s/def ::orderlines (s/coll-of ::orderline))
(s/def ::order (s/keys :req-un [::order-id ::orderlines ::receiver]))
(s/def ::order-with-line (s/and ::order #(> (::orderlines 1))))

(def parser (partial parse {:parser parse-spec}))

(parser ::order)
;{[true :order-id] clojure.core/integer?,
; [true :orderlines] [{[true :spec-tools.parse/product-id] clojure.core/integer?,
;                      [true :spec-tools.parse/price] clojure.core/double?,
;                      [false :product-name] clojure.core/string?}],
; [true :receiver] {[true :name] clojure.core/string?,
;                   [true :street] clojure.core/string?,
;                   [true :zip] :spec-tools.parse/unknown,
;                   [false :country] clojure.core/keyword?}}
