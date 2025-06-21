package suw0n.transfer_service_using_synchronized.account.database;

import suw0n.transfer_service_using_synchronized.account.entity.Account;

public interface AccountRepository {

    Account findById(String id);

    void save(String id, Long balance);

}
