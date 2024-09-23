import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.InitializingBean;

import com.alibaba.fastjson.JSON;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;

/**
 * @Description {@code guava.cache}抽象類
 * @param <K> the cache of Key
 * @param <V> the cache of Value
 *
 * @Date 2021/12/29
 * @author mingzhe.xiang
 */
@Slf4j
public abstract class AbstractGuavaCache<K, V> implements InitializingBean {

    /** 默认缓存过期时间 **/
    private static final int  DEFAULT_EXPIRE_TIME  = 60;
    /** 默认缓存初始化大小 **/
    private static final int  DEFAULT_SIZE = 100;
    /** 默认缓存元素最大个数 **/
    private static final int  DEFAULT_MAX_SIZE = 1000;

    private TimeUnit timeUnit = TimeUnit.MINUTES;

    private volatile LoadingCache<K, V> cache;

    /**
     * 实例化cache
     */
    private LoadingCache<K, V> cacheInstance() {
        if(cache == null){
            synchronized (this) {
                if(cache == null){
                    cache = newBuilder().build(new CacheLoader<K, V>() {
                        @Override
                        public V load(K key) throws Exception {
                            return loadData(key);
                        }

                        @Override
                        public ListenableFuture<V> reload(K key, V oldValue) throws Exception {
                            return reloadData(this, key, oldValue);
                        }
                    });
                }
            }
        }
        return cache;
    }

    /**
     * 構造{@link CacheBuilder}對象。
     * <p>默認的{@code CacheBuilder} {@code maximumSize}為1000 過期策略為{@code expireAfterAccess} 過期時間60分鐘
     * <p>子類可自行決定是否重写
     */
    protected CacheBuilder<Object, Object> newBuilder() {
        return CacheBuilder.newBuilder()
                .initialCapacity(DEFAULT_SIZE)
                .maximumSize(DEFAULT_MAX_SIZE)
                .expireAfterAccess(DEFAULT_EXPIRE_TIME, timeUnit);
    }

    /**
     * 加載數據到緩存
     *
     */
    protected abstract V loadData(K key);

    /**
     * 重新加載緩存(指定過期策略為{@code refreshAfterWrite} 建議複寫該方法)
     * 子類自行決定是否重寫
     */
    protected ListenableFuture<V> reloadData(CacheLoader<K, V> cacheLoader, K key, V oldValue) throws Exception {
        return cacheLoader.reload(key, oldValue);
    }

    /**
     * 獲取緩存數據
     */
    public V getValue(K key) throws ExecutionException {
        return cache.get(key);
    }

    /**
     * 獲取緩存數據,未獲取到數據時 返回默認值
     */
    public V getValueOrDefault(K key, V defaultValue) {
        try {
            return getValue(key);
        } catch (Exception e) {
            log.error("Guava Cache:key:{} not found", JSON.toJSONString(key));
            return defaultValue;
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 缓存对象初始化
        cache = cacheInstance();
        // 预加载缓存数据
        preLoadData(cache);
    }

    /**
     * CacheBean初始化时 预加载缓存数据<p>
     * 子類自行決定是否重寫,并非所有缓存都需要预加载
     */
    protected void preLoadData(LoadingCache<K,V> cache) {}

    /**
     * 刷新缓存
     * @param key
     */
    protected void refresh(K key) {
        log.info("[{}] refresh key:{}", this.getClass().getName(), key);
        cache.refresh(key);
    }

    public void invalidateAll() {
        log.info("[{}] invalidate all cache entity", this.getClass().getName());
        cache.invalidateAll();
    }

    public Map asMap() {
        return cache.asMap();
    }
}
