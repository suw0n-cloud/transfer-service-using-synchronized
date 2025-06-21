package suw0n.transfer_service_using_synchronized.account.usecase;

import suw0n.transfer_service_using_synchronized.account.database.AccountRepository;

public class TransferUseCase {

    private final AccountRepository repository;

    public TransferUseCase(final AccountRepository repository) {
        this.repository = repository;
    }

    public void execute(final Long amount, final String senderId, final String receiverId) {
        final Long balanceOfSender = repository.findById(senderId).balance();
        final Long balanceOfReceiver = repository.findById(receiverId).balance();
        if(balanceOfSender < amount) {
            throw new RuntimeException("Sender can't afford " + amount + ".");
        }
        repository.save(senderId, balanceOfSender - amount);
        repository.save(receiverId, balanceOfReceiver + amount);
        System.out.println(senderId + " : " + (balanceOfReceiver - amount) + ", receiver : " + (balanceOfSender + amount));
    }

}
