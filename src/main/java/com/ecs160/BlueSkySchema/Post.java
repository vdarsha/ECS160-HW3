package com.ecs160.BlueSkySchema;

import com.ecs160.persistence.*;

import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedList;
import java.util.List;

/*
 * Post class represents a BlueSky post.
 * Persistence annotations are added to support Redis persistence using the Session class
 */
@Persistable
public class Post {
    @PersistableId
    private Integer postId;

    @PersistableField
    private String dateTime;

    public Boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(Boolean blocked) {
        this.blocked = blocked;
    }

    private Boolean blocked;
    /*
     * The maximum size of a BlueSky post is 300 characters and 8 hashtags
     * https://www.sprinklr.com/help/articles/bluesky/publish-a-post-using-bluesky/671b8e772e64c74e572386fa#
     *
     * Therefore, it is suitable to represent the text content of a post in a String.
     * A Java String has a maximum size equal to the maximum size of "int" (2^32 - 1).
     */
    @PersistableField
    private String postContent;

    /* A post may have a list of replies */
    @PersistableListField(className = "com.ecs160.BlueSkySchema.Post")
    // We implement the extra credit portion, and thus we enable LazyLoad on the replies list
    @LazyLoad
    private List<Post> replies;

    /**
     * Default no-args constructor needed for reliable dynamic object instantiation in persistence code
     */
    public Post() {
        // Use epoch time in UTC as a sane default
        this(0, OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC).toString());
    }

    /**
     * Initializes a Post with unique identifier and time string.
     * @param id unique Post identifier
     * @param isoDateTimeString ISO 8601 date & time string
     */
    public Post(int id, String isoDateTimeString) {
        this.postId = id;
        this.dateTime = isoDateTimeString;
        this.replies = new LinkedList<Post>();
    }

    /**
     * Initializes a Post object with unique identifier, time string, and post comment text.
     * @param id unique Post identifier
     * @param isoDateTimeString ISO 8601 date & time string
     * @param postText text content of the post comment
     */
    public Post(int id, String isoDateTimeString, String postText) {
        this(id, isoDateTimeString);
        setPostText(postText);
    }

    /**
     * Getter method to return the unique identifier of the Post object.
     * @return unique Post identifier
     */
    public int getId() {
        return this.postId;
    }

    /**
     * Getter method to return the date & time of the Post object.
     * @return date & time of Post object
     */
    public String getDateTime() {
        return this.dateTime;
    }

    /**
     * Setter method to set the post comment text.
     * @param postText post comment text
     */
    public void setPostText(String postText) {
        this.postContent = postText;
    }

    /**
     * Getter method to set the post comment text.
     * @return post comment text
     */
    public String getPostText() {
        return this.postContent;
    }

    /**
     * Method to add reply to replies list in this post
     * @param post new Post object to add to replies
     */
    public void addReply(Post post){
        this.replies.add(post);
    }

    /**
     * Getter method to get the replies list
     * @return replies list
     */
    public List<Post> getReplies() {
        return this.replies;
    }
}
