package suw0n.transfer_service_using_synchronized.account.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import suw0n.transfer_service_using_synchronized.account.database.AccountRepository;
import suw0n.transfer_service_using_synchronized.account.database.InMemoryAccountRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TransferUseCaseConcurrencyTest {

    TransferUseCase useCase;
    AccountRepository accountRepository;
    static int THREAD_COUNT = 1000;

    @BeforeEach
    void beforeEach() {
        accountRepository = new InMemoryAccountRepository();
        useCase = new TransferUseCase(accountRepository);

        accountRepository.save("samsung", 1000L);
        accountRepository.save("lotte", 1000L);
    }

    @Test
    @DisplayName("성공 테스트")
    void successTest() throws InterruptedException {
        SafeTransferAdapter adapter = new SafeTransferAdapter(useCase);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i<THREAD_COUNT; i++) {
            int finalI = i;
            executor.submit(() -> {
                if (finalI % 2 == 0) {
                    adapter.execute(10L, "samsung", "lotte");
                    latch.countDown();
                } else {
                    adapter.execute(10L, "lotte", "samsung");
                    latch.countDown();
                }
            });
        }

        boolean isSafe = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        long totalBalance = accountRepository.findById("samsung").balance() + accountRepository.findById("lotte").balance();

        assertTrue(isSafe);
        assertEquals(2000L, totalBalance);
    }

    @Test
    @DisplayName("동시성을 고려하지 않은 실패 테스트")
    void failTestByNoLock() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i<THREAD_COUNT; i++) {
            int finalI = i;
            executor.submit(() -> {
                if (finalI % 2 == 0) {
                    useCase.execute(10L, "samsung", "lotte");
                    latch.countDown();
                } else {
                    useCase.execute(10L, "lotte", "samsung");
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        long totalBalance = accountRepository.findById("samsung").balance() + accountRepository.findById("lotte").balance();

        assertNotEquals(2000L, totalBalance);
    }

    @Test
    @DisplayName("데드락을 고려하지 않은 실패 테스트")
    void failTestByDeadlock() throws InterruptedException {
        DeadlockTransferAdapter adapter = new DeadlockTransferAdapter(useCase);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i<THREAD_COUNT; i++) {
            int finalI = i;
            executor.submit(() -> {
                if (finalI % 2 == 0) {
                    adapter.execute(10L, "samsung", "lotte");
                    latch.countDown();
                } else {
                    adapter.execute(10L, "lotte", "samsung");
                    latch.countDown();
                }
            });
        }

        boolean isSafe = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertFalse(isSafe);
    }

}
