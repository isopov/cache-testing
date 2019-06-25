package com.sopovs.moradanen.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import org.cache2k.Cache2kBuilder;
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
        for (int rem : new int[]{101, 55, 49, 31, 23, 17, 11, 9, 8, 7, 6, 5, 4, 3, 2, 1}) {
            double best = (double) bestPossible(rem);
            System.out.println(noCache(rem) / best + ", " + caffeine(rem) / best + ", " + guava(rem) / best + ", " + ehcache(rem) / best + ", " + cache2k(rem) / best + ", " + infinispan(rem) / best);
        }
        for (int rem : new int[]{55, 49, 31, 23, 17, 11, 9, 8, 7, 6, 5, 4, 3, 2, 1}) {
            double best = (double) bestPossible(1, rem);
            System.out.println(noCache(1, rem) / best + ", " + caffeine(1, rem) / best + ", " + guava(1, rem) / best + ", " + ehcache(1, rem) / best + ", " + cache2k(1, rem) / best + ", " + infinispan(1, rem) / best);
        }
        for (int rem : new int[]{55, 49, 31, 23, 17, 11, 9, 8, 7, 6, 5, 4, 3, 2, 1}) {
            double best = (double) bestPossible(1, 1, rem);
            System.out.println(noCache(1, 1, rem) / best + ", " + caffeine(1, 1, rem) / best + ", " + guava(1, 1, rem) / best + ", " + ehcache(1, 1, rem) / best + ", " + cache2k(1, 1, rem) / best + ", " + infinispan(1, 1, rem) / best);
        }
    }

    private static final int INF_REPS = 10;

    private static long infinispan(int... rems) {
        long result = 0;
        for (int i = 0; i < INF_REPS; i++) {
            ValueSupplier supplier = new ValueSupplier();
            try (EmbeddedCacheManager m = new DefaultCacheManager()) {
                ConfigurationBuilder cb = new ConfigurationBuilder();
                cb.memory().evictionType(EvictionType.COUNT).size(CACHE_SIZE);


                m.defineConfiguration("foo", cb.build());
                org.infinispan.Cache<Integer, Object> cache = m.getCache("foo");

                useSource(key -> cache.computeIfAbsent(key, supplier::apply), rems);
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

    private static long cache2k(int... rems) {
        ValueSupplier supplier = new ValueSupplier();
        org.cache2k.Cache<Integer, Object> cache = new Cache2kBuilder<Integer, Object>() {
        }
                .entryCapacity(CACHE_SIZE)
                .loader(supplier::apply)
                .build();
        useSource(cache::get, rems);
        return supplier.counter.sum();
    }


    private static long guava(int... rems) {
        ValueSupplier supplier = new ValueSupplier();
        com.google.common.cache.LoadingCache<Integer, Object> cacheSource = CacheBuilder.newBuilder()
                .maximumSize(CACHE_SIZE)

                .build(new CacheLoader<>() {
                    @Override
                    public Object load(Integer in) {
                        return supplier.apply(in);
                    }
                });
        useSource(cacheSource, rems);
        return supplier.counter.sum();
    }

    private static final int CAF_REPS = 10;

    private static long caffeine(int... rems) {
        long result = 0;
        for (int i = 0; i < CAF_REPS; i++) {
            ValueSupplier supplier = new ValueSupplier();
            LoadingCache<Integer, Object> cacheSource = Caffeine.newBuilder()
                    .maximumSize(CACHE_SIZE)
                    .build(supplier::apply);
            useSource(cacheSource::get, rems);
            result += supplier.counter.sum();
        }
        return result / CAF_REPS;
    }

    private static final int EHCACHE_REPS = 10;

    private static long ehcache(int... rems) {
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

                org.ehcache.Cache<Integer, Object> cache = cacheManager.getCache("foo", Integer.class, Object.class);
                useSource(cache::get, rems);
            }
            result += supplier.counter.sum();
        }

        return result / EHCACHE_REPS;
    }

    private static long noCache(int... rems) {
        ValueSupplier supplier = new ValueSupplier();
        useSource(supplier, rems);
        return supplier.counter.sum();
    }

    private static long bestPossible(int... rems) {
        int result = 10099;
        for (int i = 0; i < 10_000; i++) {
            for (int j = 1; j < 101; j++) {
                for (int rem : rems) {
                    if (j % rem == 0) {
                        result++;
                    }
                }
            }
        }
        return result;
    }

    private static void useSource(Function<Integer, Object> source, int... rems) {
        int outliers = 20_000;
        for (int i = 0; i < 10_000; i++) {
            for (int j = 1; j < 101; j++) {
                source.apply(i + j);
                for (int rem : rems) {
                    if (j % rem == 0) {
                        source.apply(outliers++);
                    }
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
