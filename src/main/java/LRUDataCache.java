import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A cache that removes the least recently used entry when its size exceeds a given limit.
 */
public class LRUDataCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    public LRUDataCache(int maxSize) {
        super(maxSize + 1, 1.0f, true);
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}
