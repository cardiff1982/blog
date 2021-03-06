package services;

import enums.ModerationStatus;
import enums.PostSortTypes;
import enums.VoteType;
import model.Post;
import model.PostComment;
import model.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import repositories.PostRepository;
import response.*;
import services.comparators.PostByCommentComp;
import services.comparators.PostByDateComp;
import services.comparators.PostLikeComp;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.transaction.Transactional;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class PostService {

    @PersistenceContext
    EntityManager entityManager;

    @Autowired
    PostRepository postRepository;

    private static String countPrefix = "select count(*) ";

    @Transactional
    public AuthResponseBody moderatePost(int postId, String decision) {
        ModerationStatus newStatus = ModerationStatus.NEW;
        if (decision.equals("accept"))
            newStatus = ModerationStatus.ACCEPTED;
        if (decision.equals("decline"))
            newStatus = ModerationStatus.DECLINED;
        Query query = entityManager.createQuery("update Post p set p.moderationStatus = :status where p.id = :pid")
                .setParameter("status", newStatus).setParameter("pid", postId);
        query.executeUpdate();
        return AuthResponseBody.builder().result(true).build();
    }

   public PostsResponseBody getPostsForModeration(int offset, int limit, String status, int userId) {
        String queryBody = "from Post p where p.isActive = '1' and p.moderationStatus = '" + status.toUpperCase() + "'";
        if (!status.equals("new"))
           queryBody = queryBody.concat(" and p.moderatorId = " + userId);
       Query query = entityManager.createQuery(queryBody);
       query.setFirstResult(offset);
       query.setMaxResults(limit);
       List<Post> posts = query.getResultList();
       return createResponse(posts.size(), posts);
   }

    //TODO Подумать над рефакторингом
    public PostsResponseBody getMyPosts (int offset, int limit, String status, int userId) {
        String appendix = "";
        String activeAppendix = " and p.isActive = '1' and p.moderationStatus = '";
        if (status.equals("inactive"))
            appendix = " and p.isActive = '0'";
        if (status.equals("pending"))
            appendix = activeAppendix + ModerationStatus.NEW + "'";
        if (status.equals("declined"))
            appendix = activeAppendix + ModerationStatus.DECLINED + "'";
        if (status.equals("published"))
            appendix = activeAppendix + ModerationStatus.ACCEPTED + "'";
        Query myPostsQuery = entityManager.createQuery("from Post p where p.user=" + userId + appendix);
        myPostsQuery.setFirstResult(offset);
        myPostsQuery.setMaxResults(limit);
        List<Post> myPosts = myPostsQuery.getResultList();
        return createResponse(myPosts.size(), myPosts);


    }

    public PostsResponseBody getPostResponse(int offset, int limit, String mode) {
        Query allPosts = entityManager.createQuery("from Post p where p.moderationStatus = 'ACCEPTED' and " +
                "p.isActive = 1 and time <= :nowTime", Post.class);
        allPosts.setParameter("nowTime", LocalDateTime.now());
        allPosts.setFirstResult(offset);
        allPosts.setMaxResults(limit);
        List<Post> resultPosts = getSortedPosts(allPosts.getResultList(), mode);
        return createResponse(getAllPostCount(), resultPosts);
    }

    public PostsResponseBody getSearchedPosts (int offset, int limit, String query) {
        if (query.trim().equals("")) {
            return  getPostResponse(offset, limit, PostSortTypes.recent.toString());
        }
        String mainQuery = "from Post p where p.text like :searchParam and p.moderationStatus = 'ACCEPTED' and " +
                "p.isActive = '1' and time < :nowTime";
        Query countQuery = entityManager.createQuery(countPrefix + mainQuery);
        countQuery.setParameter("searchParam","%" + query + "%").setParameter("nowTime", LocalDateTime.now());
        Long count = (Long) countQuery.getSingleResult();
        Query searchQuery = entityManager.createQuery(mainQuery, Post.class);
        searchQuery.setParameter("searchParam","%" + query + "%").setParameter("nowTime", LocalDateTime.now());
        searchQuery.setFirstResult(offset);
        searchQuery.setMaxResults(limit);
        List<Post> resultPosts = searchQuery.getResultList();
        return createResponse(count,resultPosts);
    }

    public PostsResponseBody getPostsByDate(int offset, int limit, String date) throws ParseException {

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date day = formatter.parse(date);

        String mainQuery = "from Post p where p.moderationStatus = 'ACCEPTED' and p.isActive = '1' " +
                "and date_trunc('day', p.time) = :day";
        Query countQuery = entityManager.createQuery(countPrefix + mainQuery);
        countQuery.setParameter("day",day);

        Long count = (Long) countQuery.getSingleResult();

        Query searchByDateQuery = entityManager.createQuery(
                mainQuery, Post.class);
        searchByDateQuery.setParameter("day", day);
        searchByDateQuery.setFirstResult(offset);
        searchByDateQuery.setMaxResults(limit);
        List<Post> resultPosts = searchByDateQuery.getResultList();
        return createResponse(count,resultPosts);
    }

    public PostsResponseBody getPostsByTag(int offset, int limit, String tag) {
        Query tagQuery = entityManager.createQuery("from Tag t where t.name = :tagName", Tag.class);
        tagQuery.setParameter("tagName", tag);
        Tag currentTag = (Tag) tagQuery.getResultList().get(0);
        List<Post> taggedPosts = currentTag.getTagsPosts();

        for (int i = 0; i < taggedPosts.size(); i++) {
            Post currentPost = taggedPosts.get(i);
            if (currentPost.getTime().isAfter(LocalDateTime.now()) || currentPost.getIsActive() != 1
                    || !currentPost.getModerationStatus().equals(ModerationStatus.ACCEPTED)) {
                taggedPosts.remove(currentPost);
            }
        }
        int finish = Math.min(taggedPosts.size(), offset + limit);
        return createResponse(taggedPosts.size(), taggedPosts.subList(offset, finish));
    }

    public PostResponseBody getPostById(int id) {
        Query byIdQuery = entityManager.createQuery("from Post p where p.id = :postId and p.isActive = '1' " +
                "and p.moderationStatus = 'ACCEPTED'", Post.class);
        byIdQuery.setParameter("postId", id);
        Post currentPost = (Post) byIdQuery.getResultList().get(0);
        String postText = currentPost.getText();
        UserBody userBody = UserBody.builder().id(currentPost.getUser().getId()).name(currentPost.getUser().getName())
                .build();

        List<PostComment> currentPostComments = currentPost.getPostComments();
        ArrayList<CommentBody> responseComments = new ArrayList<>();
        for (PostComment comment : currentPostComments) {
            UserBody commentUser = UserBody.builder().id(comment.getUser().getId()).name(comment.getUser().getName())
                    .photo(comment.getUser().getPhoto()).build();

            CommentBody commentBody = CommentBody.builder().id(comment.getId()).time(comment.getTime())
                    .text(comment.getText()).user(commentUser).build();

            responseComments.add(commentBody);

        }
        List<Tag> currentPostTags = currentPost.getPostTags();
        String[] tags = new String[currentPostTags.size()];

        for (int i = 0; i < currentPostTags.size() ; i++) {
            tags[i] = currentPostTags.get(i).getName();

        }
        return   PostResponseBody.builder().id(currentPost.getId()).time(currentPost.getTime().toString()).user(userBody)
                .title(currentPost.getTitle()).text(currentPost.getText()).likeCount(currentPost.getVotes(VoteType.like))
                .dislikeCount(currentPost.getVotes(VoteType.dislike)).commentCount(currentPost.getCommentsCount())
                .viewCount(currentPost.getViewCount()).comments(responseComments).tags(tags).build();
    }



    private List<Post> getSortedPosts(List<Post> posts, String mode) {

        if (mode.equals(PostSortTypes.best.toString()))
            posts.sort(new PostLikeComp());
        if (mode.equals(PostSortTypes.popular.toString()))
            posts.sort(new PostByCommentComp());
        if (mode.equals(PostSortTypes.early.toString()))
            posts.sort(new PostByDateComp().reversed());
        if (mode.equals(PostSortTypes.recent.toString()))
            posts.sort(new PostByDateComp());

        return posts;
    }

    private PostsResponseBody createResponse (long count, List<Post> posts) {

        List<PostResponseBody> postBodies = new ArrayList<>();
        for (int i = 0; i < posts.size(); i++) {
            Post currentPost = posts.get(i);
            UserBody userBody = UserBody.builder().id(currentPost.getUser().getId())
                    .name(currentPost.getUser().getName()).build();

            String postText = currentPost.getText();
            String announce = (postText.length() > 500) ? postText.substring(0,433).concat("...") : postText;
            PostResponseBody postBody = PostResponseBody.builder().id(currentPost.getId()).time(currentPost.getTime().toString())
                    .user(userBody).title(currentPost.getTitle()).announce(announce).likeCount(currentPost.getVotes(VoteType.like))
                    .dislikeCount(currentPost.getVotes(VoteType.dislike)).commentCount(currentPost.getCommentsCount())
                    .viewCount(currentPost.getViewCount()).build();
            postBodies.add(postBody);
        }
        return new PostsResponseBody(count, postBodies);
    }

    public long getAllPostCount() {
        Query query = entityManager.createQuery("select count(*) from Post p where p.isActive = '1' and " +
                "p.moderationStatus = 'ACCEPTED' and time <= :data").setParameter("data", LocalDateTime.now());
        Long count = (Long) query.getSingleResult();
        return count;
    }
}
