;; Largely based on the core.cache tests, which are:

;; Copyright (c) Rich Hickey. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

;; Other parts are Copyright 2014 Rob Day, and also released under the EPL.


(ns uk.me.rkd.ttlcache-test
  (:require [clojure.test :refer :all]
            [uk.me.rkd.ttlcache :refer [per-item-ttl-cache-factory ttl-cache-factory]]
            [clojure.data.priority-map :refer [priority-map]])
  (:import uk.me.rkd.ttlcache.PerItemTTLCache))

(defn do-dot-lookup-tests [c]
  (are [expect actual] (= expect actual)
       1 (.lookup c :a)
       2 (.lookup c :b)
       42 (.lookup c :c 42)
       nil (.lookup c :c)))

(defn do-ilookup-tests [c]
  (are [expect actual] (= expect actual)
       1 (:a c)
       2 (:b c)
       42 (:X c 42)
       nil (:X c)))

(defn do-assoc [c]
  (are [expect actual] (= expect actual)
       1 (:a (assoc c :a 1))
       nil (:a (assoc c :b 1))))

(defn do-dissoc [c]
  (are [expect actual] (= expect actual)
       2 (:b (dissoc c :a))
       nil (:a (dissoc c :a))
       nil (:b (-> c (dissoc :a) (dissoc :b)))
       0 (count (-> c (dissoc :a) (dissoc :b)))))

(defn do-getting [c]
  (are [actual expect] (= expect actual)
       (get c :a) 1
       (get c :e) nil
       (get c :e 0) 0
       (get c :b 0) 2
       (get c :f 0) nil

       (get-in c [:c :e]) 4
       (get-in c '(:c :e)) 4
       (get-in c [:c :x]) nil
       (get-in c [:f]) nil
       (get-in c [:g]) false
       (get-in c [:h]) nil
       (get-in c []) c
       (get-in c nil) c

       (get-in c [:c :e] 0) 4
       (get-in c '(:c :e) 0) 4
       (get-in c [:c :x] 0) 0
       (get-in c [:b] 0) 2
       (get-in c [:f] 0) nil
       (get-in c [:g] 0) false
       (get-in c [:h] 0) 0
       (get-in c [:x :y] {:y 1}) {:y 1}
       (get-in c [] 0) c
       (get-in c nil 0) c))

(defn do-finding [c]
  (are [expect actual] (= expect actual)
       (find c :a) [:a 1]
       (find c :b) [:b 2]
       (find c :c) nil
       (find c nil) nil))

(defn do-contains [c]
  (are [expect actual] (= expect actual)
       (contains? c :a) true
       (contains? c :b) true
       (contains? c :c) false
       (contains? c nil) false))


(def big-map {:a 1, :b 2, :c {:d 3, :e 4}, :f nil, :g false, nil {:h 5}})
(def small-map {:a 1 :b 2})

(defn sleepy [e t] (Thread/sleep t) e)

(deftest test-ttl-cache-ilookup
  (let [five-secs (+ 5000 (System/currentTimeMillis))
        big-time (into (priority-map) (for [[k _] big-map] [k five-secs]))
        small-time (into (priority-map) (for [[k _] small-map] [k five-secs]))]
    (testing "that the TTLCache can lookup via keywords"
      (do-ilookup-tests (PerItemTTLCache. small-map small-time (constantly 2000))))
    (testing "that the TTLCache can lookup via keywords"
      (do-dot-lookup-tests (PerItemTTLCache. small-map small-time (constantly 2000))))
    (testing "assoc and dissoc for TTLCache"
      (do-assoc (PerItemTTLCache. {} (priority-map) (constantly 2000)))
      (do-dissoc (PerItemTTLCache. {:a 1 :b 2} (priority-map :a five-secs :b five-secs) (constantly 2000))))
    (testing "that get and cascading gets work for TTLCache"
      (do-getting (PerItemTTLCache. big-map big-time (constantly 2000))))
    (testing "that finding works for TTLCache"
      (do-finding (PerItemTTLCache. small-map small-time (constantly 2000))))
    (testing "that contains? works for TTLCache"
      (do-contains (PerItemTTLCache. small-map small-time (constantly 2000))))))

(deftest test-ttl-cache
  (testing "TTL-ness with empty cache"
    (let [C (ttl-cache-factory {} :ttl 500)]
      (are [x y] (= x y)
           {:a 1, :b 2} (-> C (assoc :a 1) (assoc :b 2) .cache)
           {:c 3} (-> C (assoc :a 1) (assoc :b 2) (sleepy 700) (assoc :c 3) .cache))))
  (testing "TTL cache does not return a value that has expired."
    (let [C (ttl-cache-factory {} :ttl 500)]
      (is (nil? (-> C (assoc :a 1) (sleepy 700) (. lookup :a)))))))

(deftest test-per-item-ttl-cache
  (testing "TTL-ness with empty cache"
    (let [C (per-item-ttl-cache-factory {} :ttl-getter (fn [key value] (:ttl value)))]
      (are [x y] (= x y)
           {:a {:val 1, :ttl 500} :b {:val 2, :ttl 500}} (-> C (assoc :a {:val 1, :ttl 500}) (assoc :b {:val 2, :ttl 500}) .cache)
           {:b {:val 2, :ttl 1000}, :c {:val 3, :ttl 100}} (-> C
                                                               (assoc :a {:val 1, :ttl 500})
                                                               (assoc :b {:val 2, :ttl 1000})
                                                               (sleepy 700)
                                                               (assoc :c {:val 3, :ttl 100})
                                                               .cache))))
  (testing "TTL cache does not return a value that has expired."
    (let [C (per-item-ttl-cache-factory {} :ttl-getter (fn [key value] (:ttl value)))]
      (is (nil? (-> C
                    (assoc :a {:val 1 :ttl 500})
                    (sleepy 700)
                    (. lookup :a)))))))

