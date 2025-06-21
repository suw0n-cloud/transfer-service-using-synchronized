package suw0n.transfer_service_using_synchronized.account.usecase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SafeTransferAdapter {

    private final TransferUseCase useCase;
    private final Map<String, Object> accountLocks = new ConcurrentHashMap<>();

    public SafeTransferAdapter(final TransferUseCase useCase) {
        this.useCase = useCase;
    }

    private Object getLock(final String id) {
        return accountLocks.computeIfAbsent(id, lock -> new Object());
    }

    public void execute(final Long amount, final String senderId, final String receiverId) {
        final String firstId = senderId.compareTo(receiverId) < 0 ? senderId : receiverId;
        final String secondId = senderId.compareTo(receiverId) < 0 ? receiverId : senderId;
        synchronized(getLock(firstId)) {
            synchronized(getLock(secondId)) {
                useCase.execute(amount, senderId, receiverId);
            }
        }
    }

}
