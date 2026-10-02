package dev.abros.anthub.core;

import com.google.gson.*;
import java.util.*;

/** Cursor pagination, including a refresh of already loaded pages without blanking the view. */
public final class PagedWindow {
    private final NavigableMap<Integer, JsonArray> pages = new TreeMap<>();
    private final Map<Integer, String> cursors = new HashMap<>();
    private int loadedPage, refreshThrough;
    private boolean more;
    private long nextAt;
    public void reset() { pages.clear(); cursors.clear(); loadedPage = refreshThrough = 0; more = false; nextAt = 0; }
    public void refresh() { refreshThrough = loadedPage; nextAt = 0; }
    public int loadedPage() { return loadedPage; }
    public boolean more() { return more; }
    public String cursor(int page) { return page == 0 ? "" : cursors.getOrDefault(page, ""); }
    public JsonArray entries() { return MenuPages.merge(pages.values()); }
    public void accept(int page, MenuData.Page response, boolean hasMore, long now) {
        var batch = new JsonArray(); response.entries().forEach(batch::add);
        pages.put(page, batch); cursors.put(page + 1, response.nextCursor());
        loadedPage = Math.max(loadedPage, page);
        if (!hasMore) {
            pages.tailMap(page, false).clear(); cursors.keySet().removeIf(index -> index > page + 1);
            loadedPage = page; refreshThrough = Math.min(refreshThrough, page);
        }
        if (page == loadedPage) more = hasMore;
        nextAt = page < refreshThrough ? now + 600 : 0;
    }
    public boolean refreshDue(long now) { return nextAt > 0 && now >= nextAt; }
    public boolean refreshing() { return nextAt > 0; }
    public void stopRefresh() { nextAt = 0; }
}
