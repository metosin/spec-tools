(ns spec-tools.swagger.spec3
  (:require [clojure.spec.alpha :as s]
   [spec-tools.core :as st]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as string]
   [spec-tools.data-spec :as ds]))

(def non-empty-string-ascii-gen
  "Generator for non-empty ascii strings"
  (gen/such-that #(not= "" %)
                  (gen/string-ascii)))
(def non-empty-string-alphanumeric-gen
  "Generator for non-empty alphanumeric strings"
  (gen/such-that #(not= "" %)
                  (gen/string-alphanumeric)))

(s/def ::string-not-empty (s/with-gen
                            (s/and string? #(not= "" %))
                            (fn [] non-empty-string-ascii-gen)))
(s/def ::reference
  (ds/spec
   ::reference
   {(ds/req :?ref) ::string-not-empty}))

(s/def ::example
  (s/and
   (ds/spec {:keys-default ds/opt
             :name ::example
             :spec
             {:summary       ::string-not-empty
              :description   ::string-not-empty
              :value         any?}
             :externalValue {keyword? ::string-not-empty}})
   not-empty))

(s/def ::spec qualified-keyword?)
(s/def ::json-schema-or-reference
  (s/or
   :provided (ds/spec ::json-schema
                       {(ds/opt :?schema) ::string-not-empty
                        (ds/req :type)     (s/spec #{"string"
                                                     "number"
                                                     "integer"
                                                     "array"
                                                     "object"
                                                     "boolean"
                                                     "null"})})
   :reference ::reference
   :empty-map (s/and map? empty?)
   :bool boolean?))

(s/def ::schema (s/or :spec qualified-keyword?
                      :json-schema ::json-schema-or-reference))

(def url (re-pattern #"^(https?|ftp)://(-\.)?([^\s/?\.#-]+\.?)+(/\S*)?$"))
(s/def ::url
   (s/with-gen (s/and string? #(re-matches url %))
     #(s/gen #{"https://openapi.com"
               "ftp://random.ly"
               "https://clojuredocs.org/clojure.core/rand-nth"})))
(def email (re-pattern #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}"))
(def email-gen
  "Generator for email addresses"
  (gen/fmap
    (fn [[name host tld]]
      (str name "@" host "." tld))
    (gen/tuple
      non-empty-string-alphanumeric-gen
      non-empty-string-alphanumeric-gen
      non-empty-string-alphanumeric-gen)))
(s/def ::email (s/with-gen (s/and string? #(re-matches email %))
                (fn [] email-gen)))

(def path (re-pattern
           #"(?<=/)((\{[^\s/?{}.#-][^\s/{}]+)+}|([^\s/?{}.#-][^\s/{}]+)+)/?"))
(def path-gen
  "Generator for generating paths"
  (gen/fmap #(as-> (interleave (repeat "/") %) inter
                   (conj (vec inter) (rand-nth [nil "/"]))
                   (apply str inter))
    (gen/not-empty
      (gen/vector
       (gen/one-of
        [(gen/fmap
          #(str "{"  % "}")
          non-empty-string-alphanumeric-gen)
         non-empty-string-alphanumeric-gen]) 2 6))))
(s/def ::path (s/with-gen
                (s/and string?
                       #(= (count (re-seq path %))
                           (as-> (string/split % #"/") loc
                             ((fn [x] (if (empty? (first x)) (rest x) x)) loc)
                             (count loc))))
                (fn [] path-gen)))

(s/def ::external-docs
  (ds/spec
    ::external-docs
    {(ds/opt :description) ::string-not-empty
     (ds/req :url) ::url}))

(s/def ::oauth-flow
  (ds/spec {:keys-default ds/opt
            :name ::oauth-flow
            :spec {:authorizationUrl ::url
                   :tokenUrl         ::url
                   :refreshUrl       ::url
                   :scopes           {keyword? ::string-not-empty}}}))

(s/def ::oauth-flows
  (ds/spec {:keys-default ds/opt
            :name ::oauth-flows
            :spec
            {:implicit (s/and
                        ::oauth-flow
                        (s/keys :req-un [::authorizationUrl ::scopes]))
             :password (s/and
                        ::oauth-flow
                        (s/keys :req-un [::tokenUrl ::scopes]))
             :clientCredentials (s/and
                                 ::oauth-flow
                                 (s/keys :req-un [::tokenUrl ::scopes]))
             :authorizationCode
             (s/and ::oauth-flow
              (s/keys :req-un [::authorizationUrl ::tokenUrl ::scopes]))}}))
(defmulti security-scheme-type :type)
(defmethod security-scheme-type "apiKey" [_]
 (ds/spec
    ::apiKey
    {:name ::string-not-empty
     :in  (s/spec #{"query" "header" "cookie"})}))

(defmethod security-scheme-type "http" [_]
 (ds/spec ::http {:scheme ::string-not-empty
                  (ds/opt :bearerFormat) ::string-not-empty}))

(defmethod security-scheme-type "oauth2" [_]
 (ds/spec ::oauth2 {:flows ::oauth-flows}))

(defmethod security-scheme-type "openIdConnect" [_]
 (ds/spec ::openIdConnect {:openIdConnectUrl ::url}))


(def auth-sample {:type "http" :scheme "basic"})
(def api-key-sample {:type "apiKey" :name "api_key" :in "header"})
(def jwt-bearer-sample {:type "http" :scheme "bearer" :bearerFormat "JWT"})
(def implicit-oauth2-sample
  {:type  "oauth2"
   :flows {:implicit
           {:authorizationUrl "https://example.com/api/oauth/dialog"
            :scopes           {:write:pets "modify pets in your account"
                               :read:pets  "read your pets"}}}})

(s/def ::security-scheme-type
  (s/with-gen
    (s/multi-spec security-scheme-type :type)
    #(s/gen #{auth-sample
              api-key-sample
              jwt-bearer-sample
              implicit-oauth2-sample})))

(s/def ::security-scheme
 (s/and
  ::security-scheme-type
  (ds/spec
   ::security-scheme-base
   {(ds/opt :description) ::string-not-empty})))

(s/def ::security-scheme-or-reference
 (s/or
  :security-scheme ::security-scheme
  :reference ::reference))

(s/def ::security-requirement
  (ds/spec
    ::security-requirement
    {keyword? [::string-not-empty]}))

(s/def ::boolean-default-false
  (st/spec
   {:spec                boolean?
    :json-schema/default false}))

(s/def ::example-or-reference
 (s/or
  :example ::example
  :reference ::reference))

(defmulti parameter-style :style :default "simple")
(defmethod parameter-style "form" [_]
 (ds/spec ::parameter-explode-header-form
  {(ds/opt :explode) (st/spec
                      {:spec                boolean?
                       :json-schema/default true})}))
(def parameter-explode-header-default
  {(ds/opt :explode) ::boolean-default-false})
(defmethod parameter-style "simple" [_]
 (ds/spec ::parameter-explode-header-default
          parameter-explode-header-default))
(s/def ::parameter-style
  (s/multi-spec parameter-style :style))

(defmulti parameter-in :in)

(s/def ::query-parameter
 (ds/spec {:keys-default ds/opt
           :name ::query-parameter
           :spec
           {:allowEmptyValue ::boolean-default-false
            :allowReserved ::boolean-default-false
            :style (st/spec {:spec (s/spec #{"form"
                                             "spaceDelimited"
                                             "pipeDelimited"
                                             "deepObject"})
                             :json-schema/default "form"})}}))

(defmethod parameter-in "query" [_] ::query-parameter)

(defmethod parameter-in "path" [_]
  (ds/spec ::path-parameter
   {(ds/req :required) true?
    (ds/opt :style) (st/spec
                     {:spec (s/spec #{"matrix" "label" "simple"})
                      :json-schema/default "simple"})}))

(def header-parameter {(ds/opt :style) (s/spec #{"simple"})})

(defmethod parameter-in "header" [_]
  (ds/spec ::header-parameter header-parameter))

(defmethod parameter-in "cookie" [_]
  (ds/spec ::cookie-parameter {:style (s/spec #{"form"})}))
(s/def ::parameter-in
  (s/multi-spec parameter-in :in))

(def header-param-shared {:description ::string-not-empty
                          :required ::boolean-default-false
                          :deprecated ::boolean-default-false
                          :example any?
                          :examples {keyword? ::example-or-reference}})
(def header-base (merge header-param-shared
                        header-parameter
                        parameter-explode-header-default))
(s/def ::header-base-no-content
  (s/and (ds/spec {:keys-default ds/opt
                   :name ::header-base-no-content
                   :spec (merge header-base {:schema ::schema})})
         not-empty))
(s/def ::header-no-content
  (s/with-gen
   (s/and
      ::header-base-no-content
      #(-> % (select-keys [:name :in]) count (= 0)))
   #(s/gen ::header-base-no-content)))



(s/def ::header-no-content-or-reference
  (s/or :header-no-content ::header-no-content
        :reference ::reference))

(s/def ::encoding-obj
 (s/and
  (ds/spec
   ::encoding-obj
    {(ds/opt :contentType) ::string-not-empty
     (ds/opt :headers) {keyword? ::header-no-content-or-reference}})
  ::query-parameter
  ::parameter-style
  not-empty))

(s/def ::media-type
 (s/and
  (ds/spec {:keys-default ds/opt
             :name ::media-type
             :spec  {:example any?
                     :schema ::schema
                     :examples {keyword? ::example-or-reference}
                     :encoding {keyword?  ::encoding-obj}}})
  not-empty))

(def content-types [:application/json
                    :application/xml
                    :application/x-www-form-urlencoded
                    :text/css
                    :text/css
                    :text/html])

(def content-gen (gen/fmap #(into {} (partition-all 2) %)
                   (gen/fmap (partial apply interleave)
                     (gen/tuple (gen/return content-types)
                      (gen/list (s/gen ::media-type))))))

(s/def ::content
  (s/with-gen
    (s/every-kv keyword? ::media-type)
    (fn [] content-gen)))
(defn schema-xor-content?
  "checks that content and schema are not both present"
  [x]
  {:pre [(map? x)]}
  (-> x (select-keys [:content :schema]) count (not= 2)))

(def schema-xor-content-gen
  (gen/one-of
   [(gen/fmap (fn [x] {:schema x}) (s/gen ::schema))
    (gen/fmap (fn [x] {:content x}) (s/gen ::content))]))
(s/def ::header-base-for-content
  (s/and (ds/spec {:keys-default ds/opt
                   :name ::header-base-for-content
                   :spec header-base})
         not-empty))

(def gen-header
  (gen/fmap
            (partial apply merge)
            (gen/tuple
              schema-xor-content-gen
              (s/gen ::header-base-for-content))))

(s/def ::header
  (s/with-gen
   (s/and
      (ds/spec {:keys-default ds/opt
                :name ::header
                :spec (merge header-base {:content ::content :schema ::schema})})
      #(-> % (select-keys [:name :in]) count (= 0))
      schema-xor-content?)
   (fn [] gen-header)))

(s/def ::header-or-reference (s/or
                              :header ::header
                              :reference ::reference))
(def parameter-shared
  (merge
    header-param-shared
    {(ds/req :name) ::string-not-empty
     (ds/req :in )  (s/spec #{"query" "header" "path" "cookie"})}))

(s/def ::parameter-shared
  (ds/spec ::parameter-shared parameter-shared))

(def param-gen (gen/fmap
                (partial apply merge)
                (gen/tuple
                 schema-xor-content-gen
                  (s/gen ::parameter-shared)
                  (s/gen ::parameter-style)
                  (s/gen ::parameter-in))))

(s/def ::parameter
  (s/with-gen
    (s/and
      (ds/spec
       {:keys-default ds/opt
        :name ::parameter
        :spec  (merge
                parameter-shared
                {:content ::content
                 :schema ::schema})})
      ::parameter-in
      ::parameter-style
      schema-xor-content?)
    (fn [] param-gen)))


(defn distinct-params?
  "Checks that all entries in a collection of parameters have distinct locations"
  [params]
  (->> params
   (into #{} (map #(select-keys % [:name :in])))
   count
   (= (count params))))

(s/def ::parameter-or-reference (s/or
                                 :parameter ::parameter
                                 :reference ::reference))

(def ref-or-param-coll-gen  (gen/vector
                              (gen/one-of
                               [(s/gen ::parameter)
                                (s/gen ::reference)]) 3 5))

(s/def
  ::distinct-parameter-or-reference-list
  (s/with-gen
    (s/and
     coll?
     (s/conformer seq)
     (s/cat
      :params (s/& (s/* ::parameter) distinct-params?)
      :refs (s/* ::reference)))
   (fn [] ref-or-param-coll-gen)))

(s/def ::server-variable (ds/spec
                          ::server-variable
                          {(ds/opt :enum) [::string-not-empty]
                           :default ::string-not-empty
                           (ds/opt :description) ::string-not-empty}))

(s/def ::server (ds/spec
                 ::server
                 {:url ::url
                  (ds/opt :description) ::string-not-empty
                  (ds/opt :variables) {keyword? ::server-variable}}))

(s/def ::link
  (ds/spec
    {:keys-default ds/opt
     :name ::link
     :spec {:operationRef ::string-not-empty
            :operationId ::string-not-empty
            :parameters {keyword? any?}
            :requestBody any?
            :description ::string-not-empty
            :server ::server}}))

(s/def ::link-or-reference (s/or
                              :link ::link
                              :reference ::reference))

(s/def ::request-body
  (ds/spec
    {:keys-default ds/opt
     :name ::request-body
     :spec {:description ::string-not-empty
            (ds/req :content) ::content
            :required ::boolean-default-false}}))

(s/def ::request-body-or-reference (s/or
                                    :request-body ::request-body
                                    :reference ::reference))

(s/def ::response
  (ds/spec
    {:keys-default ds/opt
     :name ::response
     :spec {(ds/req :description) ::string-not-empty
            :headers {keyword? ::header-or-reference}
            :content ::content
            :links {keyword? ::link-or-reference}}}))

(s/def ::response-or-reference (s/or
                                :response ::response
                                :reference ::reference))
(def number-or-x-gen
  (gen/one-of [(gen/choose 0 9)
               (gen/return "x")]))
(def number-codes-gen
  (gen/fmap
   (partial apply (comp keyword (rand-nth [identity string/upper-case]) str))
   (gen/tuple (gen/choose 1 5) number-or-x-gen number-or-x-gen)))

(def response-code-gen
  (gen/one-of [number-codes-gen
               (gen/return :default)]))

(s/def ::response-code
  (s/with-gen
   (s/and
    keyword?
    (s/conformer name)
    (s/or
      :code #(re-matches #"^[1-5](\d|[xX]){2}$" %)
      :default #(= % "default")))
   (fn [] response-code-gen)))

(defn homogenous-map-gen [k-spec opt v-spec]
  (gen/fmap #(into {} (partition-all 2) %)
    (gen/fmap (partial apply interleave)
      (gen/tuple
       (gen/vector-distinct (s/gen k-spec) opt)
       (gen/list (s/gen v-spec))))))

(def responses-gen
  (gen/not-empty
   (homogenous-map-gen
    ::response-code {:min-elements 1 :max-elements 5} ::response-or-reference)))

(s/def ::responses
  (s/with-gen (s/and
               (s/every-kv ::response-code ::response-or-reference)
               not-empty)
    (fn [] responses-gen)))

(def operation-without-callbacks
   {:tags [::string-not-empty]
    :summary ::string-not-empty
    :description ::string-not-empty
    :externalDocs ::external-docs
    :operationId ::string-not-empty
    :parameters ::distinct-parameter-or-reference-list
    :requestBody ::request-body-or-reference
    (ds/req :responses) ::responses
    :deprecated ::boolean-default-false
    :security [::security-requirement]
              :servers [::server]})

(s/def ::operation-without-callbacks
  (ds/spec
    {:keys-default ds/opt
     :name ::operation-without-callbacks
     :spec operation-without-callbacks}))

(def base-path-item
   {:?ref ::string-not-empty
    :summary ::string-not-empty
    :description ::string-not-empty
    :parameters ::distinct-parameter-or-reference-list
    :server [::server]})

(def operations-keywords
  [:get :put :post :delete :options :head :patch :trace])

(def callback-operations (into {}
                               (map (fn [x] [x ::operation-without-callbacks]))
                               operations-keywords))

(s/def ::path-item-in-callback
 (ds/spec {:keys-default ds/opt
           :name ::path-item-in-callback
           :spec (merge base-path-item callback-operations)}))

(def callback-gen
  (gen/not-empty
   (homogenous-map-gen
    keyword? {:min-elements 1 :max-elements 3} ::path-item-in-callback)))

(s/def ::callback
  (s/with-gen
    (s/every-kv keyword? ::path-item-in-callback)
    (fn [] callback-gen)))

(s/def ::callback-or-reference (s/or
                                :callback ::callback
                                :reference ::reference))

(def callbacks-gen
  (homogenous-map-gen
   keyword? {:min-elements 0 :max-elements 3} ::callback-or-reference))

(s/def ::callbacks
  (s/with-gen
    (s/every-kv keyword? ::callback-or-reference)
    (fn [] callbacks-gen)))
(def callbacks {:callbacks  ::callbacks})

(s/def ::operation
  (ds/spec
    {:keys-default ds/opt
     :name ::operation
     :spec (merge operation-without-callbacks callbacks)}))

(def path-operations (into {}
                           (map (fn [x] [x ::operation]))
                           operations-keywords))
(s/def ::path-item
  (ds/spec {:keys-default ds/opt
            :name ::path-item
            :spec (merge base-path-item path-operations)}))

(s/def ::open-api
  (ds/spec
    {:keys-default ds/opt
     :name ::open-api
     :spec {(ds/req :openapi) #(re-matches #"(?is)(3\.0\.)(\d)+(-rc[0-2])?$" %)
            (ds/req :info) {(ds/req :title) ::string-not-empty
                              :description ::string-not-empty
                              :termsOfService ::string-not-empty
                              :contact { :name ::string-not-empty
                                                 :url ::url
                                                 :email ::email}
                              :license {(ds/req :name) ::string-not-empty
                                                 :url ::string-not-empty}
                             (ds/req :version) ::string-not-empty}
            :servers (st/spec {:spec (s/every ::server)
                               :json-schema/default [{:url "/"}]})
            (ds/req :paths) (s/every-kv ::path ::path-item)
            :components {:schemas {keyword? ::schema}
                         :responses {keyword? ::response-or-reference}
                         :parameters {keyword? ::parameter-or-reference}
                         :examples {keyword? ::example-or-reference}
                         :requestBodies {keyword? ::request-body-or-reference}
                         :headers {keyword? ::header-or-reference}
                         :securitySchemes {keyword? ::security-scheme-or-reference}
                         :links {keyword? ::link-or-reference}
                         :callbacks {keyword? ::callback-or-reference}}
            :security [::security-requirement]
            :tags [{:name ::string-not-empty
                             :description ::string-not-empty
                             :externalDocs ::external-docs}]
            :externalDocs ::external-docs}}))
