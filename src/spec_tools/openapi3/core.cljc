(ns spec-tools.openapi3.core
  (:require [spec-tools.json-schema :as json-schema]
            [spec-tools.visitor :as visitor]
            [spec-tools.impl :as impl]
            [spec-tools.core :as st]
            [clojure.walk :as walk]))

(defn- spec-dispatch [dispatch _ _ _] dispatch)

(defmulti accept-spec spec-dispatch :default ::default)

(defmethod accept-spec 'clojure.core/float? [_ _ _ _]
  {:type "number" :format "float"})

(defmethod accept-spec 'clojure.core/double? [_ _ _ _]
  {:type "number" :format "double"})

(defmethod accept-spec 'clojure.spec.alpha/tuple [_ _ children _]
  {:type "array"
   :items {:anyOf children}})

(defmethod accept-spec 'clojure.core/sequential? [_ _ _ _]
  {:type "array"
   :items {}})

(defmethod accept-spec ::visitor/set [_ _ children _]
  {:enum children :type "string"})

(defmethod accept-spec ::visitor/spec [dispatch spec children options]
  (let [[_ data] (impl/extract-form spec)
        swagger-meta (impl/unlift-keys data "swagger")]
    (or (:swagger data)
        (merge (json-schema/accept-spec dispatch spec children options) swagger-meta))))

(defmethod accept-spec ::default [dispatch spec children options]
  (json-schema/accept-spec dispatch spec children options))

(defn transform
  "Generate OpenAPI3 schema matching the given clojure.spec spec."
  ([spec]
   (transform spec nil))
  ([spec options]
   (visitor/visit spec accept-spec options)))

;;
;; Extract OpenAPI3 parameters
;;
(defn- extract-primitive-param
  [in spec]
  (vector
   {:name        (or (:title spec) (:type spec))
    :in          in
    :description (or (:description spec) "")
    :required    (case in
                   :path true
                   false)
    :schema      spec}))

(defn- extract-object-param
  [in {:keys [properties required]}]
  (mapv
   (fn [[k {:keys [type description allowEmptyValue] :as schema}]]
     {:name        k
      :in          (name in)
      :description (or description "")
      :required    (case in
                     :path true
                     (contains? (set required) k))
      :schema      schema})
   properties))

(defn extract-parameter
  [in spec]
  (let [parameter-spec (transform spec)
        object?        (and (contains? parameter-spec :properties)
                            (= "object" (:type parameter-spec)))]
    (if object?
      (extract-object-param in parameter-spec)
      (extract-primitive-param in parameter-spec))))

;;
;; Expand the spec
;;

(defmulti expand (fn [k _ _ _] k))

(defmethod expand ::schemas [_ v acc _]
  {:schemas
   (into
    (or (:schemas acc) {})
    (for [[name schema] v]
      {name (transform schema)}))})

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

(defn expand-qualified-keywords
  [x options]
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
;; Generate the OpenAPI3 spec
;;

(defn openapi3-spec
  "Transforms data into a OpenAPI3 spec. Input data must conform to the
  Swagger3 Spec (https://swagger.io/specification/) with a exception that it
  can have any qualified keywords which are expanded with the
  `spec-tools.openapi3.core/expand` multimethod."
  ([x]
   (openapi3-spec x nil))
  ([x options]
   (expand-qualified-keywords x options)))
