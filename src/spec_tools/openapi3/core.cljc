(ns spec-tools.openapi3.core
  (:require [spec-tools.json-schema :as json-schema]
            [spec-tools.visitor :as visitor]))

(defn- spec-dispatch [dispatch _ _ _] dispatch)

(defmulti accept-spec spec-dispatch :default ::default)

(defmethod accept-spec 'clojure.core/float? [_ _ _ _] {:type "number" :format "float"})
(defmethod accept-spec 'clojure.core/double? [_ _ _ _] {:type "number" :format "double"})
(defmethod accept-spec 'clojure.core/nil? [_ _ _ _] {})
(defmethod accept-spec 'clojure.spec.alpha/or [_ _ children _] {:anyOf children})
(defmethod accept-spec 'clojure.spec.alpha/and [_ _ children _] {:allOf children})

(defn- accept-merge
  [children]
  {:type       "object"
   :properties {}})

(defmethod accept-spec 'clojure.spec.alpha/merge [_ _ children _] {})
(defmethod accept-spec ::default [dispatch spec children options]
  (json-schema/accept-spec dispatch spec children options))

(defn transform
  "Generate OpenAPI3 schema matching the given clojure.spec spec."
  ([spec]
   (transform spec nil))
  ([spec options]
   (visitor/visit spec accept-spec options)))
