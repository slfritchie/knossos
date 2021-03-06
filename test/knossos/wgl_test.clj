(ns knossos.wgl-test
  (:require [clojure.test :refer :all]
            [knossos.wgl :refer :all]
            [knossos.op :as op]
            [knossos.model :refer :all]
            [knossos.core-test :as ct]
            [clojure.pprint :refer [pprint]]))

(deftest crash-test
  (let [model (register 0)
        history [(op/invoke 0 :write 1)
                 (op/info   0 :write 1)]
        a (analysis model history)]
    (is (:valid? a))))

(deftest sequential-test
  (let [a (analysis (register 0)
                    [(op/invoke 0 :read 0)
                     (op/ok     0 :read 0)])]
    (is (:valid? a))))

(deftest read-wrong-initial-value-test
  (let [model (register 0)
        history [{:process 0 :type :invoke :f :read :value 1}
                 {:process 0 :type :ok     :f :read :value 1}]
        a       (analysis model history)]
    (is (= {:valid?       false
            :op           {:process 0 :type :ok :f :read :value 1}
            :previous-ok  nil
            :final-paths
            #{[{:model model
                :op nil}
               {:model (inconsistent "0≠1")
                :op {:f :read, :process 0 :type :ok :value 1}}]}}

           a))))

(deftest bad-analysis-test
  (let [a (analysis
            (cas-register 0)
            (ct/read-history-2 "data/cas-register/bad/bad-analysis.edn"))]
    (is (= false (:valid? a)))
    (is (= {:process 21, :type :ok, :f :read, :value 2} (:op a)))
    (is (= {:process 19, :type :ok, :f :read, :value 0}
           (:previous-ok a)))))

(deftest bad-analysis-test-2
  (let [a (analysis (cas-register 0)
                   (ct/read-history-2 "data/cas-register/bad/cas-failure.edn"))]
    ; In this particular history, we know the value is 0, then we have
    ; concurrent reads of 0 and a write of 2 by process 76, followed by another
    ; read of 0 by process 70. The only legal linearization to that final read
    ; is all reads of 0, followed by 76 write 2, which leaves the state as 2.
    ; Process 70 read 0 should be the invalidating op.
    (is (= false (:valid? a)))

    ; We fail because we can't linearize the final read of 0
    (is (= {:process 70, :type :ok, :f :read, :value 0}
           (:op a)))

     ; The last linearized ok was the completion of process 74's read of 0, but
     ; that's not the last linearized *op*: that'd be the write of 2.
     (is (= {:process 74, :type :ok, :f :read, :value 0}
            (:previous-ok a)))

     (is (= #{[{:model (cas-register 2)
                :op {:f :write :process 76 :type :ok :value 2}}
               {:model (inconsistent "can't CAS 2 from 1 to 1")
                :op {:f :cas :process 77 :type :info :value [1 1]}}]
              [{:model (cas-register 2)
                :op {:f :write :process 76 :type :ok :value 2}}
               {:model (inconsistent "can't read 0 from register 2")
                :op {:f :read :process 70 :type :ok :value 0}}]}
           (:final-paths a)))))

(deftest rethink-fail-minimal-test
  (let [a (analysis (cas-register nil)
                    (ct/read-history-2 "data/cas-register/bad/rethink-fail-minimal.edn"))]
    (is (= false (:valid? a)))

    ; We shouldn't be able to linearize the read of 3 by process 12, which
    ; appears out of nowhere.
    (is (= {:process 1, :type :ok, :f :read, :value 3}
           (:op a)))))

(deftest rethink-fail-smaller-test
  (let [a (analysis (cas-register nil)
                    (ct/read-history-2 "data/cas-register/bad/rethink-fail-smaller.edn"))]
    (is (= false (:valid? a)))

    ; We shouldn't be able to linearize the read of 3 by process 12, which
    ; appears out of nowhere.
    (is (= {:process 12, :type :ok, :f :read, :value 3, :time 106334571856}
           (:op a)))))

(deftest cas-failure-test
  (let [a (analysis (cas-register 0)
                    (ct/read-history-2 "data/cas-register/bad/cas-failure.edn"))]
    (is (= false (:valid? a)))
    (is (= {:f :read :process 70 :type :ok :value 0}
           (:op a)))))

(deftest volatile-linearizable-test
  (dotimes [i 10]
    (let [history (ct/volatile-history 100 50 1/1000)
          a       (analysis (register 0) history)]
      (is (:valid? a)))))

(deftest examples-test
  (ct/test-examples analysis))
