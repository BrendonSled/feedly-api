/*
 * Copyright 2013 Bademus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *    Contributors:
 *                 Bademus
 */

package org.github.bademux.feedly.api.model;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.api.client.util.NullValue;
import com.google.api.client.util.Value;

import java.util.ArrayList;
import java.util.List;

public final class Entry extends GenericJson implements Markable {

  /** the unique, immutable ID for this particular article. */
  @Key
  private String id;
  /** Optional. the article’s title. This string does not contain any HTML markup. */
  @Key
  private String title;
  /**
   * Optional content object the article content. This object typically has two values: “content”
   * for the content itself, and “direction” (“ltr” for left-to-right, “rtl” for right-to-left).
   * The
   * content itself contains sanitized HTML markup
   */
  @Key
  private Content content;
  /** Optional content object the article summary. See the content object above. */
  @Key
  private Content summary;
  /** Optional string the author’s name */
  @Key
  private String author;
  /**
   * timestamp the immutable timestamp, in ms, when this article was processed by the feedly Cloud
   * servers.
   */
  @Key
  private Long crawled;
  /**
   * Optional the timestamp, in ms, when this article was re-processed and updated by the feedly
   * Cloud servers.
   */
  @Key
  private Long recrawled;
  /**
   * the timestamp, in ms, when this article was published, as reported by the RSS feed (often
   * inaccurate).
   */
  @Key
  private Long published;
  /** Optional the timestamp, in ms, when this article was updated, as reported by the RSS feed */
  @Key
  private Long updated;
  /**
   * Optional link object array a list of alternate links for this article. Each link object
   * contains a media type and a URL. Typically, a single object is present, with a link to the
   * original web page.
   */
  @Key
  private List<Location> alternate;
  /**
   * Optional origin object the feed from which this article was crawled. If present, “streamId”
   * will contain the feed id, “title” will contain the feed title, and “htmlUrl” will contain the
   * feed’s website.
   */
  @Key
  private Origin origin;
  /** Optional string array a list of keyword strings extracted from the RSS entry. */
  @Key
  private List<String> keywords;
  /**
   * Was this entry read by the user? If an Authorization header is not provided, this will always
   * return false. If an Authorization header is provided, it will reflect if the user has read
   * this
   * entry or not.
   */
  @Key
  private Boolean unread;
  /** Optional visual object an image URL for this entry. */
  @Key
  private Visual visual;
  /**
   * Optional tag object array a list of tag objects (“id” and “label”) that the user added to this
   * entry. This value is only returned if an Authorization header is provided, and at least one
   * tag
   * has been added. If the entry has been explicitly marked as read (not the feed itself), the
   * “global.read” tag will be present.
   */
  @Key
  private List<Tag> tags;
  /**
   * category object array a list of category objects (“id” and “label”) that the user associated
   * with the feed of this entry. This value is only returned if an Authorization header is
   * provided.
   */
  @Key
  private List<Category> categories;
  /**
   * Optional integer an indicator of how popular this entry is. The higher the number, the more
   * readers have read, saved or shared this particular entry.
   */
  @Key
  private Integer engagement;
  /**
   * https://groups.google.com/d/msg/feedly-cloud/ZkCTEQS3wEU/xHEvCXILNRoJ
   * This number is an indication of how "hot" an article is compared to others. It is generated by
   * dividing the current engagement number by the average engagement for this feed. In this case,
   * other articles in this feed have an average engagement of 75, so this article is three times
   * more popular.
   * The higher the number, the more "off the charts" an article is (and thus the higher the chance
   * it is interesting to the reader).
   * A few other notes:
   * -the engagement rate is only provided during the first week of an article life
   * -each feed has its own average engagement model, which is updated several times a day for very
   * active feeds
   * -the rate for a given article will change over time, since the article engagement will change
   * over time, and the average engagement is a function of time. Thus it's not recommended to
   * store
   * or cache this number for a long period of time.
   * -in some cases, we are unable to provide an engagement rate because we lack data; in this
   * case,
   * the key will not be in the JSON object
   */
  @Deprecated
  @Key
  private Double engagementRate;
  @Deprecated
  @Key
  private List<Location> canonical;
  /**
   * Optional timestamp for tagged articles, contains the timestamp when the article was tagged by
   * the user. This will only be returned when the entry is returned through the streams API.
   */
  @Key
  private Long actionTimestamp;
  /**
   * Optional link object array a list of media links (videos, images, sound etc) provided by the
   * feed. Some entries do not have a summary or content, only a collection of media links.
   */
  @Key
  private List<Enclosure> enclosure;
  /** string the unique id of this post in the RSS feed (not necessarily a URL!) */
  @Key
  private String originId;
  /** string the article fingerprint. This value might change if the article is updated. */
  @Key
  private String fingerprint;
  /** Optional string an internal search id. */
  @Key
  private String sid;

  public String getId() { return id; }

  public String getTitle() { return title; }

  public void setTitle(final String title) { this.title = title; }

  public String getAuthor() { return author; }

  public Content getContent() { return content; }

  public void setContent(final String content, final Content.Direction direction) {
    this.content = new Content(content, direction);
  }

  public void setContent(final String content) { setContent(content, Content.Direction.LTR); }

  public Content getSummary() { return summary; }

  public Long getCrawled() { return crawled; }

  public Long getRecrawled() { return recrawled; }

  public Long getPublished() { return published; }

  public Long getUpdated() { return updated; }

  public Boolean getUnread() { return unread; }

  public List<String> getKeywords() { return keywords; }

  public void setKeywords(final List<String> keywords) { this.keywords = keywords; }

  public void addKeyword(final String keyword) {
    if (keywords == null) {
      keywords = new ArrayList<String>();
    }
    keywords.add(keyword);
  }

  public Visual getVisual() { return visual; }

  public Integer getEngagement() { return engagement; }

  @Deprecated
  public Double getEngagementRate() { return engagementRate; }

  public List<Category> getCategories() { return categories; }

  public List<Tag> getTags() { return tags; }

  public void setTags(final List<Tag> tags) { this.tags = tags; }

  public void addTag(final Tag tag) {
    if (tags == null) {
      tags = new ArrayList<Tag>();
    }
    tags.add(tag);
  }

  public List<Location> getAlternate() { return alternate; }

  public void setAlternate(List<Location> alternate) {
    this.alternate = alternate;
  }

  public void addAlternate(Location alternate) {
    if (alternate == null) {
      this.alternate = new ArrayList<Location>();
    }
    this.alternate.add(alternate);
  }

  @Deprecated
  public List<Location> getCanonical() { return canonical; }

  public Origin getOrigin() { return origin; }

  public Long getActionTimestamp() { return actionTimestamp; }

  public List<Enclosure> getEnclosure() { return enclosure; }

  public String getOriginId() { return originId; }

  public String getFingerprint() { return fingerprint; }

  public String getSid() { return sid; }

  @Override
  public org.github.bademux.feedly.api.model.Entry set(String fieldName, Object value) {
    return (org.github.bademux.feedly.api.model.Entry) super.set(fieldName, value);
  }

  @Override
  public org.github.bademux.feedly.api.model.Entry clone() {
    return (org.github.bademux.feedly.api.model.Entry) super.clone();
  }

  public static class Location {

    @Key
    private String href;
    @Key
    private String type;

    public Location(final String href, final String type) {
      this.href = href;
      this.type = type;
    }

    public Location() {}

    public String getHref() { return href; }

    public String getType() { return type; }
  }

  public static class Content {

    public enum Direction {@Value("ltr")LTR, @Value("rtl")RTL, @NullValue UNKNOWN}

    @Key
    private String content;
    @Key
    private Direction direction;

    public Content(final String content, final Direction direction) {
      this.content = content;
      this.direction = direction;
    }

    public Content() {}

    public String getContent() { return content; }

    public Direction getDirection() { return direction; }
  }

  public static class Origin {

    @Key
    private String streamId;
    @Key
    private String title;
    @Key
    private String htmlUrl;

    public Origin(final String streamId, final String title, final String htmlUrl) {
      this.streamId = streamId;
      this.title = title;
      this.htmlUrl = htmlUrl;
    }

    public Origin() {}

    public String getStreamId() { return streamId; }

    public String getTitle() { return title; }

    public String getHtmlUrl() { return htmlUrl; }

    public Feed toFeed() {
      return new Subscription(streamId.substring(streamId.indexOf('/') + 1), title);
    }
  }

  /**
   * If present, “url” will contain the image URL, “width” and “height” its dimension, and
   * “contentType” its MIME type.
   */
  public static class Visual implements File {

    @Key
    protected String url;
    @Key
    private Integer width;
    @Key
    private Integer height;
    @Key
    private String contentType;

    public Visual() {}

    public Integer getWidth() { return width; }

    public Integer getHeight() { return height; }

    @Override
    public String getSource() { return url; }

    @Override
    public String getMime() { return contentType; }
  }

  public static class Enclosure implements File {

    @Key
    protected String href;
    @Key
    private Long length;
    @Key
    protected String type;

    public Enclosure() {}

    public Long getLength() { return length; }

    @Override
    public String getSource() { return href; }

    @Override
    public String getMime() { return type; }
  }

  public interface File {

    public static final String EMPTY_SOURCE = "none";

    public String getSource();

    public String getMime();
  }

  @SuppressWarnings("serial")
  public static class Entries extends ArrayList<org.github.bademux.feedly.api.model.Entry> {}
}
