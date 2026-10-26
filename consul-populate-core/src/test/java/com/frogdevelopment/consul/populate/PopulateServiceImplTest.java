package com.frogdevelopment.consul.populate;

import static com.frogdevelopment.consul.populate.PopulateServiceImpl.MAX_OPERATIONS_PER_TXN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.consul.populate.config.GlobalProperties;

import io.vertx.core.Future;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.TxnError;
import io.vertx.ext.consul.TxnRequest;
import io.vertx.ext.consul.TxnResponse;

@ExtendWith(MockitoExtension.class)
class PopulateServiceImplTest {

    @InjectMocks
    private PopulateServiceImpl populateService;

    @Mock
    private ConsulClient consulClient;
    @Mock
    private GlobalProperties globalProperties;
    @Mock
    private DataImporter dataImporter;
    @Mock
    private TxnResponse txnResponse;

    @Captor
    private ArgumentCaptor<TxnRequest> txnRequestCaptor;

    @BeforeEach
    void setUp() {
        var kv = new GlobalProperties.KV();
        // lenient: some tests fail before Consul's keys are read
        lenient().when(globalProperties.getKv()).thenReturn(kv);
        given(consulClient.leaderStatus()).willReturn(Future.succeededFuture("leader"));
        lenient().when(consulClient.getKeys(any())).thenReturn(Future.succeededFuture(List.of()));
        lenient().when(consulClient.transaction(any())).thenReturn(Future.succeededFuture(txnResponse));
        lenient().when(txnResponse.getResultsSize()).thenReturn(0);
        lenient().when(txnResponse.getErrorsSize()).thenReturn(0);
    }

    @Test
    void should_send_single_transaction_when_operations_fit_in_one_batch() {
        // given
        given(dataImporter.execute()).willReturn(entriesOfSize(10));

        // when
        populateService.populate();

        // then
        verify(consulClient, times(1)).transaction(txnRequestCaptor.capture());
        assertThat(txnRequestCaptor.getValue().getOperationsSize()).isEqualTo(10);
    }

    @Test
    void should_split_into_multiple_transactions_when_operations_exceed_limit() {
        // given — 65 keys → 2 transactions (64 + 1)
        given(dataImporter.execute()).willReturn(entriesOfSize(65));

        // when
        populateService.populate();

        // then
        verify(consulClient, times(2)).transaction(txnRequestCaptor.capture());
        var batches = txnRequestCaptor.getAllValues();
        assertThat(batches.get(0).getOperationsSize()).isEqualTo(MAX_OPERATIONS_PER_TXN);
        assertThat(batches.get(1).getOperationsSize()).isEqualTo(1);
    }

    @Test
    void should_split_into_exactly_two_full_batches_when_operations_equal_twice_limit() {
        // given — 128 keys → 2 transactions of exactly 64
        given(dataImporter.execute()).willReturn(entriesOfSize(128));

        // when
        populateService.populate();

        // then
        verify(consulClient, times(2)).transaction(txnRequestCaptor.capture());
        txnRequestCaptor.getAllValues()
                .forEach(req -> assertThat(req.getOperationsSize()).isEqualTo(MAX_OPERATIONS_PER_TXN));
    }

    @Test
    void should_include_delete_operations_for_keys_removed_from_source() {
        // given — 1 new key, 1 existing key no longer in source → 1 SET + 1 DELETE = 2 ops
        given(dataImporter.execute()).willReturn(Map.of("new-key", "value"));
        given(consulClient.getKeys(any())).willReturn(Future.succeededFuture(List.of("config/new-key", "config/old-key")));

        // when
        populateService.populate();

        // then
        verify(consulClient, times(1)).transaction(txnRequestCaptor.capture());
        assertThat(txnRequestCaptor.getValue().getOperationsSize()).isEqualTo(2);
    }

    @Test
    void should_send_no_transaction_when_there_is_nothing_to_import_and_nothing_to_delete() {
        // given
        given(dataImporter.execute()).willReturn(Map.of());

        // when
        populateService.populate();

        // then
        verify(consulClient, times(0)).transaction(any());
    }

    @Test
    void should_read_everything_but_send_no_transaction_when_dry_run() {
        // given - 70 keys to set and 1 stale key to delete would need 2 transactions
        given(dataImporter.execute()).willReturn(entriesOfSize(70));
        given(consulClient.getKeys(any())).willReturn(Future.succeededFuture(List.of("config/stale-key")));

        // when
        populateService.populate(true);

        // then
        verify(consulClient).leaderStatus();
        verify(consulClient).getKeys(any());
        verify(consulClient, never()).transaction(any());
    }

    @Test
    void should_fail_dry_run_when_the_data_cannot_be_imported() {
        // given
        given(dataImporter.execute()).willThrow(new IllegalArgumentException("Root directory does not exist: /nowhere"));

        // when
        var caught = catchThrowable(() -> populateService.populate(true));

        // then
        assertThat(caught)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Root directory does not exist: /nowhere");
        verify(consulClient, never()).transaction(any());
    }

    @Test
    void should_fail_dry_run_when_consul_is_not_reachable() {
        // given
        given(consulClient.leaderStatus()).willReturn(Future.failedFuture("connection refused"));

        // when
        var caught = catchThrowable(() -> populateService.populate(true));

        // then
        assertThat(caught)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Consul is not reachable");
        verify(consulClient, never()).transaction(any());
    }

    @Test
    void should_stop_and_throw_when_a_transaction_reports_errors() {
        // given — 65 keys → 2 transactions, the first one fails
        given(dataImporter.execute()).willReturn(entriesOfSize(65));
        var txnError = new TxnError().setWhat("boom");
        given(txnResponse.getErrorsSize()).willReturn(1);
        given(txnResponse.getErrors()).willReturn(List.of(txnError));

        // when
        var caught = catchThrowable(() -> populateService.populate());

        // then
        assertThat(caught)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transaction 1/2 failed")
                .hasMessageContaining("boom");
        verify(consulClient, times(1)).transaction(any());
    }

    private static Map<String, String> entriesOfSize(int count) {
        return IntStream.range(0, count)
                .boxed()
                .collect(Collectors.toMap(i -> "key-" + i, i -> "value-" + i));
    }
}
