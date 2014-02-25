;; Copyright Rob Day, 2014
;; Released under the EPL

(ns uk.me.rkd.core-cache-comparison-test
  (:require [clojure.test :refer :all]
            [uk.me.rkd.ttlcache :as mycache]
            [clojure.core.cache :as cache]
            [criterium.core :refer [bench quick-bench quick-benchmark benchmark]]))

(def #^{:macro true} benchmark-fn #'quick-benchmark)

(def m10 (into {} (for [x (range 10)] [x (* 23 x)])))
(def m1k (into {} (for [x (range 1000)] [x (* 23 x)])))
(def m5k (into {} (for [x (range 5000)] [x (* 23 x)])))
(def m100k (into {} (for [x (range 100000)] [x (* 23 x)])))

(def c1k (cache/ttl-cache-factory m1k :ttl 600000))
(def c5k (cache/ttl-cache-factory m5k :ttl 600000))
(def c100k (cache/ttl-cache-factory m100k :ttl 600000))

(def my-c10 (mycache/ttl-cache-factory m10 :ttl 600000))
(def my-c1k (mycache/ttl-cache-factory m1k :ttl 600000))
(def my-c5k (mycache/ttl-cache-factory m5k :ttl 600000))
(def my-c100k (mycache/ttl-cache-factory m100k :ttl 600000))

(defn make-evictable-cache [factory m n]
  (let [map (into {} (for [x (range m)] [x (* 23 x)]))
        my-cache (factory map :ttl 20000)
        _ (Thread/sleep 18000)
        bigger-cache (reduce #(cache/miss %1 %2 (* 23 %2)) my-cache (range m n))
        _ (Thread/sleep 3000)]
    bigger-cache
    ))

(defn complexity-compare [testfn]
  (let [result1k (first (:mean (benchmark-fn (testfn c1k) {})))
        result5k (first (:mean (benchmark-fn (testfn c5k) {})))]
    (/ result5k result1k)))

(defn my-complexity-compare [testfn]
  (let [result1k (first (:mean (benchmark-fn (testfn my-c1k) {})))
        result5k (first (:mean (benchmark-fn (testfn my-c5k) {})))]
    (/ result5k result1k)))

(defn implementation-speed-compare [testfn]
  (let [result (first (:mean (benchmark-fn (testfn c5k) {})))
        myresult (first (:mean (benchmark-fn (testfn my-c5k) {})))]
    (/ result myresult)))

(defn implementation-speed-compare-with-1pc-eviction [testfn]
  (let [c5k (make-evictable-cache cache/ttl-cache-factory 50 5000)
        result (first (:mean (benchmark-fn (testfn c5k) {})))
        my-c5k (make-evictable-cache mycache/ttl-cache-factory 50 5000)
        myresult (first (:mean (benchmark-fn (testfn my-c5k) {})))]
    (/ result myresult)))

(defn implementation-speed-compare-with-5pc-eviction [testfn]
  (let [c5k (make-evictable-cache cache/ttl-cache-factory 250 5000)
        result (first (:mean (benchmark-fn (testfn c5k) {})))
        my-c5k (make-evictable-cache mycache/ttl-cache-factory 250 5000)
        myresult (first (:mean (benchmark-fn (testfn my-c5k) {})))]
    (/ result myresult)))

(defn implementation-speed-compare-with-99pc-eviction [testfn]
  (let [c5k (make-evictable-cache cache/ttl-cache-factory 4950 5000)
        result (first (:mean (benchmark-fn (testfn c5k) {})))
        my-c5k (make-evictable-cache mycache/ttl-cache-factory 4950 5000)
        myresult (first (:mean (benchmark-fn (testfn my-c5k) {})))]
    (/ result myresult)))

(defn complexity-compare-map [testfn]
  (let [result1k (first (:mean (benchmark-fn (testfn m1k) {})))
        result5k (first (:mean (benchmark-fn (testfn m5k) {})))]
    (/ result5k result1k)))

(deftest comparative-performance
  (testing "How well my TTLCache implementation performs against the standard core.cache one"
    (testing "Insertion and expiry"
    (let [factor (implementation-speed-compare #(cache/miss % :c 42))]
      (println "With 0 out of 5,000 cache items being expired, adding an item to my TTLCache is " factor " times faster than the core.cache one")
      (is (> factor 100)))
    (let [factor (implementation-speed-compare-with-1pc-eviction #(cache/miss % :c 42))]
      (println "With 50 out of 5,000 cache items being expired, adding an item to my TTLCache is " factor " times faster than the core.cache one")
      (is (> factor 3)))

    (let [factor (implementation-speed-compare-with-5pc-eviction #(cache/miss % :c 42))]
      (println "With 250 out of 5,000 cache items being expired, adding an item to my TTLCache is " factor " times faster than the core.cache one")
      (is (> factor 0.5)))
    (let [factor (implementation-speed-compare-with-99pc-eviction #(cache/miss % :c 42))]
      (println "With 4950 out of 5,000 cache items being expired, adding an item to my TTLCache is " (/ 1 factor) " times SLOWER than the core.cache one")
      (is (> factor 0.01))))
    (testing "Lookup performance"
    (let [factor (implementation-speed-compare #(cache/lookup % 1))]
      (println "With a 5,000-entry cache, looking an item up in my TTLCache is " factor " times faster than the core.cache one")
      (is (> factor 0.95))))

    (testing "Manual eviction performance"
    (let [factor (implementation-speed-compare #(cache/evict % 1))]
      (println "With a 5,000-entry cache, removing an item from my TTLCache is " factor " times faster than the core.cache one")
      (is (> factor 1.2))))))

;; Cost of new insertion

(deftest new-insertion-complexity
  (testing "core.cache's TTLCache has O(n) insertion/expiry"
    (let [factor (complexity-compare #(cache/miss % :c 42))]
      (println "core.cache's TTLCache: adding an item to a cache with 5,000 entries takes " factor "longer than one with 1,000 entries")
      (is (> 6 factor 4))))
  (testing "My TTLCache has O(log n) insertion/expiry"
    (let [factor (my-complexity-compare #(cache/miss % :c 42))]
      (println "My TTLCache: adding an item to a cache with 5,000 entries takes " factor "longer than one with 1,000 entries")
      (is (< factor 1.3)))))


;; Cost of retrieval

(deftest retrieval-complexity
  (testing "Both implementations have O(log n) or better lookup"
    (let [factor (complexity-compare #(cache/lookup % 1))]
      (println "core.cache's TTLCache: finding an item in a cache with 5,000 entries takes " factor "longer than one with 1,000 entries")
      (is (< factor 1.3)))
    (let [factor (my-complexity-compare #(cache/lookup % 1))]
      (println "My TTLCache: finding an item in a cache with 5,000 entries takes " factor "longer than one with 1,000 entries")
      (is (< factor 1.3)))))



;; Cost of eviction
(deftest eviction-complexity
  (testing "Both implementations have O(log n) or better manual eviction"
    (let [factor (complexity-compare #(cache/evict % 1))]
      (println "core.cache TTLCache: removing an item from a cache with 5,000 entries takes " factor "longer than one with 1,000 entries")
      (is (< factor 1.3))))
  (testing "How much more time it takes to evict an item from in a cache of 5,000 items than from one of 1,000 items"
    (let [factor (my-complexity-compare #(cache/evict % 1))]
      (println "My TTLCache: removing an item from a cache with 5,000 entries takes " factor "longer than one with 1,000 entries")
      (is (< factor 1.3)))))






