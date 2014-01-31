(ns com.puppetlabs.test.mq
  (:import [org.apache.activemq ScheduledMessage]
           [org.apache.activemq.broker BrokerService])
  (:require [clamq.jms :as jms]
            [fs.core :as fs]
            [clojure.java.io :as io]
            [puppetlabs.trapperkeeper.testutils.logging :refer [with-log-output]])
  (:use [com.puppetlabs.mq]
        [clojure.test]))

;; FIXME: this originally came from puppetdb's com.puppetlabs.puppetdb.testutils
;; and should be migrated into a common dependency ASAP
(defmacro with-test-broker
  "Constructs and starts an embedded MQ, and evaluates `body` inside a
  `with-open` expression that takes care of connection cleanup and MQ
  tear-down.

  `name` - The name to use for the embedded MQ

  `conn-var` - Inside of `body`, the variable named `conn-var`
  contains an active connection to the embedded broker.

  Example:

      (with-test-broker \"my-broker\" the-connetion
        ;; Do something with the connection
        (prn the-connection))
  "
  [name conn-var & body]
  `(with-log-output broker-logs#
    (let [dir#                   (fs/absolute-path (fs/temp-dir))
          broker-name#           ~name
          conn-str#              (str "vm://" ~name)
          size-megs#              50
          ^BrokerService broker# (build-embedded-broker
                                   broker-name#
                                   dir#
                                   {:store-usage size-megs#
                                    :temp-usage  size-megs#})]

       (.setUseJmx broker# false)
       (.setPersistent broker# false)
       (start-broker! broker#)

       (try
         (with-open [~conn-var (connect! conn-str#)]
           ~@body)
         (finally
           (stop-broker! broker#)
           (fs/delete-dir dir#))))))

;; FIXME: this originally came from puppetdb's com.puppetlabs.puppetdb.fixtures
;; and should be migrated into a common dependency ASAP
(defn- with-test-logging-silenced
  "A fixture that temporarily redirects all log output to an atom encapsulated
  within the scope of this fixture, rather than the typical ConsoleAppender.
  Useful for tests that intentionally trigger error conditions, to prevent
  cluttering up the test output with spurious log messages."
  [f]
  (with-log-output _
    (f)))

(use-fixtures :each with-test-logging-silenced)

(deftest delay-calc
  (testing "calculation of message delays"
    (testing "should handle unit conversion"
      (is (= {ScheduledMessage/AMQ_SCHEDULED_DELAY "7200000"} (delay-property 7200000)))
      (is (= {ScheduledMessage/AMQ_SCHEDULED_DELAY "7200000"} (delay-property 7200 :seconds)))
      (is (= {ScheduledMessage/AMQ_SCHEDULED_DELAY "7200000"} (delay-property 120 :minutes)))
      (is (= {ScheduledMessage/AMQ_SCHEDULED_DELAY "7200000"} (delay-property 2 :hours)))
      (is (thrown? IllegalArgumentException (delay-property 123 :foobar))))))

(deftest embedded-broker
  (testing "embedded broker"

    (testing "should give out what it takes in"
      (let [tracer-msg "This is a test message"]
        (with-test-broker "test" conn
          (connect-and-publish! conn "queue" tracer-msg)
          (is (= [tracer-msg] (bounded-drain-into-vec! conn "queue" 1))))))

    (testing "should respect delayed message sending properties"
      (let [tracer-msg "This is a test message"]
        (with-test-broker "test" conn
          (connect-and-publish! conn "queue" tracer-msg (delay-property 3 :seconds))
          ;; After 500ms, there should be nothing in the queue
          (is (= [] (timed-drain-into-vec! conn "queue" 500)))
          (Thread/sleep 500)
          ;; Within another 3s, we should see the message, we leave some
          ;; give or take here to componsate for the fact that the MQ
          ;; scheduler resolves delays to a second tick boundary (ignoring
          ;; or diluting milliseconds) so may appear to take almost 4 seconds
          ;; sometimes. This is to avoid potential races in the test.
          (is (= [tracer-msg] (timed-drain-into-vec! conn "queue" 3000))))))))

(deftest corrupt-kahadb-journal
  (testing "corrupt kahadb journal handling"
    (testing "corruption should return exception"
      ;; We are capturing the previous known failure here, just in case in the
      ;; future ActiveMQ changes behaviour (hopefully fixing this problem) so
      ;; we can make a decision about weither capturing EOFException and
      ;; restarting the broker ourselves is still needed.
      ;;
      ;; Upstream bug is: https://issues.apache.org/jira/browse/AMQ-4339
      (let [dir          (fs/absolute-path (fs/temp-dir))
            broker-name  "test"]
        (try
          ;; Start and stop a broker, then corrupt the journal
          (let [broker (build-embedded-broker broker-name dir)]
            (start-broker! broker)
            (stop-broker! broker)
            (spit (fs/file dir "test" "KahaDB" "db-1.log") "asdf"))
          ;; Upon next open, we should get an EOFException
          (let [broker (build-embedded-broker broker-name dir)]
            (is (thrown? java.io.EOFException (start-broker! broker))))
          ;; Now lets clean up
          (finally
            (fs/delete-dir dir)))))
    (testing "build-and-start-broker! should ignore the corruption"
      ;; Current work-around is to restart the broker upon this kind of
      ;; corruption. This test makes sure this continues to work for the
      ;; lifetime of this code.
      (let [dir         (fs/absolute-path (fs/temp-dir))
            broker-name "test"]
        (try
          ;; Start and stop a broker, then corrupt the journal
          (let [broker (build-embedded-broker broker-name dir)]
            (start-broker! broker)
            (stop-broker! broker)
            (spit (fs/file dir "test" "KahaDB" "db-1.log") "asdf"))
          ;; Now lets use the more resilient build-and-start-broker!
          (let [broker (build-and-start-broker! broker-name dir {})]
            (stop-broker! broker))
          ;; Now lets clean up
          (finally
            (fs/delete-dir dir)))))))

(deftest test-build-broker
  (testing "build-embedded-broker"
    (let [broker (build-embedded-broker "somedir")]
      (is (instance? BrokerService broker)))
    (let [broker (build-embedded-broker "localhost" "somedir")]
      (is (instance? BrokerService broker)))
    (let [broker (build-embedded-broker "localhost" "somedir" {})]
      (is (instance? BrokerService broker)))
    (let [size-megs 50
          size-bytes (* size-megs 1024 1024)

          broker (build-embedded-broker "localhost" "somedir"
                      {:store-usage size-megs
                       :temp-usage  size-megs})]
      (is (instance? BrokerService broker))
      (is (.. broker (getPersistenceAdapter) (isIgnoreMissingJournalfiles)))
      (is (.. broker (getPersistenceAdapter) (isArchiveCorruptedIndex)))
      (is (.. broker (getPersistenceAdapter) (isCheckForCorruptJournalFiles)))
      (is (.. broker (getPersistenceAdapter) (isChecksumJournalFiles)))
      (is (= size-bytes (.. broker (getSystemUsage) (getStoreUsage) (getLimit))))
      (is (= size-bytes (.. broker (getSystemUsage) (getTempUsage) (getLimit)))))))

(deftest json-publish
  (testing "publish-json!"
    (testing "should fail when handed objects that can't be serialized"
      (with-test-broker "test" conn
        (is (thrown? AssertionError (publish-json! conn "queue" conn)))))

    (testing "should published a serialized version of the object"
      (with-test-broker "test" conn
        (publish-json! conn "queue" "foo")
        (is (= ["\"foo\""] (bounded-drain-into-vec! conn "queue" 1)))))))
