(ns spec-tools.swagger.core
  (:require [clojure.walk :as walk]
            [spec-tools.json-schema :as json-schema]
            [spec-tools.visitor :as visitor]
            [spec-tools.impl :as impl]
            [spec-tools.core :as st]))

;;
;; conversion
;;

(defn- spec-dispatch [dispatch _ _ _] dispatch)
(defmulti accept-spec spec-dispatch :default ::default)

(defmethod accept-spec 'clojure.core/float? [_ _ _ _] {:type "number" :format "float"})
(defmethod accept-spec 'clojure.core/double? [_ _ _ _] {:type "number" :format "double"})
(defmethod accept-spec 'clojure.core/nil? [_ _ _ _] {})

;; anyOf is not supported
(defmethod accept-spec 'clojure.spec.alpha/or [_ _ children _]
  (assoc
    (first children)
    :x-anyOf children))

;; allOf is not supported
(defmethod accept-spec 'clojure.spec.alpha/and [_ _ children _]
  (assoc
    (first children)
    :x-allOf children))

(defn- accept-merge [children]
  ;; Use x-anyOf and x-allOf instead of normal versions
  {:type "object"
   :properties (->> (concat children
                            (mapcat :x-anyOf children)
                            (mapcat :x-allOf children))
                    (map :properties)
                    (reduce merge {}))
   ;; Don't include top schema from s/or.
   :required (->> (concat (remove :x-anyOf children)
                          (mapcat :x-allOf children))
                  (map :required)
                  (reduce into (sorted-set))
                  (into []))})

(defmethod accept-spec 'clojure.spec.alpha/merge [_ _ children _]
  (accept-merge children))

(defmethod accept-spec 'spec-tools.core/merge [_ _ children _]
  (accept-merge children))

;; anyOf is not supported
(defmethod accept-spec 'clojure.spec.alpha/alt [_ _ children _]
  (assoc
    (first children)
    :x-anyOf children))

;; anyOf is not supported
(defmethod accept-spec 'clojure.spec.alpha/cat [_ _ children _]
  {:type "array"
   :items (assoc
            (first children)
            :x-anyOf children)})

;; heterogeneous lists not supported
(defmethod accept-spec 'clojure.spec.alpha/tuple [_ _ children _]
  {:type "array"
   :items {}
   :x-items children})

;; FIXME: resolve a real type, https://github.com/metosin/spec-tools/issues/60
(defmethod accept-spec ::visitor/set [_ _ children _]
  {:enum children :type "string"})

(defmethod accept-spec 'clojure.spec.alpha/nilable [_ _ children {:keys [type in]}]
  (let [k (if (and (= type :parameter) (not= in :body)) :allowEmptyValue :x-nullable)]
    (assoc (impl/unwrap children) k true)))

(defmethod accept-spec ::visitor/spec [dispatch spec children options]
  (let [[_ data] (impl/extract-form spec)
        swagger-meta (impl/unlift-keys data "swagger")]
    (merge (json-schema/accept-spec dispatch spec children options) swagger-meta)))

(defmethod accept-spec ::default [dispatch spec children options]
  (json-schema/accept-spec dispatch spec children options))

(defn transform
  "Generate Swagger schema matching the given clojure.spec spec.

  Since clojure.spec is more expressive than Swagger schemas, everything that
  satisfies the spec should satisfy the resulting schema, but the converse is
  not true."
  ([spec]
   (transform spec nil))
  ([spec options]
   (visitor/visit spec accept-spec options)))

;;
;; extract swagger2 parameters
;;

(defmulti extract-parameter (fn [in _] in))

(defmethod extract-parameter :body [_ spec]
  (let [schema (transform spec {:in :body, :type :parameter})]
    [{:in "body"
      :name (-> spec st/spec-name impl/qualified-name (or ""))
      :description (-> spec st/spec-description (or ""))
      :required (not (impl/nilable-spec? spec))
      :schema schema}]))

(defmethod extract-parameter :default [in spec]
  (let [{:keys [properties required]} (transform spec {:in in, :type :parameter})]
    (mapv
      (fn [[k {:keys [type] :as schema}]]
        (merge
          {:in (name in)
           :name k
           :description (-> spec st/spec-description (or ""))
           :type type
           :required (contains? (set required) k)}
          schema))
      properties)))

;;
;; expand the spec
;;

(defmulti expand (fn [k _ _ _] k))

(defmethod expand ::responses [_ v acc _]
  {:responses
   (into
     (or (:responses acc) {})
     (for [[status response] v]
       [status (as-> response $
                     (if (:schema $) (update $ :schema transform {:type :schema}) $)
                     (update $ :description (fnil identity "")))]))})

(defmethod expand ::parameters [_ v acc _]
  (let [old (or (:parameters acc) [])
        new (mapcat (fn [[in spec]] (extract-parameter in spec)) v)
        merged (->> (into old new)
                    (reverse)
                    (reduce
                      (fn [[ps cache :as acc] p]
                        (let [c (select-keys p [:in :name])]
                          (if-not (cache c)
                            [(conj ps p) (conj cache c)]
                            acc)))
                      [[] #{}])
                    (first)
                    (reverse)
                    (vec))]
    {:parameters merged}))

(defn expand-qualified-keywords [x options]
  (let [accept? (set (keys (methods expand)))]
    (walk/postwalk
      (fn [x]
        (if (map? x)
          (reduce-kv
            (fn [acc k v]
              (if (accept? k)
                (-> acc (dissoc k) (merge (expand k v acc options)))
                acc))
            x
            x)
          x))
      x)))

;;
;; generate the swagger spec
;;

(defn swagger-spec
  "Transforms data into a swagger2 spec. Input data must conform
  to the Swagger2 Spec (http://swagger.io/specification/) with a
  exception that it can have any qualified keywords that are expanded
  with the `spec-tools.swagger.core/expand` multimethod."
  ([x]
   (swagger-spec x nil))
  ([x options]
   (expand-qualified-keywords x options)))
