package com.frogdevelopment.consul.populate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.vertx.ext.consul.TxnKVOperation;
import io.vertx.ext.consul.TxnKVVerb;
import io.vertx.ext.consul.TxnOperation;
import io.vertx.ext.consul.TxnRequest;

/**
 * Consul rejects a transaction whose body is above {@code txn_max_req_len} (413, checked against Consul 1.22: 524288
 * bytes are accepted, 524292 are not), so the split must be exact, not an estimate.
 */
class TransactionSplittingTest {

    @Test
    void should_use_exactly_the_size_limit_to_split() {
        // given
        final var operations = operations(5);
        final var sizeOfAll = PopulateServiceImpl.bodySize(PopulateServiceImpl.toTransactions(operations, 1_000_000).get(0));

        // when / then - the body of all the operations fits in a limit of exactly its size, and not in one byte less
        assertThat(PopulateServiceImpl.toTransactions(operations, sizeOfAll)).hasSize(1);
        assertThat(PopulateServiceImpl.toTransactions(operations, sizeOfAll - 1)).hasSize(2);
    }

    @Test
    void should_split_at_64_operations_when_the_size_is_not_the_constraint() {
        // when
        final var transactions = PopulateServiceImpl.toTransactions(operations(130), 1_000_000);

        // then
        assertThat(transactions).extracting(TxnRequest::getOperationsSize).containsExactly(64, 64, 2);
    }

    @Test
    void should_keep_every_operation_in_order() {
        // when
        final var transactions = PopulateServiceImpl.toTransactions(operations(200), 3_000);

        // then - many small transactions, none lost, none moved
        assertThat(transactions.size()).isGreaterThan(3);
        final var keys = transactions.stream()
                .flatMap(transaction -> transaction.getOperations().stream())
                .map(operation -> ((TxnKVOperation) operation).getKey())
                .toList();
        assertThat(keys).isEqualTo(operations(200).stream().map(operation -> ((TxnKVOperation) operation).getKey()).toList());
    }

    private static List<TxnOperation> operations(final int count) {
        return IntStream.range(0, count)
                .<TxnOperation>mapToObj(i -> new TxnKVOperation()
                        .setKey("config/key-%03d".formatted(i))
                        .setValue("value-" + i)
                        .setType(TxnKVVerb.SET))
                .toList();
    }
}
