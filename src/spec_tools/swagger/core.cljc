(ns spec-tools.swagger.core
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
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
  (let [children' (map #(if (contains? % :$ref)
                          (first (vals (::definitions %)))
                          %)
                       children)]
    {:type "object"
     :properties (->> (concat children'
                              (mapcat :x-anyOf children')
                              (mapcat :x-allOf children'))
                      (map :properties)
                      (reduce merge {}))
     ;; Don't include top schema from s/or.
     :required (->> (concat (remove :x-anyOf children')
                            (mapcat :x-allOf children'))
                    (map :required)
                    (reduce into (sorted-set))
                    (into []))}))

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

(defmethod accept-spec 'clojure.core/sequential? [_ _ _ _]
  {:type "array"
   :items {}})

;; FIXME: resolve a real type, https://github.com/metosin/spec-tools/issues/60
(defmethod accept-spec ::visitor/set [_ _ children _]
  {:enum children :type "string"})

(defmethod accept-spec 'clojure.spec.alpha/nilable [_ _ children {:keys [type in]}]
  (let [k (if (and (= type :parameter) (not= in :body)) :allowEmptyValue :x-nullable)]
    (assoc (impl/unwrap children) k true)))

(defmethod accept-spec ::visitor/spec [dispatch spec children options]
  (let [[_ data] (impl/extract-form spec)
        swagger-meta (impl/unlift-keys data "swagger")]
    (or (:swagger data)
        (merge (json-schema/accept-spec dispatch spec children options) swagger-meta))))

(defmethod accept-spec ::default [dispatch spec children options]
  (json-schema/accept-spec dispatch spec children options))

(defn- update-if [m k f & args]
  (if (contains? m k)
    (apply update m k f args)
    m))

(defmulti create-or-raise-refs (fn [{:keys [type]} _] type))

(defmethod create-or-raise-refs "object" [swagger options]
  (if (and (or (= :schema (:type options))
               (= :body (:in options)))
           (contains? swagger :title))
    (let [title (string/replace (:title swagger) #"/" ".")
          swagger' (create-or-raise-refs (dissoc swagger :title) options)]
      {:$ref         (str "#/definitions/" title)
       ::definitions (merge {title (dissoc swagger' ::definitions)} (::definitions swagger'))})
    (let [definitions (apply merge
                             (::definitions (:additionalProperties swagger))
                             (map ::definitions (vals (:properties swagger))))]
      (if definitions
        (-> swagger
            (assoc ::definitions definitions)
            (update-if :properties update-vals #(dissoc % ::definitions))
            (update-if :additionalProperties dissoc ::definitions))
        swagger))))

(defmethod create-or-raise-refs "array" [swagger _]
  (let [definitions (get-in swagger [:items ::definitions])]
    (if definitions
      (-> swagger
          (update ::definitions merge definitions)
          (update :items dissoc ::definitions))
      swagger)))

(defmethod create-or-raise-refs :default [swagger _]
  swagger)

(defn- accept-spec-with-refs [dispatch spec children options]
  (create-or-raise-refs
    (accept-spec dispatch spec children options)
    options))

(defn transform
  "Generate Swagger schema matching the given clojure.spec spec.

  Since clojure.spec is more expressive than Swagger schemas, everything that
  satisfies the spec should satisfy the resulting schema, but the converse is
  not true.

  Available options:

  | Key      | Description
  |----------|-----------------------------------------------------------
  | `:refs?` | Whether refs should be created for objects. Default: false

  "
  ([spec]
   (transform spec nil))
  ([spec options]
   (if (:refs? options)
     (visitor/visit spec accept-spec-with-refs options)
     (visitor/visit spec accept-spec options))))

;;
;; extract swagger2 parameters
;;

(defmulti extract-parameter (fn [in _ & _] in))

(defmethod extract-parameter :body
  ([in spec]
   (extract-parameter in spec nil))
  ([_ spec options]
   (let [schema (transform spec (merge options {:in :body, :type :parameter}))]
     [{:in          "body"
       :name        (-> spec st/spec-name impl/qualified-name (or "body"))
       :description (-> spec st/spec-description (or ""))
       :required    (not (impl/nilable-spec? spec))
       :schema      schema}])))

(defmethod extract-parameter :default
  ([in spec]
   (extract-parameter in spec nil))
  ([in spec options]
   (let [{:keys [properties required]} (transform spec (merge options {:in in, :type :parameter}))]
     (mapv
       (fn [[k {:keys [type] :as schema}]]
         (merge
           {:in          (name in)
            :name        k
            :description (-> spec st/spec-description (or ""))
            :type        type
            :required    (contains? (set required) k)}
           schema))
       properties))))

;;
;; expand the spec
;;

(defmulti expand (fn [k _ _ _] k))

(defmethod expand ::responses [_ v acc options]
  (let [responses (into
                    (or (:responses acc) {})
                    (for [[status response] v]
                      [status (as-> response $
                                    (if (:schema $) (update $ :schema transform (merge options {:type :schema})) $)
                                    (update $ :description (fnil identity "")))]))]
    (if (:refs? options)
      {:responses   (update-vals responses #(update-if % :schema dissoc ::definitions))
       :definitions (apply merge (map #(get-in % [:schema ::definitions]) (vals responses)))}
      {:responses responses})))

(defmethod expand ::parameters [_ v acc options]
  (let [old (or (:parameters acc) [])
        new (mapcat (fn [[in spec]] (extract-parameter in spec options)) v)
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
    (if (:refs? options)
      {:parameters  (mapv #(update-if % :schema dissoc ::definitions) merged)
       :definitions (apply merge (map #(get-in % [:schema ::definitions]) merged))}
      {:parameters merged})))

(defn expand-qualified-keywords [x options]
  (let [accept? (set (keys (methods expand)))
        merge-only-maps (fn [& colls] (if (every? map? colls) (apply merge colls) (last colls)))]
    (walk/postwalk
      (fn [x]
        (if (map? x)
          (reduce-kv
            (fn [acc k v]
              (if (accept? k)
                (merge-with merge-only-maps (dissoc acc k) (expand k v acc options))
                acc))
            x
            x)
          x))
      x)))

(defn- raise-refs-to-top [swagger-doc]
  (let [swagger-doc'
        (cond-> swagger-doc
          (:paths swagger-doc) (->
                                 (assoc :definitions (apply merge (map :definitions (mapcat vals (vals (:paths swagger-doc))))))
                                 (update :paths update-vals (fn [path] (update-vals path #(dissoc % :definitions))))))]
    (cond-> swagger-doc'
      (nil? (:definitions swagger-doc')) (dissoc swagger-doc' :definitions))))

;;
;; generate the swagger spec
;;

(defn swagger-spec
  "Transforms data into a swagger2 spec. Input data must conform
  to the Swagger2 Spec (https://swagger.io/specification/v2/) with a
  exception that it can have any qualified keywords that are expanded
  with the `spec-tools.swagger.core/expand` multimethod.

  Available options:

  | Key      | Description
  |----------|-----------------------------------------------------------
  | `:refs?` | Whether refs should be created for objects. Default: false
  "
  ([x]
   (swagger-spec x nil))
  ([x options]
   (cond-> (expand-qualified-keywords x options)
     (:refs? options) (raise-refs-to-top))))
