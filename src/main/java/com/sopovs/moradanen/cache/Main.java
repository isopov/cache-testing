package com.sopovs.moradanen.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import org.cache2k.Cache2kBuilder;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.spi.loaderwriter.CacheLoaderWriter;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.eviction.EvictionType;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

import java.io.IOException;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import static org.ehcache.config.builders.CacheConfigurationBuilder.newCacheConfigurationBuilder;
import static org.ehcache.config.builders.CacheManagerBuilder.newCacheManagerBuilder;
import static org.ehcache.config.builders.ResourcePoolsBuilder.heap;

public class Main {
    private static final int CACHE_SIZE = 150;

    public static void main(String[] args) {
        for (int rem : new int[]{101, 55, 49, 31, 23, 17, 11, 9, 8, 7, 6, 5, 4, 3, 2, 1})
            System.out.println(bestPossible(rem) + ", " + caffeine(rem) + ", " + guava(rem) + ", " + ehcache(rem) + ", " + cache2k(rem) + ", " + infinispan(rem));

    }

    private static final int INF_REPS = 10;

    private static long infinispan(int rem) {
        long result = 0;
        for (int i = 0; i < INF_REPS; i++) {
            ValueSupplier supplier = new ValueSupplier();
            try (EmbeddedCacheManager m = new DefaultCacheManager()) {
                ConfigurationBuilder cb = new ConfigurationBuilder();
                cb.memory().evictionType(EvictionType.COUNT).size(CACHE_SIZE);


                m.defineConfiguration("foo", cb.build());
                org.infinispan.Cache<Integer, Object> cache = m.getCache("foo");

                useSource(key -> cache.computeIfAbsent(key, supplier::apply), rem);
                if (cache.size() > CACHE_SIZE) {
                    //Since Infinispan is currently the best, lets check it at least this way
                    throw new IllegalStateException("Infinispan cheats with cache size of " + cache.size());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            result += supplier.counter.sum();
        }
        return result / INF_REPS;
    }


    private static long cache2k(int rem) {
        ValueSupplier supplier = new ValueSupplier();
        org.cache2k.Cache<Integer, Object> cache = new Cache2kBuilder<Integer, Object>() {
        }
                .entryCapacity(CACHE_SIZE)
                .loader(supplier::apply)
                .build();
        useSource(cache::get, rem);
        return supplier.counter.sum();
    }

    private static long guava(int rem) {
        ValueSupplier supplier = new ValueSupplier();
        com.google.common.cache.LoadingCache<Integer, Object> cacheSource = CacheBuilder.newBuilder()
                .maximumSize(CACHE_SIZE)

                .build(new CacheLoader<>() {
                    @Override
                    public Object load(Integer in) {
                        return supplier.apply(in);
                    }
                });
        useSource(cacheSource, rem);
        return supplier.counter.sum();
    }

    private static final int CAF_REPS = 10;

    private static long caffeine(int rem) {
        long result = 0;
        for (int i = 0; i < CAF_REPS; i++) {
            ValueSupplier supplier = new ValueSupplier();
            LoadingCache<Integer, Object> cacheSource = Caffeine.newBuilder()
                    .maximumSize(CACHE_SIZE)
                    .build(supplier::apply);
            useSource(cacheSource::get, rem);
            result += supplier.counter.sum();
        }
        return result / CAF_REPS;

    }

    private static final int EHCACHE_REPS = 10;

    private static long ehcache(int rem) {
        long result = 0;
        for (int i = 0; i < EHCACHE_REPS; i++) {

            ValueSupplier supplier = new ValueSupplier();
            try (CacheManager cacheManager = newCacheManagerBuilder()
                    .withCache("foo", newCacheConfigurationBuilder(Integer.class, Object.class, heap(CACHE_SIZE))
                            .withLoaderWriter(new CacheLoaderWriter<>() {
                                @Override
                                public Object load(Integer key) {
                                    return supplier.apply(key);
                                }

                                @Override
                                public void write(Integer key, Object value) {

                                }

                                @Override
                                public void delete(Integer key) {

                                }
                            }))
                    .build(true)) {

                Cache<Integer, Object> cache = cacheManager.getCache("foo", Integer.class, Object.class);
                useSource(cache::get, rem);
            }
            result += supplier.counter.sum();
        }

        return result / EHCACHE_REPS;
    }

    private static long bestPossible(int rem) {
        int result = 10099;
        for (int i = 0; i < 10_000; i++) {
            for (int j = 1; j < 101; j++) {
                if (j % rem == 0) {
                    result++;
                }
            }
        }
        return result;
    }

    private static void useSource(Function<Integer, Object> source, int rem) {
        int outliers = 20_000;
        for (int i = 0; i < 10_000; i++) {
            for (int j = 1; j < 101; j++) {
                source.apply(i + j);
                if (j % rem == 0) {
                    source.apply(outliers++);
                }
            }
        }
    }

    public static class ValueSupplier implements Function<Integer, Object> {
        private final LongAdder counter = new LongAdder();

        @Override
        public Object apply(Integer integer) {
            counter.increment();
            return new Object();
        }
    }


}
