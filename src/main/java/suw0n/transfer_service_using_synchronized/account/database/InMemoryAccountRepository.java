package suw0n.transfer_service_using_synchronized.account.database;

import suw0n.transfer_service_using_synchronized.account.entity.Account;

import java.util.HashMap;
import java.util.Map;

public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> database = new HashMap<>();

    @Override
    public Account findById(final String id) {
        return database.get(id);
    }

    @Override
    public void save(final String id, final Long balance) {
        if(database.containsKey(id)) {
            database.replace(id, Account.of(balance));
        } else {
            database.put(id, Account.of(balance));
        }
    }

}
