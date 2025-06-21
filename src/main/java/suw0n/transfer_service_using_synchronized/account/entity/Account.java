package suw0n.transfer_service_using_synchronized.account.entity;

import java.time.LocalDateTime;

public record Account(Long balance, LocalDateTime updatedAt) {
    public static Account of(final Long balance) {
        return new Account(balance, LocalDateTime.now());
    }
}
