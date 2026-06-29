package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SmsProviderResolver {
    private final Map<SmsProviderType, SmsProviderClient> clientsByType;

    public SmsProviderResolver(List<SmsProviderClient> clients) {
        EnumMap<SmsProviderType, SmsProviderClient> resolved = new EnumMap<>(SmsProviderType.class);
        for (SmsProviderClient client : clients) {
            SmsProviderClient previous = resolved.putIfAbsent(client.providerType(), client);
            if (previous != null) {
                throw new IllegalStateException("Duplicate SMS provider client for " + client.providerType());
            }
        }
        this.clientsByType = Map.copyOf(resolved);
    }

    /**
     * The SMS provider types that have a registered {@link SmsProviderClient}. Used to validate the
     * configured default provider at startup and to drive the queue worker per provider.
     */
    public Set<SmsProviderType> registeredProviderTypes() {
        return clientsByType.keySet();
    }

    public SmsProviderClient resolve(SmsProviderType providerType) {
        SmsProviderType requestedType = providerType == null ? SmsProviderType.STUB : providerType;
        SmsProviderClient client = clientsByType.get(requestedType);
        if (client == null) {
            throw new IllegalStateException("No SMS provider client registered for " + requestedType);
        }
        return client;
    }
}
