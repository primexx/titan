package com.thinkaurelius.titan.diskstorage.infinispan;

import com.thinkaurelius.titan.diskstorage.StorageException;
import com.thinkaurelius.titan.diskstorage.common.LocalStoreManager;
import com.thinkaurelius.titan.diskstorage.common.NoOpStoreTransaction;
import com.thinkaurelius.titan.diskstorage.keycolumnvalue.StoreFeatures;
import com.thinkaurelius.titan.diskstorage.keycolumnvalue.StoreTransaction;
import com.thinkaurelius.titan.diskstorage.keycolumnvalue.StoreTxConfig;
import com.thinkaurelius.titan.diskstorage.keycolumnvalue.keyvalue.CacheStore;
import com.thinkaurelius.titan.diskstorage.keycolumnvalue.keyvalue.CacheStoreManager;
import com.thinkaurelius.titan.diskstorage.util.FileStorageConfiguration;

import org.apache.commons.configuration.Configuration;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.transaction.lookup.DummyTransactionManagerLookup;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */

public class InfinispanCacheStoreManager extends LocalStoreManager implements CacheStoreManager {

    protected final FileStorageConfiguration storageConfig;
    protected final StoreFeatures features = getDefaultFeatures();
    private final EmbeddedCacheManager manager;

    private final Map<String, InfinispanCacheStore> stores = new HashMap<String, InfinispanCacheStore>();

    public InfinispanCacheStoreManager(Configuration config) throws StorageException {
        super(config);
        storageConfig = new FileStorageConfiguration(directory);
        GlobalConfiguration gcfg = new GlobalConfigurationBuilder().globalJmxStatistics().allowDuplicateDomains(true).build();
        manager = new DefaultCacheManager(gcfg);
    }


    @Override
    public synchronized CacheStore openDatabase(final String name) throws StorageException {
        if (stores.containsKey(name)) {
            return stores.get(name);
        }

        ConfigurationBuilder cb = new ConfigurationBuilder();
        org.infinispan.configuration.cache.Configuration conf;
        if (transactional) {
            conf = cb.transaction().transactionMode(TransactionMode.TRANSACTIONAL)
                    .autoCommit(false).transactionManagerLookup(new DummyTransactionManagerLookup())
                    .build();
        } else {
            conf = cb.transaction().transactionMode(TransactionMode.NON_TRANSACTIONAL).build();
        }
        manager.defineConfiguration(name,conf);
        InfinispanCacheStore newStore = new InfinispanCacheStore(name, manager);
        stores.put(name, newStore);

        return newStore;
    }

    @Override
    public void clearStorage() throws StorageException {
        
        for (String storeName : manager.getCacheNames()) {
            Cache<Object, Object> store = manager.getCache(storeName);
            store.clear();
        }
        // close();
    }

    @Override
    public String getName() {
        return toString();
    }


    @Override
    public StoreTransaction beginTransaction(StoreTxConfig config) throws StorageException {
        if (transactional) {
//            TransactionManager tm = manager.getGlobalComponentRegistry().getComponent(TransactionManager.class);
//            http://infinispan.org/docs/6.0.x/getting_started/getting_started.html#_cache_with_transaction_management
            throw new UnsupportedOperationException();
        } else {
            return new NoOpStoreTransaction(config);
        }
    }

    @Override
    public void close() throws StorageException {
        manager.stop(); // Stops all of the manager's caches
    }

    @Override
    public StoreFeatures getFeatures() {
        return features;
    }

    @Override
    public String getConfigurationProperty(String key) throws StorageException {
        return storageConfig.getConfigurationProperty(key);
    }

    @Override
    public void setConfigurationProperty(String key, String value) throws StorageException {
        storageConfig.setConfigurationProperty(key, value);
    }

    private StoreFeatures getDefaultFeatures() {
        StoreFeatures features = new StoreFeatures();

        features.supportsOrderedScan = false;
        features.supportsUnorderedScan = true;
        features.supportsBatchMutation = false;
        features.supportsMultiQuery = false;

        //TODO: support transactions for Infinispan
        features.supportsTxIsolation = false;
        features.supportsConsistentKeyOperations = true;
        features.supportsLocking = false;

        features.isDistributed = false;
        features.isKeyOrdered = false;
        features.hasLocalKeyPartition = false;

        return features;
    }

}