package suw0n.transfer_service_using_synchronized.account.usecase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DeadlockTransferAdapter {

    private final TransferUseCase useCase;
    private final Map<String, Object> accountLocks = new ConcurrentHashMap<>();

    public DeadlockTransferAdapter(final TransferUseCase useCase) {
        this.useCase = useCase;
    }

    private Object getLock(final String id) {
        return accountLocks.computeIfAbsent(id, lock -> new Object());
    }

    public void execute(final Long amount, final String senderId, final String receiverId) {
        synchronized(getLock(senderId)) {
            synchronized(getLock(receiverId)) {
                useCase.execute(amount, senderId, receiverId);
            }
        }
    }

}
