/*
 * Copyright 2014 Bademus
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   Contributors:
 *                Bademus
 */

package org.github.bademux.feedly.api.util.db;

import com.google.api.client.repackaged.com.google.common.base.Joiner;
import com.google.api.client.repackaged.com.google.common.base.Strings;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.github.bademux.feedly.api.model.Category;
import org.github.bademux.feedly.api.model.Entry;
import org.github.bademux.feedly.api.model.Feed;
import org.github.bademux.feedly.api.model.IdGenericJson;
import org.github.bademux.feedly.api.model.Subscription;
import org.github.bademux.feedly.api.model.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static android.content.ContentProviderOperation.Builder;
import static android.content.ContentProviderOperation.newInsert;
import static org.github.bademux.feedly.api.provider.FeedlyContract.Categories;
import static org.github.bademux.feedly.api.provider.FeedlyContract.Entries;
import static org.github.bademux.feedly.api.provider.FeedlyContract.EntriesByCategory;
import static org.github.bademux.feedly.api.provider.FeedlyContract.EntriesByTag;
import static org.github.bademux.feedly.api.provider.FeedlyContract.EntriesTags;
import static org.github.bademux.feedly.api.provider.FeedlyContract.Feeds;
import static org.github.bademux.feedly.api.provider.FeedlyContract.FeedsByCategory;
import static org.github.bademux.feedly.api.provider.FeedlyContract.FeedsCategories;
import static org.github.bademux.feedly.api.provider.FeedlyContract.Files;
import static org.github.bademux.feedly.api.provider.FeedlyContract.Tags;

public final class FeedlyDbUtils {

  public static Collection<ContentProviderOperation> processSubscriptions(
      final Collection<Subscription> subscriptions) {
    //approx. size: subscriptions + categories + mappings
    List<ContentProviderOperation> operations = new ArrayList<>(subscriptions.size() * 2);
    Map<String, ContentProviderOperation> miscOps = new HashMap<>();
    for (Subscription subscription : subscriptions) {
      operations.add(newInsert(Feeds.CONTENT_URI).withValues(convert(subscription)).build());
      processCategories(operations, miscOps, subscription, subscription.getCategories());
    }
    return operations;
  }

  public static Collection<ContentProviderOperation> processEntries(
      final Collection<Entry> entries) {
    //approx. size: subscriptions + categories + mappings
    List<ContentProviderOperation> operations = new ArrayList<>(entries.size() * 2);
    Map<String, ContentProviderOperation> miscOps = new HashMap<>();
    for (Entry entry : entries) {
      Entry.Origin stream = entry.getOrigin();

      Feed feed = new Subscription(IdGenericJson.parse(stream.getStreamId()), stream.getTitle());
      addIfNew(operations, miscOps, newInsert(Feeds.CONTENT_URI).withValues(convert(feed)),
               feed.getId());
      processCategories(operations, miscOps, feed, entry.getCategories());

      operations.add(newInsert(Entries.CONTENT_URI).withValues(convert(entry)).build());
      processTags(operations, miscOps, entry, entry.getTags());
    }
    return operations;
  }

  private static void addIfNew(final List<ContentProviderOperation> operations,
                               final Map<String, ContentProviderOperation> miscOps,
                               final Builder builder, final String id) {
    if (!miscOps.containsKey(id)) {
      final ContentProviderOperation op = builder.build();
      operations.add(op);
      miscOps.put(id, op);
    }
  }

  protected static void processCategories(final List<ContentProviderOperation> operations,
                                          final Map<String, ContentProviderOperation> miscOps,
                                          final Feed feed, final List<Category> categories) {
    if (categories == null) {
      return;
    }
    for (int i = 0; i < categories.size(); i++) {
      Category category = categories.get(i);
      addIfNew(operations, miscOps,
               newInsert(Categories.CONTENT_URI).withValues(convert(category)), category.getId());

      Builder builder = newInsert(FeedsCategories.CONTENT_URI).withValues(convert(feed, category));
      //hints commiting after this operation if last item
      if (i == categories.size()) {
        builder.withYieldAllowed(true);
      }
      addIfNew(operations, miscOps, builder, feed.getId() + category.getId());
    }
  }

  protected static void processTags(final List<ContentProviderOperation> operations,
                                    final Map<String, ContentProviderOperation> miscOps,
                                    final Entry entry, final List<Tag> tags) {
    if (tags == null) {
      return;
    }
    for (int i = 0; i < tags.size(); i++) {
      Tag tag = tags.get(i);
      addIfNew(operations, miscOps,
               newInsert(Tags.CONTENT_URI).withValues(convert(tag)), tag.getId());
      Builder builder = newInsert(EntriesTags.CONTENT_URI).withValues(convert(entry, tag));
      //hints commiting after this operation if last item
      if (i == tags.size()) {
        builder.withYieldAllowed(true);
      }
      addIfNew(operations, miscOps, builder, entry.getId() + tag.getId());
    }
  }

  public static ContentValues convert(final Feed subscription) {
    ContentValues values = new ContentValues();
    values.put(Feeds.ID, subscription.getId());
    values.put(Feeds.TITLE, subscription.getTitle());
    values.put(Feeds.WEBSITE, subscription.getWebsite());
    values.put(Feeds.VELOCITY, subscription.getVelocity());
    Feed.State state = subscription.getState();
    if (state != null) {
      values.put(Feeds.STATE, state.name());
    }
    return values;
  }

  public static ContentValues convert(final Subscription subscription) {
    ContentValues values = convert((Feed) subscription);
    values.put(Feeds.SORTID, subscription.getSortid());
    values.put(Feeds.UPDATED, subscription.getUpdated());
    return values;
  }

  public static ContentValues convert(final Category category) {
    ContentValues values = new ContentValues();
    values.put(Categories.ID, category.getId());
    values.put(Categories.LABEL, category.getLabel());
    return values;
  }

  public static ContentValues convert(final Feed subscription, final Category category) {
    return convert(FeedsCategories.FEED_ID, subscription.getId(),
                   FeedsCategories.CATEGORY_ID, category.getId());
  }

  public static ContentValues convert(final Entry entry, final Tag tag) {
    return convert(EntriesTags.ENTRY_ID, entry.getId(), EntriesTags.TAG_ID, tag.getId());
  }

  public static ContentValues convert(final String name1, final String value1,
                                      final String name2, final String value2) {
    ContentValues values = new ContentValues();
    values.put(name1, value1);
    values.put(name2, value2);
    return values;
  }

  public static ContentValues convert(final Tag tag) {
    ContentValues values = new ContentValues();
    values.put(Tags.ID, tag.getId());
    values.put(Tags.LABEL, tag.getLabel());
    return values;
  }

  public static ContentValues convert(final Entry entry) {
    ContentValues values = new ContentValues();
    values.put(Entries.ID, entry.getId());
    values.put(Entries.UNREAD, entry.getUnread());
    values.put(Entries.TITLE, entry.getTitle());
    List<String> keywords = entry.getKeywords();
    if (keywords != null) {
      values.put(Entries.KEYWORDS, Joiner.on(SEPARATOR).join(keywords));
    }
    values.put(Entries.PUBLISHED, entry.getPublished());
    values.put(Entries.UPDATED, entry.getUnread());
    values.put(Entries.CRAWLED, entry.getCrawled());
    Long recrawled = entry.getRecrawled();
    if (recrawled != null) {
      values.put(Entries.CRAWLED, recrawled);
    }
    String author = entry.getAuthor();
    if (author != null) {
      values.put(Entries.AUTHOR, author);
    }
    values.put(Entries.ENGAGEMENT, entry.getEngagement());
    Double engagementRate = entry.getEngagementRate();
    if (engagementRate != null) {
      values.put(Entries.ENGAGEMENTRATE, engagementRate);
    }
    Entry.Content summary = entry.getSummary();
    if (summary != null) {
      String text = summary.getContent();
      if (text != null) {
        values.put(Entries.SUMMARY, summary.getContent());
        Entry.Content.Direction dir = summary.getDirection();
        if (dir != null) {
          values.put(Entries.SUMMARY_DIRECTION, dir.name());
        }
      }
    }
    Entry.Content content = entry.getContent();
    if (content != null) {
      String text = content.getContent();
      if (text != null) {
        values.put(Entries.CONTENT, text);
        Entry.Content.Direction dir = content.getDirection();
        if (dir != null) {
          values.put(Entries.SUMMARY_DIRECTION, dir.name());
        }
      }
    }
    Entry.Visual visual = entry.getVisual();
    if (visual != null) {
      values.put(Entries.VISUAL_URL, visual.getUrl());
    }
    List<Entry.Enclosure> enclosures = entry.getEnclosure();
    if (enclosures != null && !enclosures.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      Iterator<Entry.Enclosure> it = enclosures.iterator();
      boolean hasNext = it.hasNext();
      while (hasNext) {
        Entry.Enclosure enclosure = it.next();
        String mime = enclosure.getMime();
        hasNext = it.hasNext();
        if (!Strings.isNullOrEmpty(mime)) {
          sb.append(mime);
          if (hasNext) { sb.append(SEPARATOR); }
        }
      }
      values.put(Entries.ENCLOSURE_MIMES, sb.toString());
    }
    values.put(Entries.ORIGINID, entry.getOriginId());
    values.put(Entries.FINGERPRINT, entry.getFingerprint());

    Entry.Origin origin = entry.getOrigin();
    if (origin != null) {
      values.put(Entries.ORIGIN_STREAMID, entry.getOrigin().getStreamId());
      values.put(Entries.ORIGIN_TITLE, entry.getOrigin().getTitle());
    }

    return values;
  }

  public static ContentValues convert(final Entry.File file) {
    ContentValues values = new ContentValues();
    values.put(Files.URL, file.getUrl());
    String mime = file.getMime();
    if (mime != null) {
      values.put(Files.MIME, file.getMime());
    }
    return values;
  }

  static final Comparator<ContentValues> ENTRIES_TAGS_CMP = new Comparator<ContentValues>() {
    @Override
    public int compare(final ContentValues lhs, final ContentValues rhs) {
      boolean q = lhs.get(EntriesTags.ENTRY_ID).equals(rhs.get(EntriesTags.ENTRY_ID))
                  && lhs.get(EntriesTags.TAG_ID).equals(rhs.get(EntriesTags.TAG_ID));
      return q ? 0 : 1;
    }
  };
  static final Comparator<ContentValues> FEEDS_CATEGORIES_CMP
      = new Comparator<ContentValues>() {
    @Override
    public int compare(final ContentValues lhs, final ContentValues rhs) {
      boolean q = lhs.get(FeedsCategories.CATEGORY_ID).equals(rhs.get(FeedsCategories.CATEGORY_ID))
                  && lhs.get(FeedsCategories.FEED_ID).equals(rhs.get(FeedsCategories.FEED_ID));
      return q ? 0 : 1;
    }
  };
  static final Comparator<ContentValues> ENTITY_CMP = new Comparator<ContentValues>() {
    @Override
    public int compare(final ContentValues lhs, final ContentValues rhs) {
      return lhs.get("id").equals(rhs.get("id")) ? 0 : 1;
    }
  };

  public static void processEntries(final ContentResolver contentResolver,
                                    final Collection<Entry> inEntries) {

    Set<ContentValues> feeds = new TreeSet<ContentValues>(ENTITY_CMP);
    Set<ContentValues> categories = new TreeSet<ContentValues>(ENTITY_CMP);
    Set<ContentValues> feedsCategories = new TreeSet<ContentValues>(FEEDS_CATEGORIES_CMP);

    Set<ContentValues> entries = new TreeSet<ContentValues>(ENTITY_CMP);
    Set<ContentValues> tags = new TreeSet<ContentValues>(ENTITY_CMP);
    Set<ContentValues> entriesTags = new TreeSet<ContentValues>(ENTRIES_TAGS_CMP);
    List<ContentValues> files = new ArrayList<ContentValues>();

    for (Entry entry : inEntries) {
      entries.add(convert(entry));

      List<Tag> tgs = entry.getTags();
      if (tgs != null) {
        for (Tag tag : tgs) {
          tags.add(convert(tag));
          entriesTags.add(convert(entry, tag));
        }
      }

      Entry.Origin stream = entry.getOrigin();
      Feed feed = new Subscription(IdGenericJson.parse(stream.getStreamId()), stream.getTitle());
      feeds.add(convert(feed));

      List<Category> cats = entry.getCategories();
      if (cats != null) {
        for (Category category : cats) {
          categories.add(convert(category));
          feedsCategories.add(convert(feed, category));
        }
      }

      Entry.Visual visual = entry.getVisual();
      if (visual != null) {
        files.add(convert(visual));
      }

      List<Entry.Enclosure> enclosures = entry.getEnclosure();
      if (enclosures != null) {
        for (Entry.Enclosure enclosure : enclosures) {
          files.add(convert(enclosure));
        }
      }
    }

    bulkInsert(contentResolver, Files.CONTENT_URI, files.toArray(new ContentValues[files.size()]));

    bulkInsert(contentResolver, Feeds.CONTENT_URI,
               feeds.toArray(new ContentValues[feeds.size()]));
    bulkInsert(contentResolver, Categories.CONTENT_URI,
               categories.toArray(new ContentValues[categories.size()]));
    bulkInsert(contentResolver, FeedsCategories.CONTENT_URI,
               feedsCategories.toArray(new ContentValues[feedsCategories.size()]));

    bulkInsert(contentResolver, Entries.CONTENT_URI,
               entries.toArray(new ContentValues[entries.size()]));
    bulkInsert(contentResolver, Tags.CONTENT_URI,
               tags.toArray(new ContentValues[tags.size()]));
    bulkInsert(contentResolver, EntriesTags.CONTENT_URI,
               entriesTags.toArray(new ContentValues[entriesTags.size()]));
  }

  public static void processSubscriptions(final ContentResolver contentResolver,
                                          final Collection<Subscription> inSubscriptions) {
    Set<ContentValues> feeds = new TreeSet<ContentValues>(ENTITY_CMP);
    Set<ContentValues> categories = new TreeSet<ContentValues>(ENTITY_CMP);
    Set<ContentValues> feedsCategories = new TreeSet<ContentValues>(FEEDS_CATEGORIES_CMP);

    for (Subscription subscription : inSubscriptions) {
      feeds.add(convert(subscription));
      List<Category> cats = subscription.getCategories();
      if (cats == null) {
        continue;
      }
      for (Category category : cats) {
        categories.add(convert(category));
        feedsCategories.add(convert(subscription, category));
      }
    }

    bulkInsert(contentResolver, Feeds.CONTENT_URI,
               feeds.toArray(new ContentValues[feeds.size()]));
    bulkInsert(contentResolver, Categories.CONTENT_URI,
               categories.toArray(new ContentValues[categories.size()]));
    bulkInsert(contentResolver, FeedsCategories.CONTENT_URI,
               feedsCategories.toArray(new ContentValues[feedsCategories.size()]));
  }

  protected static void bulkInsert(final ContentResolver contentResolver, final Uri uri,
                                   final ContentValues[] values) {
    if (values != null) { contentResolver.bulkInsert(uri, values); }
  }

  public static void create(final SQLiteDatabase db) {
    db.execSQL("CREATE TABLE IF NOT EXISTS " + Feeds.TBL_NAME + "("
               + Feeds.ID + " TEXT PRIMARY KEY NOT NULL,"
               + Feeds.TITLE + " TEXT NOT NULL,"
               + Feeds.SORTID + " TEXT,"
               + Feeds.UPDATED + " BIGINT,"
               + Feeds.WEBSITE + " TEXT,"
               + Feeds.VELOCITY + " DOUBLE,"
               + Feeds.STATE + " TEXT, "
               + Feeds.FAVICON + " TEXT, "
               + "FOREIGN KEY(" + Feeds.FAVICON + ") REFERENCES "
               + Files.TBL_NAME + "(" + Files.URL + ") ON UPDATE CASCADE)");

    db.execSQL("CREATE INDEX idx_" + Feeds.TBL_NAME + "_" + Feeds.TITLE
               + " ON " + Feeds.TBL_NAME + "(" + Feeds.TITLE + ")");
    db.execSQL("CREATE INDEX idx_" + Feeds.TBL_NAME + "_" + Feeds.SORTID
               + " ON " + Feeds.TBL_NAME + "(" + Feeds.SORTID + ")");
    db.execSQL("CREATE INDEX idx_" + Feeds.TBL_NAME + "_" + Feeds.WEBSITE
               + " ON " + Feeds.TBL_NAME + "(" + Feeds.WEBSITE + ")");
    db.execSQL("CREATE INDEX idx_" + Feeds.TBL_NAME + "_" + Feeds.FAVICON
               + " ON " + Feeds.TBL_NAME + "(" + Feeds.FAVICON + ")");

    db.execSQL("CREATE TABLE IF NOT EXISTS " + Categories.TBL_NAME + "("
               + Categories.ID + " TEXT PRIMARY KEY NOT NULL,"
               + Categories.LABEL + " TEXT)");
    db.execSQL("CREATE INDEX idx_" + Categories.TBL_NAME + "_" + Categories.LABEL
               + " ON " + Categories.TBL_NAME + "(" + Categories.LABEL + ")");

    db.execSQL("CREATE TABLE IF NOT EXISTS " + FeedsCategories.TBL_NAME + "("
               + FeedsCategories.FEED_ID + " TEXT,"
               + FeedsCategories.CATEGORY_ID + " TEXT,"
               + "PRIMARY KEY(" + FeedsCategories.FEED_ID + ',' + FeedsCategories.CATEGORY_ID
               + ") ON CONFLICT IGNORE,"
               + "FOREIGN KEY(" + FeedsCategories.FEED_ID + ") REFERENCES "
               + Feeds.TBL_NAME + "(" + Feeds.ID + ") ON UPDATE CASCADE ON DELETE CASCADE,"
               + "FOREIGN KEY(" + FeedsCategories.CATEGORY_ID + ") REFERENCES "
               + Categories.TBL_NAME + "(" + Categories.ID
               + ") ON UPDATE CASCADE ON DELETE CASCADE)");

    db.execSQL("CREATE INDEX idx_" + FeedsCategories.TBL_NAME + "_" + FeedsCategories.FEED_ID
               + " ON " + FeedsCategories.TBL_NAME + "(" + FeedsCategories.FEED_ID + ")");
    db.execSQL("CREATE INDEX idx_" + FeedsCategories.TBL_NAME + "_" + FeedsCategories.CATEGORY_ID
               + " ON " + FeedsCategories.TBL_NAME + "(" + FeedsCategories.CATEGORY_ID + ")");

    db.execSQL("CREATE VIEW " + FeedsByCategory.TBL_NAME + " AS "
               + "SELECT * FROM " + Feeds.TBL_NAME + " INNER JOIN " + FeedsCategories.TBL_NAME
               + " ON " + Feeds.TBL_NAME + "." + Feeds.ID + "="
               + FeedsCategories.TBL_NAME + "." + FeedsCategories.FEED_ID);

    db.execSQL("CREATE TABLE IF NOT EXISTS " + Entries.TBL_NAME + "("
               + Entries.ID + " TEXT PRIMARY KEY NOT NULL,"
               + Entries.AUTHOR + " TEXT,"
               + Entries.TITLE + " TEXT,"
               + Entries.CONTENT + " TEXT,"
               + Entries.CONTENT_DIRECTION + " TEXT,"
               + Entries.SUMMARY + " TEXT,"
               + Entries.SUMMARY_DIRECTION + " TEXT,"
               + Entries.CRAWLED + " TIMESTAMP NOT NULL, "
               + Entries.RECRAWLED + " TIMESTAMP, "
               + Entries.PUBLISHED + " TIMESTAMP NOT NULL,"
               + Entries.UPDATED + " TIMESTAMP,"
               + Entries.VISUAL_URL + " TEXT,"
               + Entries.UNREAD + " BOOLEAN NOT NULL,"
               + Entries.KEYWORDS + " TEXT,"
               + Entries.ENGAGEMENT + " BIGINT,"
               + Entries.ENGAGEMENTRATE + " DOUBLE,"
               + Entries.ORIGINID + " TEXT,"
               + Entries.FINGERPRINT + " TEXT,"
               + Entries.ORIGIN_STREAMID + " TEXT,"
               + Entries.ORIGIN_TITLE + " TEXT,"
               + Entries.ENCLOSURE_MIMES + " TEXT,"
               + "FOREIGN KEY(" + Entries.ORIGIN_STREAMID + ") REFERENCES "
               + Feeds.TBL_NAME + "(" + Feeds.ID + ") ON UPDATE CASCADE ON DELETE CASCADE, "
               + "FOREIGN KEY(" + Entries.VISUAL_URL + ") REFERENCES "
               + Files.TBL_NAME + "(" + Files.URL + ") ON UPDATE CASCADE)");

    db.execSQL("CREATE INDEX idx_" + Entries.TBL_NAME + "_" + Entries.TITLE
               + " ON " + Entries.TBL_NAME + "(" + Entries.TITLE + ")");
    db.execSQL("CREATE INDEX idx_" + Entries.TBL_NAME + "_" + Entries.AUTHOR
               + " ON " + Entries.TBL_NAME + "(" + Entries.AUTHOR + ")");
    db.execSQL("CREATE INDEX idx_" + Entries.TBL_NAME + "_" + Entries.ORIGINID
               + " ON " + Entries.TBL_NAME + "(" + Entries.ORIGINID + ")");
    db.execSQL("CREATE INDEX idx_" + Entries.TBL_NAME + "_" + Entries.ORIGIN_STREAMID
               + " ON " + Entries.TBL_NAME + "(" + Entries.ORIGIN_STREAMID + ")");
    db.execSQL("CREATE UNIQUE INDEX uqx_" + Entries.TBL_NAME + "_" + Entries.FINGERPRINT
               + " ON " + Entries.TBL_NAME + "(" + Entries.FINGERPRINT + ")");
    db.execSQL("CREATE INDEX idx_" + Entries.TBL_NAME + "_" + Entries.VISUAL_URL
               + " ON " + Entries.TBL_NAME + "(" + Entries.VISUAL_URL + ")");

    db.execSQL("CREATE VIEW " + EntriesByCategory.TBL_NAME + " AS "
               + "SELECT * FROM " + Entries.TBL_NAME + " INNER JOIN " + FeedsCategories.TBL_NAME
               + " ON " + Entries.TBL_NAME + "." + Entries.ORIGIN_STREAMID + "="
               + FeedsCategories.TBL_NAME + "." + FeedsCategories.FEED_ID);

    db.execSQL("CREATE TABLE IF NOT EXISTS " + Tags.TBL_NAME + "("
               + Tags.ID + " TEXT PRIMARY KEY NOT NULL,"
               + Tags.LABEL + " TEXT)");
    db.execSQL("CREATE INDEX idx_" + Tags.TBL_NAME + "_" + Tags.LABEL
               + " ON " + Tags.TBL_NAME + "(" + Tags.LABEL + ")");

    db.execSQL("CREATE TABLE IF NOT EXISTS " + EntriesTags.TBL_NAME + "("
               + EntriesTags.ENTRY_ID + " TEXT,"
               + EntriesTags.TAG_ID + " TEXT,"
               + "PRIMARY KEY(" + EntriesTags.ENTRY_ID + ',' + EntriesTags.TAG_ID
               + ") ON CONFLICT IGNORE,"
               + "FOREIGN KEY(" + EntriesTags.ENTRY_ID + ") REFERENCES "
               + Entries.TBL_NAME + "(" + Entries.ID + ") ON UPDATE CASCADE ON DELETE CASCADE,"
               + "FOREIGN KEY(" + EntriesTags.TAG_ID + ") REFERENCES "
               + Tags.TBL_NAME + "(" + Tags.ID + ") ON UPDATE CASCADE ON DELETE CASCADE)");
    db.execSQL("CREATE INDEX idx_" + EntriesTags.TBL_NAME + "_" + EntriesTags.ENTRY_ID
               + " ON " + EntriesTags.TBL_NAME + "(" + EntriesTags.ENTRY_ID + ")");
    db.execSQL("CREATE INDEX idx_" + EntriesTags.TBL_NAME + "_" + EntriesTags.TAG_ID
               + " ON " + EntriesTags.TBL_NAME + "(" + EntriesTags.TAG_ID + ")");

    db.execSQL("CREATE VIEW " + EntriesByTag.TBL_NAME + " AS "
               + "SELECT * FROM " + Entries.TBL_NAME + " INNER JOIN " + EntriesTags.TBL_NAME
               + " ON " + Entries.TBL_NAME + "." + Entries.ID + "="
               + EntriesTags.TBL_NAME + "." + EntriesTags.ENTRY_ID);

    db.execSQL("CREATE TABLE IF NOT EXISTS " + Files.TBL_NAME + "("
               + Files.URL + " TEXT PRIMARY KEY NOT NULL,"
               + Files.MIME + " TEXT, "
               + Files.RETRIES + " INT, "
               + Files.CREATED + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");

    db.execSQL("CREATE INDEX idx_" + Files.TBL_NAME + "_" + Files.URL
               + " ON " + Files.TBL_NAME + "(" + Files.URL + ")");
  }

  public static void dropAll(final SQLiteDatabase db) {
    drop(db, "index", "name NOT LIKE 'sqlite_autoindex_%'");

    //TODO: preserve order (dependency)
//    FeedlyDbUtils.drop(db, "table", "name != 'android_metadata'");
    db.execSQL("DROP TABLE IF EXISTS '" + FeedsCategories.TBL_NAME + "'");
    db.execSQL("DROP TABLE IF EXISTS '" + EntriesTags.TBL_NAME + "'");
    db.execSQL("DROP TABLE IF EXISTS '" + Entries.TBL_NAME + "'");
    db.execSQL("DROP TABLE IF EXISTS '" + Categories.TBL_NAME + "'");
    db.execSQL("DROP TABLE IF EXISTS '" + Feeds.TBL_NAME + "'");
    db.execSQL("DROP TABLE IF EXISTS '" + Tags.TBL_NAME + "'");
    db.execSQL("DROP TABLE IF EXISTS '" + Files.TBL_NAME + "'");

    drop(db, "view", null);
  }

  public static void drop(final SQLiteDatabase db, final String type, final String whereClause) {
    String sql = "SELECT name FROM sqlite_master WHERE type='" + type + '\'';
    if (whereClause != null) {
      sql += " AND " + whereClause;
    }

    Cursor c = db.rawQuery(sql, null);
    if (c.moveToFirst()) {
      while (!c.isAfterLast()) {
        db.execSQL("DROP " + type + " IF EXISTS '" + c.getString(0) + '\'');
        c.moveToNext();
      }
    }
  }

  /**
   * Merges two String arrays, nullsafe
   *
   * @param strings1 - first array
   * @param strings2 - second array
   * @return merged
   */
  public static final String[] merge(final String[] strings1, final String... strings2) {
    if (strings1 == null || strings2.length == 0) {
      if (strings2 == null || strings2.length == 0) {
        return null;
      }
      return strings2;
    }

    String[] tmpProjection = new String[strings1.length + strings2.length];
    System.arraycopy(strings2, 0, tmpProjection, 0, strings2.length);
    System.arraycopy(strings1, 0, tmpProjection, strings2.length, strings1.length);
    return tmpProjection;
  }

  public static final char SEPARATOR = '\t';

  private FeedlyDbUtils() {}
}
