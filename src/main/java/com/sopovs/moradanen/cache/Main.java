package com.sopovs.moradanen.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.spi.loaderwriter.CacheLoaderWriter;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import static org.ehcache.config.builders.CacheConfigurationBuilder.newCacheConfigurationBuilder;
import static org.ehcache.config.builders.CacheManagerBuilder.newCacheManagerBuilder;
import static org.ehcache.config.builders.ResourcePoolsBuilder.heap;

public class Main {


    public static void main(String[] args) {
        for (int rem : new int[]{101, 55, 49, 31, 23, 17, 11, 9, 8,7,6,5, 4, 3, 2, 1})
            System.out.println(noCache(rem) + ", " + caffeine(rem) + ", " + guava(rem) + ", " + ehcache(rem));

    }

    private static long guava(int rem) {
        ValueSupplier supplier = new ValueSupplier();
        com.google.common.cache.LoadingCache<Integer, Object> cacheSource = CacheBuilder.newBuilder()
                .maximumSize(150)

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
                    .maximumSize(150)
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
                    .withCache("foo", newCacheConfigurationBuilder(Integer.class, Object.class, heap(150))
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

    private static long noCache(int rem) {
        ValueSupplier supplier = new ValueSupplier();
        useSource(supplier, rem);
        return supplier.counter.sum();
    }


    public static void useSource(Function<Integer, Object> source, int rem) {
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
